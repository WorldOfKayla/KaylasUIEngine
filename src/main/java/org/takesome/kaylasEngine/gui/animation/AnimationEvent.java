package org.takesome.kaylasEngine.gui.animation;

/**
 * Immutable lifecycle sample emitted by the shared animation runtime.
 *
 * <p>Frame events are produced only while at least one {@link AnimationListener} is registered,
 * keeping the default animation path allocation-light. Generic frame tasks expose timing but may
 * not have a normalized progress value; in that case {@link #progress()} is {@link Double#NaN}.</p>
 */
public record AnimationEvent(
        long animationId,
        String name,
        Phase phase,
        long frameIndex,
        long elapsedNanos,
        long deltaNanos,
        double progress,
        String curve,
        String detail
) {
    /** Animation lifecycle phases observable from the public runtime. */
    public enum Phase {
        SCHEDULED,
        STARTED,
        FRAME,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public AnimationEvent {
        name = name == null || name.isBlank() ? "animation" : name.trim();
        curve = curve == null || curve.isBlank() ? "none" : curve.trim();
        detail = detail == null ? "" : detail;
        frameIndex = Math.max(0L, frameIndex);
        elapsedNanos = Math.max(0L, elapsedNanos);
        deltaNanos = Math.max(0L, deltaNanos);
    }

    /** Returns whether this event carries a normalized animation progress sample. */
    public boolean hasProgress() {
        return Double.isFinite(progress);
    }

    /** Elapsed animation time in milliseconds, useful for diagnostics and logging. */
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    /** Delta from the preceding rendered frame in milliseconds. */
    public double deltaMillis() {
        return deltaNanos / 1_000_000.0;
    }
}
