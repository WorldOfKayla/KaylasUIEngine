package org.takesome.kaylasEngine.gui.animation;

import java.util.Comparator;
import java.util.List;

/** Data keyframe for geometry and visual-effect animation channels. */
public final class TimelineKeyFrame {
    private double time;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private int offsetX;
    private int offsetY;
    private double opacity = 1.0;
    private double glow;
    private double shine = -1.0;
    private String interpolation = "linear";

    public double time() {
        return time;
    }

    public double scaleX() {
        return scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public double opacity() {
        return opacity;
    }

    public double glow() {
        return glow;
    }

    public double shine() {
        return shine;
    }

    public String interpolation() {
        return interpolation;
    }

    public static void sort(List<TimelineKeyFrame> frames) {
        if (frames != null) {
            frames.sort(Comparator.comparingDouble(TimelineKeyFrame::time));
        }
    }
}
