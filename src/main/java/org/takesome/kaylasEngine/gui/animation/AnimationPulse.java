package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.pulse.AnimationPulseRuntime;

import java.util.Objects;

/** Shared, demand-driven frame clock for Swing animations. */
public final class AnimationPulse {
    private static final AnimationPulse SHARED = new AnimationPulse(AnimationPulseRuntime.shared());
    private final AnimationPulseRuntime runtime;

    @FunctionalInterface
    public interface FrameTask {
        /** @return {@code true} to remain subscribed, or {@code false} when complete. */
        boolean onFrame(long nowNanos, long deltaNanos);
    }

    public static final class Subscription implements AutoCloseable {
        private final AnimationPulseRuntime.Handle handle;

        private Subscription(AnimationPulseRuntime.Handle handle) {
            this.handle = handle;
        }

        public boolean isActive() { return handle.isActive(); }
        public void cancel() { handle.cancel(); }
        @Override public void close() { cancel(); }
    }

    private AnimationPulse(AnimationPulseRuntime runtime) {
        this.runtime = runtime;
    }

    public static AnimationPulse shared() { return SHARED; }

    public Subscription schedule(int frameDelayMs, FrameTask task) {
        Objects.requireNonNull(task, "task");
        return new Subscription(runtime.schedule(frameDelayMs, task::onFrame));
    }

    public int activeAnimationCount() { return runtime.activeAnimationCount(); }
    public int adaptiveFrameDelayMs() { return runtime.adaptiveFrameDelayMs(); }
    public long tickCount() { return runtime.tickCount(); }
    public long maxFrameWorkNanos() { return runtime.maxFrameWorkNanos(); }
}
