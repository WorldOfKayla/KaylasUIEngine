package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator;

/** Maps loading UI progress policy to the reusable engine animator options. */
final class LoadingUiProgressAdapter {
    private LoadingUiProgressAdapter() {
    }

    static ProgressBarAnimator.Options toEngineOptions(ScriptedLoadingUi.Progress progress) {
        return new ProgressBarAnimator.Options()
                .setProgressUpdateMs(progress.updateMs)
                .setProgressStep(progress.step)
                .setInitialDelayMs(progress.initialDelayMs)
                .setCycleDelayMs(progress.cycleDelayMs)
                .setTimelineDurationMs(progress.timelineDurationMs)
                .setTimelineFrameDelayMs(progress.timelineFrameDelayMs)
                .setMaxValue(progress.maxValue)
                .setLoop(progress.loop)
                .setRandomMessages(progress.randomMessages)
                .setShowText(progress.showText)
                .setShowPercent(progress.showPercent)
                .setResetOnStop(progress.resetOnStop)
                .setHideOnStop(progress.hideOnStop)
                .setAnimateEntrance(progress.animateEntrance)
                .setAnimateExit(progress.animateExit);
    }
}
