package org.takesome.kaylasEngine.gui.animation.internal.pulse;

/** Internal shared frame-clock boundary. */
public interface AnimationPulseRuntime {
    @FunctionalInterface
    interface FrameTask {
        boolean onFrame(long nowNanos, long deltaNanos);
    }

    interface Handle extends AutoCloseable {
        boolean isActive();
        void cancel();
        @Override default void close() { cancel(); }
    }

    static AnimationPulseRuntime shared() {
        return SwingAnimationPulseRuntime.shared();
    }

    Handle schedule(int frameDelayMs, FrameTask task);
    int activeAnimationCount();
    int adaptiveFrameDelayMs();
    long tickCount();
    long maxFrameWorkNanos();
}
