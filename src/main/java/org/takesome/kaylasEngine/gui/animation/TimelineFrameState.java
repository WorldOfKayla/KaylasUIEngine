package org.takesome.kaylasEngine.gui.animation;

/** Resolved timeline state delivered to a Swing component updater on every animation tick. */
public record TimelineFrameState(
        double progress,
        double scaleX,
        double scaleY,
        int offsetX,
        int offsetY
) {
}
