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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final long UI_SOUND_COOLDOWN_NANOS = 90_000_000L;
    private static final long SLOW_SOUND_WARN_NANOS = 35_000_000L;
    private static final long LOG_THROTTLE_NANOS = 1_000_000_000L;

    private static volatile int updateRateMs = DEFAULT_UPDATE_RATE_MS;

    private final Engine engine;
    private final VorbisAudioFileReader vorbisAudioFileReader;
    private final Set<Clip> activeClips = ConcurrentHashMap.newKeySet();
    private final Map<Clip, PlaybackStatusListener> clipListeners = new ConcurrentHashMap<>();
    private final Map<Clip, String> clipPaths = new ConcurrentHashMap<>();
    private final Map<Clip, Timer> clipTimers = new ConcurrentHashMap<>();
    private final Map<String, CachedAudio> audioCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPlayNanosByPath = new ConcurrentHashMap<>();
    private final AtomicReference<Runnable> stopAllSoundsCallback = new AtomicReference<>();

    private volatile long lastDroppedSoundLogNanos;
    private volatile long lastSlowSoundLogNanos;

    public SoundPlayer(Engine engine) {
        this.engine = engine;
        this.vorbisAudioFileReader = new VorbisAudioFileReader();
    }

    public void playSound(String path, boolean loop, PlaybackStatusListener listener) {
        if (!isSoundEnabled()) {
            return;
        }
        if (path == null || path.isBlank()) {
            Engine.LOGGER.warn("Ignoring empty sound path");
            return;
        }

        if (!loop && !claimUiSoundSlot(path)) {
            return;
        }

        long submittedAt = System.nanoTime();
        engine.getExecutorServiceProvider().submitTask(
                () -> playSoundInternal(path, loop, listener, submittedAt),
                "Play Sound Task"
        );
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

    private void playSoundInternal(String path, boolean loop, PlaybackStatusListener listener, long submittedAtNanos) {
        if (loop) {
            playStreamingSound(path, true, listener, submittedAtNanos);
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

            registerAndStartClip(clip, path, false, listener);
            long totalElapsed = System.nanoTime() - startedAt;
            logSlowPlayback(path, submittedAtNanos, startedAt, clipOpenElapsed, totalElapsed, false);
        } catch (IOException | LineUnavailableException ex) {
            Engine.LOGGER.error("Failed to play sound: {}", path, ex);
        }
    }

    private void playStreamingSound(String path, boolean loop, PlaybackStatusListener listener, long submittedAtNanos) {
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

                registerAndStartClip(clip, path, loop, listener);
                long totalElapsed = System.nanoTime() - startedAt;
                logSlowPlayback(path, submittedAtNanos, startedAt, clipOpenElapsed, totalElapsed, loop);
            }
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException ex) {
            Engine.LOGGER.error("Failed to play sound: {}", path, ex);
        }
    }

    private void registerAndStartClip(Clip clip, String path, boolean loop, PlaybackStatusListener listener) {
        clip.addLineListener(this);

        activeClips.add(clip);
        clipPaths.put(clip, path);
        if (listener != null) {
            clipListeners.put(clip, listener);
        }

        setVolume(clip, resolveVolume(path));
        if (loop) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
        clip.start();

        if (listener != null) {
            listener.onPlaybackStarted(path);
        }
        startPlaybackTimer(clip, path, listener);
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
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            Engine.LOGGER.debug("Clip does not support MASTER_GAIN volume control");
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float range = gainControl.getMaximum() - gainControl.getMinimum();
        float gain = (range * volume) + gainControl.getMinimum();
        gainControl.setValue(gain);
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
                listener.onPlaybackProgress(path, clip.getMicrosecondPosition(), clip.getMicrosecondLength());
            }
        }, 0, updateRateMs);
    }

    public void changeActiveVolume(float volume) {
        float safeVolume = Math.max(0.0f, Math.min(1.0f, volume));
        for (Clip clip : activeClips) {
            setVolume(clip, safeVolume);
        }
    }

    public void stopAllSounds(Runnable onStopAction) {
        stopAllSoundsCallback.set(onStopAction);
        for (Clip clip : Set.copyOf(activeClips)) {
            if (clip.isRunning()) {
                fadeOut(clip);
            } else {
                cleanupClip(clip, true);
            }
        }
        checkAndRunCallback();
    }

    public void stopAllSounds() {
        stopAllSounds(null);
    }

    private void checkAndRunCallback() {
        Runnable callback = stopAllSoundsCallback.get();
        if (activeClips.isEmpty() && callback != null && stopAllSoundsCallback.compareAndSet(callback, null)) {
            callback.run();
        }
    }

    private void fadeOut(Clip clip) {
        engine.getExecutorServiceProvider().runAsync(() -> {
            try {
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float minVolume = gainControl.getMinimum();
                    float currentVolume = gainControl.getValue();
                    while (currentVolume > minVolume && activeClips.contains(clip)) {
                        currentVolume = Math.max(minVolume, currentVolume - 0.25f);
                        gainControl.setValue(currentVolume);
                        Thread.sleep(50);
                    }
                    gainControl.setValue(minVolume);
                }
                clip.stop();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Engine.LOGGER.debug("Sound fade-out interrupted", ex);
            } finally {
                cleanupClip(clip, true);
            }
        }, "Sound FadeOut Task");
    }

    public void onAllSoundsFinished(Runnable callback) {
        if (callback == null) {
            return;
        }
        if (activeClips.isEmpty()) {
            callback.run();
        } else {
            stopAllSoundsCallback.set(callback);
        }
    }

    @Override
    public void update(LineEvent event) {
        if (event.getType() == LineEvent.Type.STOP && event.getLine() instanceof Clip clip) {
            cleanupClip(clip, true);
        }
    }

    private void cleanupClip(Clip clip, boolean notifyListener) {
        boolean wasActive = activeClips.remove(clip);
        Timer timer = clipTimers.remove(clip);
        if (timer != null) {
            timer.cancel();
        }

        PlaybackStatusListener listener = clipListeners.remove(clip);
        String path = clipPaths.remove(clip);
        if (notifyListener && listener != null && path != null) {
            listener.onPlaybackStopped(path);
        }

        if (clip.isOpen()) {
            clip.close();
        }
        if (wasActive) {
            checkAndRunCallback();
        }
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
