package org.takesome.kaylasEngine.gui.animation.internal.timeline;

import org.takesome.kaylasEngine.gui.animation.TimelineFrameState;
import org.takesome.kaylasEngine.gui.animation.TimelineKeyFrame;

import java.util.List;

/** Package-private monotonic timeline sampler and interpolator. */
final class TimelineSampler {
    private TimelineSampler() { }

    static TimelineFrameState sample(List<TimelineKeyFrame> frames, double progress, int[] segmentIndex) {
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
        while (index < frames.size() - 2 && progress > frames.get(index + 1).time()) index++;
        while (index > 0 && progress < frames.get(index).time()) index--;
        segmentIndex[0] = index;
        TimelineKeyFrame current = frames.get(index);
        TimelineKeyFrame next = frames.get(index + 1);
        double duration = next.time() - current.time();
        double segmentProgress = duration <= 0.0 ? 1.0 : (progress - current.time()) / duration;
        return interpolate(current, next, clamp01(segmentProgress), progress);
    }

    static TimelineFrameState stateFrom(TimelineKeyFrame frame, double progress) {
        return new TimelineFrameState(progress, frame.scaleX(), frame.scaleY(), frame.offsetX(), frame.offsetY());
    }

    private static TimelineFrameState interpolate(TimelineKeyFrame current,
                                                   TimelineKeyFrame next,
                                                   double ratio,
                                                   double progress) {
        double eased = applyEasing(ratio, next.interpolation());
        return new TimelineFrameState(
                progress,
                lerp(current.scaleX(), next.scaleX(), eased),
                lerp(current.scaleY(), next.scaleY(), eased),
                (int) Math.round(lerp(current.offsetX(), next.offsetX(), eased)),
                (int) Math.round(lerp(current.offsetY(), next.offsetY(), eased))
        );
    }

    private static double applyEasing(double ratio, String interpolation) {
        return switch (interpolation == null ? "linear" : interpolation) {
            case "easeIn" -> 1.0 - Math.cos(ratio * Math.PI / 2.0);
            case "easeOut" -> Math.sin(ratio * Math.PI / 2.0);
            case "easeInOut" -> 0.5 - Math.cos(ratio * Math.PI) / 2.0;
            default -> ratio;
        };
    }

    private static double lerp(double start, double end, double ratio) { return start + (end - start) * ratio; }
    static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
