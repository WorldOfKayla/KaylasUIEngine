package org.takesome.kaylasEngine.gui.animation.internal.timeline;

import org.takesome.kaylasEngine.gui.animation.AnimationPulse;
import org.takesome.kaylasEngine.gui.animation.SwingTimerGroup;
import org.takesome.kaylasEngine.gui.animation.TimelineFrameState;
import org.takesome.kaylasEngine.gui.animation.TimelineKeyFrame;
import org.takesome.kaylasEngine.gui.animation.internal.scheduling.SwingEdt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Package-private timeline runner over the shared animation pulse. */
final class DefaultTimelineExecution implements TimelineExecution {
    private final SwingTimerGroup timers;
    private final int frameDelayMs;

    DefaultTimelineExecution(SwingTimerGroup timers, int frameDelayMs) {
        this.timers = Objects.requireNonNull(timers, "timers");
        this.frameDelayMs = Math.max(1, frameDelayMs);
    }

    @Override
    public void animate(int durationMs,
                        List<TimelineKeyFrame> keyFrames,
                        Consumer<TimelineFrameState> updater,
                        Runnable onComplete) {
        Objects.requireNonNull(updater, "updater");
        if (keyFrames == null || keyFrames.isEmpty()) {
            complete(onComplete);
            return;
        }
        List<TimelineKeyFrame> frames = new ArrayList<>(keyFrames);
        frames.sort(Comparator.comparingDouble(TimelineKeyFrame::time));
        if (frames.size() == 1) {
            SwingEdt.run(() -> {
                updater.accept(TimelineSampler.stateFrom(frames.get(0), 1.0));
                complete(onComplete);
            });
            return;
        }
        SwingEdt.run(() -> start(Math.max(1, durationMs), frames, updater, onComplete));
    }

    private void start(int durationMs,
                       List<TimelineKeyFrame> frames,
                       Consumer<TimelineFrameState> updater,
                       Runnable onComplete) {
        long startedAt = System.nanoTime();
        long durationNanos = durationMs * 1_000_000L;
        int[] segmentIndex = {0};
        AnimationPulse.Subscription[] subscription = {null};
        subscription[0] = timers.track(AnimationPulse.shared().schedule(frameDelayMs, (now, delta) -> {
            double progress = TimelineSampler.clamp01((now - startedAt) / (double) durationNanos);
            updater.accept(TimelineSampler.sample(frames, progress, segmentIndex));
            if (progress >= 1.0) {
                timers.forget(subscription[0]);
                complete(onComplete);
                return false;
            }
            return true;
        }));
    }

    private static void complete(Runnable onComplete) {
        if (onComplete != null) onComplete.run();
    }
}
