package org.takesome.kaylasEngine.gui.animation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.takesome.kaylasEngine.Engine;
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

    /** Runtime policy for progress animation. Applications may build this from JSON, Lua or code. */
    public static final class Options {
        private int progressUpdateMs = DEFAULT_PROGRESS_UPDATE_MS;
        private int progressStep = 1;
        private int initialDelayMs = 0;
        private int cycleDelayMs = 0;
        private int timelineDurationMs = DEFAULT_TIMELINE_DURATION_MS;
        private int timelineFrameDelayMs = DEFAULT_ANIMATION_FRAME_MS;
        private int maxValue = -1;
        private boolean loop = true;
        private boolean randomMessages = true;
        private boolean showText = true;
        private boolean showPercent = true;
        private boolean resetOnStop = true;
        private boolean hideOnStop = false;
        private boolean animateEntrance = true;
        private boolean animateExit = true;

        public Options() {
        }

        public Options(Options source) {
            if (source == null) {
                return;
            }
            this.progressUpdateMs = source.progressUpdateMs;
            this.progressStep = source.progressStep;
            this.initialDelayMs = source.initialDelayMs;
            this.cycleDelayMs = source.cycleDelayMs;
            this.timelineDurationMs = source.timelineDurationMs;
            this.timelineFrameDelayMs = source.timelineFrameDelayMs;
            this.maxValue = source.maxValue;
            this.loop = source.loop;
            this.randomMessages = source.randomMessages;
            this.showText = source.showText;
            this.showPercent = source.showPercent;
            this.resetOnStop = source.resetOnStop;
            this.hideOnStop = source.hideOnStop;
            this.animateEntrance = source.animateEntrance;
            this.animateExit = source.animateExit;
        }

        public int progressUpdateMs() { return progressUpdateMs; }
        public int progressStep() { return progressStep; }
        public int initialDelayMs() { return initialDelayMs; }
        public int cycleDelayMs() { return cycleDelayMs; }
        public int timelineDurationMs() { return timelineDurationMs; }
        public int timelineFrameDelayMs() { return timelineFrameDelayMs; }
        public int maxValue() { return maxValue; }
        public boolean loop() { return loop; }
        public boolean randomMessages() { return randomMessages; }
        public boolean showText() { return showText; }
        public boolean showPercent() { return showPercent; }
        public boolean resetOnStop() { return resetOnStop; }
        public boolean hideOnStop() { return hideOnStop; }
        public boolean animateEntrance() { return animateEntrance; }
        public boolean animateExit() { return animateExit; }

        public Options setProgressUpdateMs(int progressUpdateMs) {
            this.progressUpdateMs = Math.max(1, progressUpdateMs);
            return this;
        }

        public Options setProgressStep(int progressStep) {
            this.progressStep = Math.max(1, progressStep);
            return this;
        }

        public Options setInitialDelayMs(int initialDelayMs) {
            this.initialDelayMs = Math.max(0, initialDelayMs);
            return this;
        }

        public Options setCycleDelayMs(int cycleDelayMs) {
            this.cycleDelayMs = Math.max(0, cycleDelayMs);
            return this;
        }

        public Options setTimelineDurationMs(int timelineDurationMs) {
            this.timelineDurationMs = Math.max(1, timelineDurationMs);
            return this;
        }

        public Options setTimelineFrameDelayMs(int timelineFrameDelayMs) {
            this.timelineFrameDelayMs = Math.max(1, timelineFrameDelayMs);
            return this;
        }

        public Options setMaxValue(int maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        public Options setLoop(boolean loop) {
            this.loop = loop;
            return this;
        }

        public Options setRandomMessages(boolean randomMessages) {
            this.randomMessages = randomMessages;
            return this;
        }

        public Options setShowText(boolean showText) {
            this.showText = showText;
            return this;
        }

        public Options setShowPercent(boolean showPercent) {
            this.showPercent = showPercent;
            return this;
        }

        public Options setResetOnStop(boolean resetOnStop) {
            this.resetOnStop = resetOnStop;
            return this;
        }

        public Options setHideOnStop(boolean hideOnStop) {
            this.hideOnStop = hideOnStop;
            return this;
        }

        public Options setAnimateEntrance(boolean animateEntrance) {
            this.animateEntrance = animateEntrance;
            return this;
        }

        public Options setAnimateExit(boolean animateExit) {
            this.animateExit = animateExit;
            return this;
        }
    }

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

    private List<String> messages = List.of();
    private ProgressListener progressListener;
    private ProgressAnimationConfig animationConfig = new ProgressAnimationConfig();
    private Timer progressTimer;
    private int progressValue;
    private int messageIndex;

    public ProgressBarAnimator(JProgressBar progressBar,
                               JLabel progressText,
                               String messagesResource,
                               String animationConfigResource,
                               String logPrefix) {
        this(progressBar, progressText, messagesResource, animationConfigResource, logPrefix, new Options());
    }

    public ProgressBarAnimator(JProgressBar progressBar,
                               JLabel progressText,
                               String messagesResource,
                               String animationConfigResource,
                               String logPrefix,
                               Options options) {
        this(progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options,
                progressBar::setValue,
                progressBar::getMaximum,
                progressText::setText,
                progressText::setVisible,
                ignored -> { });
    }

    public ProgressBarAnimator(ProgressBar progressBar,
                               JLabel progressText,
                               String messagesResource,
                               String animationConfigResource,
                               String logPrefix) {
        this(progressBar, progressText, messagesResource, animationConfigResource, logPrefix, new Options());
    }

    public ProgressBarAnimator(ProgressBar progressBar,
                               JLabel progressText,
                               String messagesResource,
                               String animationConfigResource,
                               String logPrefix,
                               Options options) {
        this(progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options,
                progressBar::setValue,
                progressBar::getMaximum,
                progressBar::setString,
                progressBar::setStringPainted,
                progressBar::setShowPercent);
    }

    private ProgressBarAnimator(JComponent progressBar,
                                JLabel progressText,
                                String messagesResource,
                                String animationConfigResource,
                                String logPrefix,
                                Options options,
                                IntConsumer progressValueSetter,
                                IntSupplier progressMaximumSupplier,
                                Consumer<String> progressTextSetter,
                                Consumer<Boolean> progressTextVisibilitySetter,
                                Consumer<Boolean> progressPercentVisibilitySetter) {
        this.progressBar = Objects.requireNonNull(progressBar, "progressBar");
        this.progressText = Objects.requireNonNull(progressText, "progressText");
        this.progressValueSetter = Objects.requireNonNull(progressValueSetter, "progressValueSetter");
        this.progressMaximumSupplier = Objects.requireNonNull(progressMaximumSupplier, "progressMaximumSupplier");
        this.progressTextSetter = Objects.requireNonNull(progressTextSetter, "progressTextSetter");
        this.progressTextVisibilitySetter = Objects.requireNonNull(progressTextVisibilitySetter, "progressTextVisibilitySetter");
        this.progressPercentVisibilitySetter = Objects.requireNonNull(progressPercentVisibilitySetter, "progressPercentVisibilitySetter");
        this.originalBounds = progressBar.getBounds();
        this.logPrefix = logPrefix == null || logPrefix.isBlank() ? "[UI-PROGRESS]" : logPrefix;
        this.options = new Options(options);
        this.timelineAnimator = new TimelineAnimator(timers, this.options.timelineFrameDelayMs());
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

    protected List<String> resolveMessages() {
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
        List<String> source = resolveMessages();
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
