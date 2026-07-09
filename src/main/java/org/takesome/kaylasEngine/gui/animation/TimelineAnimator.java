package org.takesome.kaylasEngine.gui.animation;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generic EDT timeline runner for small Swing animations.
 *
 * <p>The runner samples sorted {@link TimelineKeyFrame} values using {@code System.nanoTime()} and
 * delegates the actual component mutation to the caller. That keeps interpolation and timer lifecycle
 * in the engine, while applications keep their visual policy in JSON/Lua resources.</p>
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
        Timer timer = new Timer(frameDelayMs, null);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);
        timer.addActionListener(event -> {
            double progress = clamp01((System.nanoTime() - startedAt) / (double) durationNanos);
            updater.accept(sample(frames, progress));
            if (progress >= 1.0) {
                timers.stop(timer);
                runCompletion(onComplete);
            }
        });
        timers.start(timer);
    }

    private static TimelineFrameState sample(List<TimelineKeyFrame> frames, double progress) {
        TimelineKeyFrame first = frames.get(0);
        if (progress <= first.time()) {
            return stateFrom(first, progress);
        }

        TimelineKeyFrame last = frames.get(frames.size() - 1);
        if (progress >= last.time()) {
            return stateFrom(last, progress);
        }

        for (int i = 0; i < frames.size() - 1; i++) {
            TimelineKeyFrame current = frames.get(i);
            TimelineKeyFrame next = frames.get(i + 1);
            if (progress >= current.time() && progress <= next.time()) {
                double duration = next.time() - current.time();
                double segmentProgress = duration <= 0.0 ? 1.0 : (progress - current.time()) / duration;
                return interpolate(current, next, clamp01(segmentProgress), progress);
            }
        }
        return stateFrom(last, progress);
    }

    private static TimelineFrameState interpolate(TimelineKeyFrame current,
                                                  TimelineKeyFrame next,
                                                  double ratio,
                                                  double progress) {
        String interpolation = next.interpolation();
        double scaleX = interpolate(current.scaleX(), next.scaleX(), ratio, interpolation);
        double scaleY = interpolate(current.scaleY(), next.scaleY(), ratio, interpolation);
        int offsetX = (int) Math.round(interpolate(current.offsetX(), next.offsetX(), ratio, interpolation));
        int offsetY = (int) Math.round(interpolate(current.offsetY(), next.offsetY(), ratio, interpolation));
        return new TimelineFrameState(progress, scaleX, scaleY, offsetX, offsetY);
    }

    private static TimelineFrameState stateFrom(TimelineKeyFrame frame, double progress) {
        return new TimelineFrameState(progress, frame.scaleX(), frame.scaleY(), frame.offsetX(), frame.offsetY());
    }

    private static double interpolate(double start, double end, double ratio, String interpolation) {
        return switch (interpolation == null ? "linear" : interpolation) {
            case "easeIn" -> start + (end - start) * (1.0 - Math.cos(ratio * Math.PI / 2.0));
            case "easeOut" -> start + (end - start) * Math.sin(ratio * Math.PI / 2.0);
            case "easeInOut" -> start + (end - start) * (0.5 - Math.cos(ratio * Math.PI) / 2.0);
            default -> start + (end - start) * ratio;
        };
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
