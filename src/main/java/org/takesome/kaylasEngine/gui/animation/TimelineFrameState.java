package org.takesome.kaylasEngine.gui.animation;

/** Resolved geometry and visual-effect state delivered on every animation tick. */
public record TimelineFrameState(
        double progress,
        double scaleX,
        double scaleY,
        int offsetX,
        int offsetY,
        double opacity,
        double glow,
        double shine
) {
    public TimelineFrameState(double progress,
                              double scaleX,
                              double scaleY,
                              int offsetX,
                              int offsetY) {
        this(progress, scaleX, scaleY, offsetX, offsetY, 1.0, 0.0, -1.0);
    }
}
