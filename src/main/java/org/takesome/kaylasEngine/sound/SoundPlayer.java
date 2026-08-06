package org.takesome.kaylasEngine.sound;

import de.jarnbjo.vorbis.VorbisAudioFileReader;
import org.takesome.kaylasEngine.Engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small sound playback service for launcher/UI sounds.
 *
 * <p>Short UI sounds are intentionally cached as decoded PCM. Without this cache every hover/click
 * effect re-opened the bundled OGG resource and decoded Vorbis again, which produced executor bursts
 * and native audio pressure during UI animation.</p>
 */
public class SoundPlayer implements LineListener {
    private static final int DEFAULT_UPDATE_RATE_MS = 100;
    private static final int PCM_READ_BUFFER_BYTES = 16 * 1024;
    private static final int MAX_ACTIVE_EFFECT_CLIPS = 16;
    private static final int FADE_OUT_DURATION_MS = 650;
    private static final int FADE_OUT_FRAME_MS = 25;
    private static final int FORCE_CLOSE_TIMEOUT_MS = 1500;
    private static final long UI_SOUND_COOLDOWN_NANOS = 90_000_000L;
    private static final long SLOW_SOUND_WARN_NANOS = 35_000_000L;
    private static final long LOG_THROTTLE_NANOS = 1_000_000_000L;

    private static volatile int updateRateMs = DEFAULT_UPDATE_RATE_MS;

    private final Engine engine;
    private final VorbisAudioFileReader vorbisAudioFileReader;
    private final Object playbackLock = new Object();
    private final SoundPlaybackGate playbackGate = new SoundPlaybackGate();
    private final Set<Clip> activeClips = ConcurrentHashMap.newKeySet();
    private final Set<Clip> fadingClips = ConcurrentHashMap.newKeySet();
    private final Map<Clip, PlaybackStatusListener> clipListeners = new ConcurrentHashMap<>();
    private final Map<Clip, String> clipPaths = new ConcurrentHashMap<>();
    private final Map<Clip, Timer> clipTimers = new ConcurrentHashMap<>();
    private final Map<String, CachedAudio> audioCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPlayNanosByPath = new ConcurrentHashMap<>();
    private final ExecutorService audioFadeExecutor;
    private final ScheduledExecutorService audioWatchdogExecutor;

    private volatile long lastDroppedSoundLogNanos;
    private volatile long lastSlowSoundLogNanos;

    public SoundPlayer(Engine engine) {
        this.engine = engine;
        this.vorbisAudioFileReader = new VorbisAudioFileReader();
        this.audioFadeExecutor = Executors.newCachedThreadPool(daemonThreadFactory("sound-fade-"));
        this.audioWatchdogExecutor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("sound-watchdog-"));
    }

    public void playSound(String path, boolean loop, PlaybackStatusListener listener) {
        if (!isSoundEnabled()) {
            return;
        }
        if (path == null || path.isBlank()) {
            Engine.LOGGER.warn("Ignoring empty sound path");
            return;
        }

        SoundPlaybackGate.Ticket ticket = playbackGate.reserve();
        if (ticket == null) {
            Engine.LOGGER.debug("[SOUND] playback rejected while launcher audio is suspended: {}", path);
            return;
        }
        if (!loop && !claimUiSoundSlot(path)) {
            playbackGate.abandon(ticket);
            return;
        }

        long submittedAt = System.nanoTime();
        try {
            engine.getExecutorServiceProvider().submitTask(
                    () -> playSoundInternal(path, loop, listener, submittedAt, ticket),
                    "Play Sound Task"
            );
        } catch (RuntimeException error) {
            playbackGate.abandon(ticket);
            throw error;
        }
    }

    public void playSound(String path, boolean loop) {
        playSound(path, loop, null);
    }

    private boolean claimUiSoundSlot(String path) {
        long now = System.nanoTime();
        Long previous = lastPlayNanosByPath.get(path);
        if (previous != null && now - previous < UI_SOUND_COOLDOWN_NANOS) {
            logDroppedSound(path, "cooldown");
            return false;
        }

        if (activeClips.size() >= MAX_ACTIVE_EFFECT_CLIPS) {
            logDroppedSound(path, "activeClipLimit");
            return false;
        }

        lastPlayNanosByPath.put(path, now);
        return true;
    }

    private void logDroppedSound(String path, String reason) {
        long now = System.nanoTime();
        if (now - lastDroppedSoundLogNanos >= LOG_THROTTLE_NANOS) {
            lastDroppedSoundLogNanos = now;
            Engine.LOGGER.debug(
                    "[SOUND] dropped UI sound: path={}, reason={}, activeClips={}, cacheSize={}",
                    path,
                    reason,
                    activeClips.size(),
                    audioCache.size()
            );
        }
    }

    private void playSoundInternal(String path,
                                   boolean loop,
                                   PlaybackStatusListener listener,
                                   long submittedAtNanos,
                                   SoundPlaybackGate.Ticket ticket) {
        try {
            if (loop) {
                playStreamingSound(path, true, listener, submittedAtNanos, ticket);
                return;
            }

            long startedAt = System.nanoTime();
            CachedAudio cachedAudio = getCachedAudio(path);
            if (cachedAudio == null) {
                return;
            }

            try (AudioInputStream audioInputStream = cachedAudio.openStream()) {
                long clipOpenStartedAt = System.nanoTime();
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                long clipOpenElapsed = System.nanoTime() - clipOpenStartedAt;

                if (!registerAndStartClip(clip, path, false, listener, ticket)) {
                    return;
                }
                long totalElapsed = System.nanoTime() - startedAt;
                logSlowPlayback(path, submittedAtNanos, startedAt, clipOpenElapsed, totalElapsed, false);
            } catch (IOException | LineUnavailableException ex) {
                Engine.LOGGER.error("Failed to play sound: {}", path, ex);
            }
        } finally {
            playbackGate.abandon(ticket);
        }
    }

    private void playStreamingSound(String path,
                                    boolean loop,
                                    PlaybackStatusListener listener,
                                    long submittedAtNanos,
                                    SoundPlaybackGate.Ticket ticket) {
        long startedAt = System.nanoTime();
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                Engine.LOGGER.warn("Sound resource not found: {}", path);
                return;
            }

            try (AudioInputStream audioInputStream = vorbisAudioFileReader.getAudioInputStream(inputStream)) {
                long clipOpenStartedAt = System.nanoTime();
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                long clipOpenElapsed = System.nanoTime() - clipOpenStartedAt;

                if (!registerAndStartClip(clip, path, loop, listener, ticket)) {
                    return;
                }
                long totalElapsed = System.nanoTime() - startedAt;
                logSlowPlayback(path, submittedAtNanos, startedAt, clipOpenElapsed, totalElapsed, loop);
            }
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException ex) {
            Engine.LOGGER.error("Failed to play sound: {}", path, ex);
        }
    }

    private boolean registerAndStartClip(Clip clip,
                                         String path,
                                         boolean loop,
                                         PlaybackStatusListener listener,
                                         SoundPlaybackGate.Ticket ticket) {
        synchronized (playbackLock) {
            if (!playbackGate.activate(ticket)) {
                closeQuietly(clip);
                return false;
            }

            clip.addLineListener(this);
            activeClips.add(clip);
            clipPaths.put(clip, path);
            if (listener != null) {
                clipListeners.put(clip, listener);
            }

            try {
                setVolume(clip, resolveVolume(path));
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                }
                clip.start();
            } catch (RuntimeException error) {
                cleanupClip(clip, false);
                Engine.LOGGER.error("Unable to start sound clip: {}", path, error);
                return false;
            }
        }

        if (listener != null && activeClips.contains(clip)) {
            listener.onPlaybackStarted(path);
        }
        if (activeClips.contains(clip)) {
            startPlaybackTimer(clip, path, listener);
        }
        return true;
    }

    private CachedAudio getCachedAudio(String path) {
        return audioCache.computeIfAbsent(path, this::loadCachedAudio);
    }

    private CachedAudio loadCachedAudio(String path) {
        long startedAt = System.nanoTime();
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                Engine.LOGGER.warn("Sound resource not found: {}", path);
                return null;
            }

            try (AudioInputStream audioInputStream = vorbisAudioFileReader.getAudioInputStream(inputStream)) {
                AudioFormat format = audioInputStream.getFormat();
                byte[] pcm = readPcmBytes(audioInputStream);
                long frameLength = resolveFrameLength(format, pcm.length);
                CachedAudio cachedAudio = new CachedAudio(format, pcm, frameLength);
                long elapsed = System.nanoTime() - startedAt;
                Engine.LOGGER.debug(
                        "[SOUND] decoded and cached: path={}, bytes={}, frameLength={}, elapsed={} ms",
                        path,
                        pcm.length,
                        frameLength,
                        nanosToMillis(elapsed)
                );
                return cachedAudio;
            }
        } catch (IOException | UnsupportedAudioFileException ex) {
            Engine.LOGGER.error("Failed to decode sound: {}", path, ex);
            return null;
        }
    }

    private byte[] readPcmBytes(AudioInputStream audioInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[PCM_READ_BUFFER_BYTES];
        int read;
        while ((read = audioInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private long resolveFrameLength(AudioFormat format, int byteLength) {
        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            return AudioSystem.NOT_SPECIFIED;
        }
        return byteLength / frameSize;
    }

    private void logSlowPlayback(String path,
                                 long submittedAtNanos,
                                 long startedAtNanos,
                                 long clipOpenElapsedNanos,
                                 long totalElapsedNanos,
                                 boolean loop) {
        long now = System.nanoTime();
        if (totalElapsedNanos < SLOW_SOUND_WARN_NANOS && clipOpenElapsedNanos < SLOW_SOUND_WARN_NANOS) {
            return;
        }
        if (now - lastSlowSoundLogNanos < LOG_THROTTLE_NANOS) {
            return;
        }
        lastSlowSoundLogNanos = now;
        Engine.LOGGER.warn(
                "[SOUND] slow playback start: path={}, loop={}, queueWait={} ms, clipOpen={} ms, total={} ms, activeClips={}, cacheSize={}",
                path,
                loop,
                nanosToMillis(startedAtNanos - submittedAtNanos),
                nanosToMillis(clipOpenElapsedNanos),
                nanosToMillis(totalElapsedNanos),
                activeClips.size(),
                audioCache.size()
        );
    }

    private boolean isSoundEnabled() {
        if (engine.getConfig() == null || engine.getConfig().getConfig() == null) {
            return true;
        }
        Object enabled = engine.getConfig().getConfig().get("enableSound");
        return enabled == null || Boolean.parseBoolean(String.valueOf(enabled));
    }

    private float resolveVolume(String path) {
        Object configuredVolume = engine.getConfig() != null && engine.getConfig().getConfig() != null
                ? engine.getConfig().getConfig().get("volume")
                : null;
        float volume = 1.0f;
        try {
            volume = Float.parseFloat(String.valueOf(configuredVolume)) / 100.0f;
        } catch (NumberFormatException ignored) {
            if (configuredVolume != null) {
                Engine.LOGGER.warn("Invalid sound volume config: {}", configuredVolume);
            }
        }
        if (path.contains("mus")) {
            volume -= 0.15f;
        }
        return Math.max(0.0f, Math.min(1.0f, volume));
    }

    private void setVolume(Clip clip, float volume) {
        FloatControl volumeControl = resolveVolumeControl(clip);
        if (volumeControl == null) {
            Engine.LOGGER.debug("Clip does not support MASTER_GAIN or VOLUME control");
            return;
        }
        float range = volumeControl.getMaximum() - volumeControl.getMinimum();
        float gain = (range * volume) + volumeControl.getMinimum();
        volumeControl.setValue(gain);
    }

    private FloatControl resolveVolumeControl(Clip clip) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        }
        if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
            return (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
        }
        return null;
    }

    private void startPlaybackTimer(Clip clip, String path, PlaybackStatusListener listener) {
        if (listener == null) {
            return;
        }
        Timer timer = new Timer("sound-progress-" + path, true);
        Timer previous = clipTimers.put(clip, timer);
        if (previous != null) {
            previous.cancel();
        }

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (activeClips.contains(clip)) {
                    listener.onPlaybackProgress(path, clip.getMicrosecondPosition(), clip.getMicrosecondLength());
                }
            }
        }, 0, updateRateMs);
    }

    public void changeActiveVolume(float volume) {
        float safeVolume = Math.max(0.0f, Math.min(1.0f, volume));
        for (Clip clip : Set.copyOf(activeClips)) {
            try {
                setVolume(clip, safeVolume);
            } catch (RuntimeException error) {
                Engine.LOGGER.debug("Unable to change active clip volume", error);
            }
        }
    }

    /**
     * Smoothly closes current audio, rejects every queued/new sound, and completes only after active
     * native clips are closed. This is the launch barrier used before spawning Minecraft.
     */
    public CompletableFuture<Void> fadeOutAndSuspend() {
        return stopAllSoundsAsync(true);
    }

    public CompletableFuture<Void> stopAllSoundsAsync() {
        return stopAllSoundsAsync(false);
    }

    private CompletableFuture<Void> stopAllSoundsAsync(boolean suspendFurtherPlayback) {
        final CompletableFuture<Void> drained;
        final Set<Clip> snapshot;
        synchronized (playbackLock) {
            drained = playbackGate.stopAndDrain(suspendFurtherPlayback);
            snapshot = Set.copyOf(activeClips);
        }

        stopSnapshot(snapshot);
        audioWatchdogExecutor.schedule(
                () -> forceCloseRemaining(snapshot),
                FORCE_CLOSE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
        );
        return drained;
    }

    public void stopAllSounds(Runnable onStopAction) {
        CompletableFuture<Void> stopped = stopAllSoundsAsync();
        if (onStopAction != null) {
            stopped.whenComplete((ignored, error) -> runCallback(onStopAction, error));
        }
    }

    public void stopAllSounds() {
        stopAllSounds(null);
    }

    public void resumePlayback() {
        playbackGate.resume();
    }

    public boolean isPlaybackSuspended() {
        return playbackGate.isSuspended();
    }

    private void stopSnapshot(Set<Clip> clips) {
        for (Clip clip : clips) {
            if (!activeClips.contains(clip)) {
                continue;
            }
            try {
                if (clip.isRunning()) {
                    fadeOut(clip);
                } else {
                    cleanupClip(clip, true);
                }
            } catch (RuntimeException error) {
                Engine.LOGGER.debug("Unable to inspect active sound clip; forcing cleanup", error);
                cleanupClip(clip, true);
            }
        }
    }

    private void fadeOut(Clip clip) {
        if (!fadingClips.add(clip)) {
            return;
        }
        audioFadeExecutor.execute(() -> {
            try {
                FloatControl volumeControl = resolveVolumeControl(clip);
                if (volumeControl != null) {
                    float minimum = volumeControl.getMinimum();
                    float start = volumeControl.getValue();
                    int frames = Math.max(1, FADE_OUT_DURATION_MS / FADE_OUT_FRAME_MS);
                    for (int frame = 1; frame <= frames && activeClips.contains(clip); frame++) {
                        float progress = frame / (float) frames;
                        float eased = progress * progress * (3.0f - 2.0f * progress);
                        float gain = start + (minimum - start) * eased;
                        volumeControl.setValue(Math.max(minimum, gain));
                        Thread.sleep(FADE_OUT_FRAME_MS);
                    }
                    if (activeClips.contains(clip)) {
                        volumeControl.setValue(minimum);
                    }
                }
                if (clip.isOpen()) {
                    clip.stop();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                Engine.LOGGER.debug("Sound fade-out interrupted", error);
            } catch (RuntimeException error) {
                Engine.LOGGER.debug("Native sound fade-out failed; clip will be closed", error);
            } finally {
                fadingClips.remove(clip);
                cleanupClip(clip, true);
            }
        });
    }

    private void forceCloseRemaining(Set<Clip> launchSnapshot) {
        int forced = 0;
        for (Clip clip : launchSnapshot) {
            if (activeClips.contains(clip)) {
                forced++;
                cleanupClip(clip, true);
            }
        }
        if (forced > 0) {
            Engine.LOGGER.warn("[SOUND] force-closed {} clip(s) after fade-out timeout", forced);
        }
    }

    public void onAllSoundsFinished(Runnable callback) {
        if (callback == null) {
            return;
        }
        playbackGate.whenIdle().whenComplete((ignored, error) -> runCallback(callback, error));
    }

    private void runCallback(Runnable callback, Throwable error) {
        if (error != null) {
            Engine.LOGGER.warn("Sound completion callback received an error", error);
        }
        try {
            callback.run();
        } catch (RuntimeException callbackError) {
            Engine.LOGGER.error("Sound completion callback failed", callbackError);
        }
    }

    @Override
    public void update(LineEvent event) {
        if (event.getType() == LineEvent.Type.STOP && event.getLine() instanceof Clip clip) {
            cleanupClip(clip, true);
        }
    }

    private void cleanupClip(Clip clip, boolean notifyListener) {
        if (clip == null) {
            return;
        }

        PlaybackStatusListener listener;
        String path;
        synchronized (playbackLock) {
            if (!activeClips.remove(clip)) {
                return;
            }
            fadingClips.remove(clip);
            Timer timer = clipTimers.remove(clip);
            if (timer != null) {
                timer.cancel();
            }
            listener = clipListeners.remove(clip);
            path = clipPaths.remove(clip);
        }

        closeQuietly(clip);
        playbackGate.deactivate();
        if (notifyListener && listener != null && path != null) {
            try {
                listener.onPlaybackStopped(path);
            } catch (RuntimeException error) {
                Engine.LOGGER.error("Sound playback listener failed for {}", path, error);
            }
        }
    }

    private void closeQuietly(Clip clip) {
        if (clip == null) {
            return;
        }
        try {
            if (clip.isOpen()) {
                clip.close();
            }
        } catch (RuntimeException error) {
            Engine.LOGGER.debug("Unable to close native sound clip", error);
        }
    }

    private ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public static void setUPDATE_RATE(int rate) {
        SoundPlayer.updateRateMs = Math.max(10, rate);
    }

    private record CachedAudio(AudioFormat format, byte[] pcmBytes, long frameLength) {
        private AudioInputStream openStream() {
            return new AudioInputStream(new ByteArrayInputStream(pcmBytes), format, frameLength);
        }
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
