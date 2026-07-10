package org.takesome.kaylasEngine.gui.animation;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generic EDT timeline runner for small Swing animations.
 *
 * <p>Animations share the engine-wide {@link AnimationPulse}. Timeline sampling keeps a monotonic
 * segment cursor and evaluates easing once per frame instead of repeating trigonometric work for
 * every animated property.</p>
 */
public final class TimelineAnimator {
    private final SwingTimerGroup timers;
    private final int frameDelayMs;

    public TimelineAnimator(SwingTimerGroup timers, int frameDelayMs) {
        this.timers = Objects.requireNonNull(timers, "timers");
        this.frameDelayMs = Math.max(1, frameDelayMs);
    }

    public void animate(int durationMs,
                        List<TimelineKeyFrame> keyFrames,
                        Consumer<TimelineFrameState> updater,
                        Runnable onComplete) {
        Objects.requireNonNull(updater, "updater");
        if (keyFrames == null || keyFrames.isEmpty()) {
            runCompletion(onComplete);
            return;
        }

        List<TimelineKeyFrame> frames = new ArrayList<>(keyFrames);
        frames.sort(Comparator.comparingDouble(TimelineKeyFrame::time));
        if (frames.size() == 1) {
            runOnEdt(() -> {
                updater.accept(stateFrom(frames.get(0), 1.0));
                runCompletion(onComplete);
            });
            return;
        }

        runOnEdt(() -> startOnEdt(Math.max(1, durationMs), frames, updater, onComplete));
    }

    private void startOnEdt(int durationMs,
                            List<TimelineKeyFrame> frames,
                            Consumer<TimelineFrameState> updater,
                            Runnable onComplete) {
        final long startedAt = System.nanoTime();
        final long durationNanos = durationMs * 1_000_000L;
        final int[] segmentIndex = {0};
        final AnimationPulse.Subscription[] subscription = {null};

        subscription[0] = timers.track(AnimationPulse.shared().schedule(frameDelayMs, (nowNanos, deltaNanos) -> {
            double progress = clamp01((nowNanos - startedAt) / (double) durationNanos);
            updater.accept(sample(frames, progress, segmentIndex));
            if (progress >= 1.0) {
                timers.forget(subscription[0]);
                runCompletion(onComplete);
                return false;
            }
            return true;
        }));
    }

    private static TimelineFrameState sample(List<TimelineKeyFrame> frames,
                                             double progress,
                                             int[] segmentIndex) {
        TimelineKeyFrame first = frames.get(0);
        if (progress <= first.time()) {
            segmentIndex[0] = 0;
            return stateFrom(first, progress);
        }

        TimelineKeyFrame last = frames.get(frames.size() - 1);
        if (progress >= last.time()) {
            segmentIndex[0] = Math.max(0, frames.size() - 2);
            return stateFrom(last, progress);
        }

        int index = Math.max(0, Math.min(segmentIndex[0], frames.size() - 2));
        while (index < frames.size() - 2 && progress > frames.get(index + 1).time()) {
            index++;
        }
        while (index > 0 && progress < frames.get(index).time()) {
            index--;
        }
        segmentIndex[0] = index;

        TimelineKeyFrame current = frames.get(index);
        TimelineKeyFrame next = frames.get(index + 1);
        double duration = next.time() - current.time();
        double segmentProgress = duration <= 0.0 ? 1.0 : (progress - current.time()) / duration;
        return interpolate(current, next, clamp01(segmentProgress), progress);
    }

    private static TimelineFrameState interpolate(TimelineKeyFrame current,
                                                   TimelineKeyFrame next,
                                                   double ratio,
                                                   double progress) {
        double easedRatio = applyEasing(ratio, next.interpolation());
        double scaleX = lerp(current.scaleX(), next.scaleX(), easedRatio);
        double scaleY = lerp(current.scaleY(), next.scaleY(), easedRatio);
        int offsetX = (int) Math.round(lerp(current.offsetX(), next.offsetX(), easedRatio));
        int offsetY = (int) Math.round(lerp(current.offsetY(), next.offsetY(), easedRatio));
        return new TimelineFrameState(progress, scaleX, scaleY, offsetX, offsetY);
    }

    private static TimelineFrameState stateFrom(TimelineKeyFrame frame, double progress) {
        return new TimelineFrameState(progress, frame.scaleX(), frame.scaleY(), frame.offsetX(), frame.offsetY());
    }

    private static double applyEasing(double ratio, String interpolation) {
        return switch (interpolation == null ? "linear" : interpolation) {
            case "easeIn" -> 1.0 - Math.cos(ratio * Math.PI / 2.0);
            case "easeOut" -> Math.sin(ratio * Math.PI / 2.0);
            case "easeInOut" -> 0.5 - Math.cos(ratio * Math.PI) / 2.0;
            default -> ratio;
        };
    }

    private static double lerp(double start, double end, double ratio) {
        return start + (end - start) * ratio;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void runCompletion(Runnable onComplete) {
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
