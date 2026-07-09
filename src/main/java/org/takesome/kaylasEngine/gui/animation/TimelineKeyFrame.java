package org.takesome.kaylasEngine.gui.animation;

import java.util.Comparator;
import java.util.List;

/** Data keyframe for simple Swing component geometry timelines. */
public final class TimelineKeyFrame {
    private double time;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private int offsetX;
    private int offsetY;
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

    public String interpolation() {
        return interpolation;
    }

    public static void sort(List<TimelineKeyFrame> frames) {
        if (frames != null) {
            frames.sort(Comparator.comparingDouble(TimelineKeyFrame::time));
        }
    }
}
