package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.timeline.TimelineExecution;

import java.util.List;
import java.util.function.Consumer;

/** Generic EDT timeline runner for small Swing animations. */
public final class TimelineAnimator {
    private final TimelineExecution execution;

    public TimelineAnimator(SwingTimerGroup timers, int frameDelayMs) {
        this.execution = TimelineExecution.create(timers, frameDelayMs);
    }

    public void animate(int durationMs,
                        List<TimelineKeyFrame> keyFrames,
                        Consumer<TimelineFrameState> updater,
                        Runnable onComplete) {
        execution.animate(durationMs, keyFrames, updater, onComplete);
    }
}
