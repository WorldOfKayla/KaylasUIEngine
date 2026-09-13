package org.takesome.kaylasEngine.gui.animation.internal.pulse;

/** Package-private accumulator for pulse workload and frame-deadline diagnostics. */
final class PulseTimingDiagnostics {
    private static final long MIN_LATE_FRAME_THRESHOLD_NANOS = 1_000_000L;

    private long maxFrameWorkNanos;
    private long smoothedFrameWorkNanos;
    private long lateFrameCount;
    private long maxFrameLatenessNanos;

    void recordFrameWork(long frameWorkNanos) {
        long safe = Math.max(0L, frameWorkNanos);
        maxFrameWorkNanos = Math.max(maxFrameWorkNanos, safe);
        smoothedFrameWorkNanos = smoothedFrameWorkNanos == 0L
                ? safe
                : ((smoothedFrameWorkNanos * 7L) + safe) / 8L;
    }

    void recordLateness(long nowNanos, long scheduledAtNanos, long intervalNanos) {
        if (scheduledAtNanos <= 0L) {
            return;
        }
        long lateness = Math.max(0L, nowNanos - scheduledAtNanos);
        long threshold = Math.max(MIN_LATE_FRAME_THRESHOLD_NANOS, Math.max(1L, intervalNanos) / 2L);
        if (lateness >= threshold) {
            lateFrameCount++;
            maxFrameLatenessNanos = Math.max(maxFrameLatenessNanos, lateness);
        }
    }

    long maxFrameWorkNanos() {
        return maxFrameWorkNanos;
    }

    long smoothedFrameWorkNanos() {
        return smoothedFrameWorkNanos;
    }

    long lateFrameCount() {
        return lateFrameCount;
    }

    long maxFrameLatenessNanos() {
        return maxFrameLatenessNanos;
    }
}
