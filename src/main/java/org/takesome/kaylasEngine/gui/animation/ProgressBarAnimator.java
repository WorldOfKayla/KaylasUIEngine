package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.progress.ProgressAnimationController;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Public facade for the engine-owned Swing progress animation runtime.
 *
 * <p>Resource parsing, timer state and timeline execution are isolated in the internal progress
 * package. This class preserves the stable construction and extension API.</p>
 */
public class ProgressBarAnimator {
    private static final int DEFAULT_ANIMATION_FRAME_MS = 16;
    private static final int DEFAULT_PROGRESS_UPDATE_MS = 100;
    private static final int DEFAULT_TIMELINE_DURATION_MS = 500;

    public interface ProgressListener {
        void onStart();
        void onProgress(int value);
        void onComplete();
    }

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

    private final ProgressAnimationController controller;

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
        this(
                progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options,
                progressBar::setValue,
                progressBar::getMaximum,
                progressText::setText,
                progressText::setVisible,
                ignored -> { }
        );
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
        this(
                progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options,
                progressBar::setValue,
                progressBar::getMaximum,
                progressBar::setString,
                progressBar::setStringPainted,
                progressBar::setShowPercent
        );
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
        controller = ProgressAnimationController.create(new ProgressAnimationController.Config(
                progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options,
                progressValueSetter,
                progressMaximumSupplier,
                progressTextSetter,
                progressTextVisibilitySetter,
                progressPercentVisibilitySetter,
                this::resolveMessages
        ));
    }

    public void setProgressListener(ProgressListener listener) {
        controller.setProgressListener(listener);
    }

    public void startProgressTest() {
        controller.startProgressTest();
    }

    public void stop() {
        controller.stop();
    }

    protected List<String> resolveMessages() {
        return controller.loadedMessages();
    }
}
