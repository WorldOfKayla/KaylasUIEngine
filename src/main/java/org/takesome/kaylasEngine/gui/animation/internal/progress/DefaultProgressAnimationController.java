package org.takesome.kaylasEngine.gui.animation.internal.progress;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.Options;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.ProgressListener;
import org.takesome.kaylasEngine.gui.animation.SwingTimerGroup;
import org.takesome.kaylasEngine.gui.animation.TimelineAnimator;
import org.takesome.kaylasEngine.gui.animation.TimelineKeyFrame;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.resources.ResourceLoader;

import javax.swing.JComponent;
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
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Engine-owned Swing progress animation controller.
 *
 * <p>Applications provide resource paths, localized messages and runtime options. The engine owns timer
 * lifecycle, interpolation, progress loop semantics and EDT safety.</p>
 */
final class DefaultProgressAnimationController implements ProgressAnimationController {
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_ANIMATION_FRAME_MS = 16;
    private static final int DEFAULT_PROGRESS_UPDATE_MS = 100;
    private static final int DEFAULT_TIMELINE_DURATION_MS = 500;

    /** Runtime policy for progress animation. Applications may build this from JSON, Lua or code. */
    private final JComponent progressBar;
    private final IntConsumer progressValueSetter;
    private final IntSupplier progressMaximumSupplier;
    private final Consumer<String> progressTextSetter;
    private final Consumer<Boolean> progressTextVisibilitySetter;
    private final Consumer<Boolean> progressPercentVisibilitySetter;
    private final JLabel progressText;
    private final Rectangle originalBounds;
    private final String logPrefix;
    private final SwingTimerGroup timers = new SwingTimerGroup();
    private final TimelineAnimator timelineAnimator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Options options;
    private final java.util.function.Supplier<List<String>> messageResolver;

    private List<String> messages = List.of();
    private ProgressListener progressListener;
    private ProgressAnimationConfig animationConfig = new ProgressAnimationConfig();
    private Timer progressTimer;
    private int progressValue;
    private int messageIndex;

    DefaultProgressAnimationController(ProgressAnimationController.Config config) {
        Objects.requireNonNull(config, "config");
        this.progressBar = Objects.requireNonNull(config.progressBar(), "progressBar");
        this.progressText = Objects.requireNonNull(config.progressText(), "progressText");
        this.progressValueSetter = Objects.requireNonNull(config.progressValueSetter(), "progressValueSetter");
        this.progressMaximumSupplier = Objects.requireNonNull(config.progressMaximumSupplier(), "progressMaximumSupplier");
        this.progressTextSetter = Objects.requireNonNull(config.progressTextSetter(), "progressTextSetter");
        this.progressTextVisibilitySetter = Objects.requireNonNull(
                config.progressTextVisibilitySetter(),
                "progressTextVisibilitySetter"
        );
        this.progressPercentVisibilitySetter = Objects.requireNonNull(
                config.progressPercentVisibilitySetter(),
                "progressPercentVisibilitySetter"
        );
        this.messageResolver = Objects.requireNonNull(config.messageResolver(), "messageResolver");
        this.originalBounds = progressBar.getBounds();
        this.logPrefix = config.logPrefix() == null || config.logPrefix().isBlank()
                ? "[UI-PROGRESS]"
                : config.logPrefix();
        this.options = new Options(config.options());
        this.timelineAnimator = new TimelineAnimator(timers, this.options.timelineFrameDelayMs());
        loadMessagesFromJson(config.messagesResource());
        loadAnimationConfig(config.animationConfigResource());
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
            Engine.getLOGGER().debug(
                    "{} progress animator start: source=SwingTimer updateMs={} step={} loop={} timelineDurationMs={} timelineFrameDelayMs={} randomMessages={} showText={} showPercent={}",
                    logPrefix,
                    options.progressUpdateMs(),
                    options.progressStep(),
                    options.loop(),
                    options.timelineDurationMs(),
                    options.timelineFrameDelayMs(),
                    options.randomMessages(),
                    options.showText(),
                    options.showPercent());
            progressValue = 0;
            messageIndex = 0;
            progressTextVisibilitySetter.accept(options.showText());
            progressPercentVisibilitySetter.accept(options.showPercent());
            if (!options.showText()) {
                progressTextSetter.accept("");
            }
            if (progressListener != null) {
                progressListener.onStart();
            }
            animateProgressBarEntrance(() -> {
                updateProgressMessage();
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
            if (options.resetOnStop()) {
                setProgressValue(0);
                progressValue = 0;
            }
            if (options.hideOnStop()) {
                progressBar.setVisible(false);
            }
            Engine.getLOGGER().debug("{} progress animator stopped", logPrefix);
        });
    }

    @Override
    public List<String> loadedMessages() {
        return messages;
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

    private void updateProgressMessage() {
        if (!options.showText()) {
            return;
        }
        List<String> source = messageResolver.get();
        if (source == null || source.isEmpty()) {
            return;
        }
        List<String> available = source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(message -> !message.isEmpty())
                .toList();
        if (available.isEmpty()) {
            return;
        }
        String message;
        if (options.randomMessages()) {
            message = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        } else {
            message = available.get(messageIndex % available.size());
            messageIndex++;
        }
        progressTextSetter.accept(message);
    }

    private void startProgressTimer() {
        if (!running.get()) {
            return;
        }

        stopProgressTimer();
        final int maxValue = resolveMaxValue();
        progressTimer = new Timer(options.progressUpdateMs(), event -> {
            if (!running.get()) {
                stopProgressTimer();
                return;
            }

            int visibleValue = Math.min(progressValue, maxValue);
            setProgressValue(visibleValue);
            if (progressListener != null) {
                progressListener.onProgress(visibleValue);
            }

            progressValue += options.progressStep();
            if (progressValue > maxValue) {
                completeProgressCycle(maxValue);
            }
        });
        progressTimer.setInitialDelay(options.initialDelayMs());
        progressTimer.setCoalesce(true);
        timers.start(progressTimer);
    }

    private void setProgressValue(int value) {
        progressValueSetter.accept(value);
    }

    private int resolveMaxValue() {
        if (options.maxValue() > 0) {
            return options.maxValue();
        }
        return Math.max(1, progressMaximumSupplier.getAsInt());
    }

    private void completeProgressCycle(int maxValue) {
        stopProgressTimer();
        setProgressValue(maxValue);
        if (progressListener != null) {
            progressListener.onProgress(maxValue);
        }
        animateProgressBarExit(() -> {
            if (options.resetOnStop()) {
                setProgressValue(0);
            }
            progressValue = 0;
            if (progressListener != null) {
                progressListener.onComplete();
            }
            if (running.get() && options.loop()) {
                runAfterCycleDelay(() -> animateProgressBarEntrance(() -> {
                    updateProgressMessage();
                    startProgressTimer();
                }));
            } else {
                running.set(false);
            }
        });
    }

    private void runAfterCycleDelay(Runnable action) {
        if (options.cycleDelayMs() <= 0) {
            action.run();
            return;
        }
        Timer delayTimer = new Timer(options.cycleDelayMs(), null);
        delayTimer.setRepeats(false);
        delayTimer.addActionListener(event -> {
            timers.stop(delayTimer);
            action.run();
        });
        timers.start(delayTimer);
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            timers.stop(progressTimer);
            progressTimer = null;
        }
    }

    private void animateProgressBarEntrance(Runnable onComplete) {
        List<TimelineKeyFrame> entrance = animationConfig.entrance;
        if (!options.animateEntrance() || entrance == null || entrance.isEmpty()) {
            progressBar.setVisible(true);
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
        if (!options.animateExit() || exit == null || exit.isEmpty()) {
            if (options.hideOnStop()) {
                progressBar.setVisible(false);
            }
            onComplete.run();
            return;
        }
        SwingUtilities.invokeLater(() -> animateWithTimeline(exit, () -> {
            progressBar.setVisible(false);
            onComplete.run();
        }));
    }

    private void animateWithTimeline(List<TimelineKeyFrame> keyFrames, Runnable onComplete) {
        timelineAnimator.animate(options.timelineDurationMs(), keyFrames, state -> {
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
