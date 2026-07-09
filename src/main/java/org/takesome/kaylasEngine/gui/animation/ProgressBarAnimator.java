package org.takesome.kaylasEngine.gui.animation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.resources.ResourceLoader;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Engine-owned Swing progress animation controller.
 *
 * <p>Applications provide resource paths for messages and keyframes. The engine owns timer lifecycle,
 * interpolation, progress loop semantics and EDT safety.</p>
 */
public class ProgressBarAnimator {
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_ANIMATION_FRAME_MS = 16;
    private static final int DEFAULT_PROGRESS_UPDATE_MS = 100;
    private static final int DEFAULT_TIMELINE_DURATION_MS = 500;

    public interface ProgressListener {
        void onStart();
        void onProgress(int value);
        void onComplete();
    }

    private final JProgressBar progressBar;
    private final JLabel progressText;
    private final Rectangle originalBounds;
    private final String logPrefix;
    private final SwingTimerGroup timers = new SwingTimerGroup();
    private final TimelineAnimator timelineAnimator = new TimelineAnimator(timers, DEFAULT_ANIMATION_FRAME_MS);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private List<String> messages = List.of();
    private ProgressListener progressListener;
    private ProgressAnimationConfig animationConfig = new ProgressAnimationConfig();
    private Timer progressTimer;
    private int progressValue;

    public ProgressBarAnimator(JProgressBar progressBar,
                               JLabel progressText,
                               String messagesResource,
                               String animationConfigResource,
                               String logPrefix) {
        this.progressBar = Objects.requireNonNull(progressBar, "progressBar");
        this.progressText = Objects.requireNonNull(progressText, "progressText");
        this.originalBounds = progressBar.getBounds();
        this.logPrefix = logPrefix == null || logPrefix.isBlank() ? "[UI-PROGRESS]" : logPrefix;
        loadMessagesFromJson(messagesResource);
        loadAnimationConfig(animationConfigResource);
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public void startProgressTest() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (!running.get()) {
                return;
            }
            Engine.getLOGGER().debug("{} progress animator start: source=SwingTimer, updateMs={}", logPrefix, DEFAULT_PROGRESS_UPDATE_MS);
            progressValue = 0;
            if (progressListener != null) {
                progressListener.onStart();
            }
            animateProgressBarEntrance(() -> {
                updateRandomMessage();
                startProgressTimer();
            });
        });
    }

    public void stop() {
        running.set(false);
        SwingUtilities.invokeLater(() -> {
            stopProgressTimer();
            timers.stopAll();
            progressBar.setBounds(originalBounds);
            progressBar.setValue(0);
            Engine.getLOGGER().debug("{} progress animator stopped", logPrefix);
        });
    }

    private void loadMessagesFromJson(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            messages = List.of();
            return;
        }
        try (InputStream inputStream = ResourceLoader.open(filePath, ProgressBarAnimator.class.getClassLoader());
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);
            List<String> loaded = new ArrayList<>();
            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.size(); i++) {
                    loaded.add(jsonArray.get(i).getAsString());
                }
            }
            messages = List.copyOf(loaded);
        } catch (IOException | JsonSyntaxException | JsonIOException error) {
            Engine.getLOGGER().warn("{} unable to load progress messages: {}", logPrefix, error.getMessage());
            messages = List.of();
        }
    }

    private void loadAnimationConfig(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            animationConfig = new ProgressAnimationConfig();
            return;
        }
        try {
            ProgressAnimationConfig loaded = ResourceLoader.loadJson(
                    filePath,
                    ProgressAnimationConfig.class,
                    GSON,
                    ProgressBarAnimator.class.getClassLoader()
            );
            animationConfig = loaded == null ? new ProgressAnimationConfig() : loaded;
            TimelineKeyFrame.sort(animationConfig.entrance);
            TimelineKeyFrame.sort(animationConfig.exit);
        } catch (Exception error) {
            Engine.getLOGGER().warn("{} unable to load progress animation config: {}", logPrefix, error.getMessage());
            animationConfig = new ProgressAnimationConfig();
        }
    }

    private void updateRandomMessage() {
        if (!messages.isEmpty()) {
            progressText.setText(messages.get(ThreadLocalRandom.current().nextInt(messages.size())));
        }
    }

    private void startProgressTimer() {
        if (!running.get()) {
            return;
        }

        stopProgressTimer();
        final int maxValue = Math.max(1, progressBar.getMaximum());
        progressTimer = new Timer(DEFAULT_PROGRESS_UPDATE_MS, event -> {
            if (!running.get()) {
                stopProgressTimer();
                return;
            }

            progressBar.setValue(progressValue);
            if (progressListener != null) {
                progressListener.onProgress(progressValue);
            }

            progressValue++;
            if (progressValue > maxValue) {
                stopProgressTimer();
                animateProgressBarExit(() -> {
                    progressBar.setBounds(originalBounds);
                    progressBar.setValue(0);
                    progressValue = 0;
                    if (progressListener != null) {
                        progressListener.onComplete();
                    }
                    if (running.get()) {
                        animateProgressBarEntrance(() -> {
                            updateRandomMessage();
                            startProgressTimer();
                        });
                    }
                });
            }
        });
        progressTimer.setInitialDelay(0);
        progressTimer.setCoalesce(true);
        timers.start(progressTimer);
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            timers.stop(progressTimer);
            progressTimer = null;
        }
    }

    private void animateProgressBarEntrance(Runnable onComplete) {
        List<TimelineKeyFrame> entrance = animationConfig.entrance;
        if (entrance == null || entrance.isEmpty()) {
            onComplete.run();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(true);
            animateWithTimeline(entrance, onComplete);
        });
    }

    private void animateProgressBarExit(Runnable onComplete) {
        List<TimelineKeyFrame> exit = animationConfig.exit;
        if (exit == null || exit.isEmpty()) {
            onComplete.run();
            return;
        }
        SwingUtilities.invokeLater(() -> animateWithTimeline(exit, () -> {
            progressBar.setVisible(false);
            onComplete.run();
        }));
    }

    private void animateWithTimeline(List<TimelineKeyFrame> keyFrames, Runnable onComplete) {
        timelineAnimator.animate(DEFAULT_TIMELINE_DURATION_MS, keyFrames, state -> {
            int newWidth = Math.max(0, (int) Math.round(originalBounds.width * state.scaleX()));
            int newHeight = Math.max(0, (int) Math.round(originalBounds.height * state.scaleY()));
            int newX = originalBounds.x - (newWidth - originalBounds.width) / 2 + state.offsetX();
            int newY = originalBounds.y + state.offsetY();
            progressBar.setBounds(newX, newY, newWidth, newHeight);
        }, onComplete);
    }

    private static final class ProgressAnimationConfig {
        private List<TimelineKeyFrame> entrance;
        private List<TimelineKeyFrame> exit;
    }
}
