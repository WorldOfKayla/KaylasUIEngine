package org.takesome.kaylasEngine.gui.animation.internal.pulse;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.animation.internal.scheduling.SwingEdt;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Objects;

/** Package-private demand-driven Swing frame clock with adaptive cadence. */
final class SwingAnimationPulseRuntime implements AnimationPulseRuntime {
    private static final int DEFAULT_FRAME_DELAY_MS = 16;
    private static final int MAX_ADAPTIVE_FRAME_DELAY_MS = 33;
    private static final long OVERLOAD_BUDGET_NANOS = 12_000_000L;
    private static final long RECOVERY_BUDGET_NANOS = 4_000_000L;
    private static final int OVERLOAD_TICKS_TO_ADAPT = 3;
    private static final int STABLE_TICKS_TO_RECOVER = 120;
    private static final SwingAnimationPulseRuntime SHARED = new SwingAnimationPulseRuntime();

    private final ArrayList<Subscription> subscriptions = new ArrayList<>();
    private final Timer timer;
    private volatile int adaptiveFrameDelayMs;
    private int overloadedTicks;
    private int stableTicks;
    private volatile long tickCount;
    private volatile long maxFrameWorkNanos;
    private volatile int activeAnimationCount;
    private boolean ticking;

    private SwingAnimationPulseRuntime() {
        timer = new Timer(DEFAULT_FRAME_DELAY_MS, event -> tick());
        timer.setCoalesce(true);
        timer.setRepeats(true);
    }

    static AnimationPulseRuntime shared() {
        return SHARED;
    }

    @Override
    public Handle schedule(int frameDelayMs, FrameTask task) {
        Subscription subscription = new Subscription(
                Math.max(1, frameDelayMs),
                Objects.requireNonNull(task, "task")
        );
        SwingEdt.run(() -> activate(subscription));
        return subscription;
    }

    @Override public int activeAnimationCount() { return activeAnimationCount; }
    @Override public int adaptiveFrameDelayMs() { return adaptiveFrameDelayMs; }
    @Override public long tickCount() { return tickCount; }
    @Override public long maxFrameWorkNanos() { return maxFrameWorkNanos; }

    private void activate(Subscription subscription) {
        if (subscription.cancelled) return;
        subscription.active = true;
        subscription.lastFrameAtNanos = 0L;
        subscription.nextFrameAtNanos = 0L;
        subscriptions.add(subscription);
        activeAnimationCount = subscriptions.size();
        if (!ticking) updateTimerCadence(true);
    }

    private void cancel(Subscription subscription) {
        if (subscription == null) return;
        subscription.cancelled = true;
        subscription.active = false;
        SwingEdt.run(() -> {
            if (!ticking) {
                compactSubscriptions();
                updateTimerCadence(false);
            }
        });
    }

    private void tick() {
        long frameStartedAt = System.nanoTime();
        int initialSize = subscriptions.size();
        ticking = true;
        try {
            for (int index = 0; index < initialSize; index++) {
                Subscription subscription = subscriptions.get(index);
                if (subscription.cancelled || !subscription.active
                        || frameStartedAt < subscription.nextFrameAtNanos) {
                    continue;
                }
                long intervalNanos = effectiveFrameDelayMs(subscription) * 1_000_000L;
                long deltaNanos = subscription.lastFrameAtNanos == 0L
                        ? intervalNanos
                        : Math.max(0L, frameStartedAt - subscription.lastFrameAtNanos);
                boolean keepRunning;
                try {
                    keepRunning = subscription.task.onFrame(frameStartedAt, deltaNanos);
                } catch (Throwable error) {
                    keepRunning = false;
                    Engine.getLOGGER().error("Animation frame callback failed and was detached.", error);
                }
                if (keepRunning && !subscription.cancelled) {
                    subscription.lastFrameAtNanos = frameStartedAt;
                    subscription.nextFrameAtNanos = frameStartedAt + intervalNanos;
                } else {
                    subscription.active = false;
                    subscription.cancelled = true;
                }
            }
        } finally {
            ticking = false;
        }
        compactSubscriptions();
        long frameWorkNanos = System.nanoTime() - frameStartedAt;
        tickCount++;
        maxFrameWorkNanos = Math.max(maxFrameWorkNanos, frameWorkNanos);
        updateAdaptiveCadence(frameWorkNanos);
        updateTimerCadence(false);
    }

    private int effectiveFrameDelayMs(Subscription subscription) {
        return Math.max(subscription.requestedFrameDelayMs, adaptiveFrameDelayMs);
    }

    private void updateAdaptiveCadence(long frameWorkNanos) {
        if (frameWorkNanos >= OVERLOAD_BUDGET_NANOS) {
            overloadedTicks++;
            stableTicks = 0;
            if (overloadedTicks >= OVERLOAD_TICKS_TO_ADAPT) {
                adaptiveFrameDelayMs = adaptiveFrameDelayMs == 0
                        ? 20
                        : Math.min(MAX_ADAPTIVE_FRAME_DELAY_MS, adaptiveFrameDelayMs + 4);
                overloadedTicks = 0;
            }
            return;
        }
        overloadedTicks = 0;
        if (frameWorkNanos <= RECOVERY_BUDGET_NANOS && adaptiveFrameDelayMs > 0) {
            stableTicks++;
            if (stableTicks >= STABLE_TICKS_TO_RECOVER) {
                adaptiveFrameDelayMs = Math.max(0, adaptiveFrameDelayMs - 4);
                if (adaptiveFrameDelayMs < DEFAULT_FRAME_DELAY_MS) adaptiveFrameDelayMs = 0;
                stableTicks = 0;
            }
        } else {
            stableTicks = 0;
        }
    }

    private void compactSubscriptions() {
        int writeIndex = 0;
        int size = subscriptions.size();
        for (int readIndex = 0; readIndex < size; readIndex++) {
            Subscription subscription = subscriptions.get(readIndex);
            if (subscription.cancelled || !subscription.active) continue;
            if (writeIndex != readIndex) subscriptions.set(writeIndex, subscription);
            writeIndex++;
        }
        if (writeIndex < size) subscriptions.subList(writeIndex, size).clear();
        activeAnimationCount = subscriptions.size();
    }

    private void updateTimerCadence(boolean wakeImmediately) {
        if (subscriptions.isEmpty()) {
            timer.stop();
            adaptiveFrameDelayMs = 0;
            overloadedTicks = 0;
            stableTicks = 0;
            return;
        }
        int delayMs = Integer.MAX_VALUE;
        for (Subscription subscription : subscriptions) {
            delayMs = Math.min(delayMs, effectiveFrameDelayMs(subscription));
        }
        delayMs = delayMs == Integer.MAX_VALUE ? DEFAULT_FRAME_DELAY_MS : Math.max(1, delayMs);
        timer.setDelay(delayMs);
        if (!timer.isRunning()) {
            timer.setInitialDelay(0);
            timer.start();
        } else if (wakeImmediately) {
            timer.setInitialDelay(0);
            timer.restart();
        }
    }

    private final class Subscription implements Handle {
        private final int requestedFrameDelayMs;
        private final FrameTask task;
        private volatile boolean cancelled;
        private volatile boolean active;
        private long lastFrameAtNanos;
        private long nextFrameAtNanos;

        private Subscription(int requestedFrameDelayMs, FrameTask task) {
            this.requestedFrameDelayMs = requestedFrameDelayMs;
            this.task = task;
        }

        @Override public boolean isActive() { return active && !cancelled; }
        @Override public void cancel() { SwingAnimationPulseRuntime.this.cancel(this); }
    }
}
