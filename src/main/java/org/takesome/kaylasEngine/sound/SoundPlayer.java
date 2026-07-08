package org.takesome.kaylasEngine.sound;

import de.jarnbjo.vorbis.VorbisAudioFileReader;
import org.takesome.kaylasEngine.Engine;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SoundPlayer implements LineListener {
    private static final int DEFAULT_UPDATE_RATE_MS = 100;
    private static volatile int updateRateMs = DEFAULT_UPDATE_RATE_MS;

    private final Engine engine;
    private final VorbisAudioFileReader vorbisAudioFileReader;
    private final Set<Clip> activeClips = ConcurrentHashMap.newKeySet();
    private final Map<Clip, PlaybackStatusListener> clipListeners = new ConcurrentHashMap<>();
    private final Map<Clip, String> clipPaths = new ConcurrentHashMap<>();
    private final Map<Clip, Timer> clipTimers = new ConcurrentHashMap<>();
    private final AtomicReference<Runnable> stopAllSoundsCallback = new AtomicReference<>();

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

        engine.getExecutorServiceProvider().submitTask(() -> playSoundInternal(path, loop, listener), "Play Sound Task");
    }

    public void playSound(String path, boolean loop) {
        playSound(path, loop, null);
    }

    private void playSoundInternal(String path, boolean loop, PlaybackStatusListener listener) {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                Engine.LOGGER.warn("Sound resource not found: {}", path);
                return;
            }

            try (AudioInputStream audioInputStream = vorbisAudioFileReader.getAudioInputStream(inputStream)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
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
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException ex) {
            Engine.LOGGER.error("Failed to play sound: {}", path, ex);
        }
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
}
