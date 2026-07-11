package org.takesome.kaylasEngine.gui.animation.internal.timeline;

import org.takesome.kaylasEngine.gui.animation.SwingTimerGroup;
import org.takesome.kaylasEngine.gui.animation.TimelineFrameState;
import org.takesome.kaylasEngine.gui.animation.TimelineKeyFrame;

import java.util.List;
import java.util.function.Consumer;

/** Internal execution boundary for key-frame timelines. */
public interface TimelineExecution {
    static TimelineExecution create(SwingTimerGroup timers, int frameDelayMs) {
        return new DefaultTimelineExecution(timers, frameDelayMs);
    }

    void animate(int durationMs,
                 List<TimelineKeyFrame> keyFrames,
                 Consumer<TimelineFrameState> updater,
                 Runnable onComplete);
}
