package org.takesome.kaylasEngine.gui.animation;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.gui.loadingManager.ScriptedLoadingUi;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JProgressBar;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unified demand-driven runtime for all KaylasUI animations.
 *
 * <p>The engine owns the shared frame clock, delayed and interval scheduling, easing evaluation,
 * tween execution and lifecycle handles. Higher-level window, overlay, drawer and progress
 * animators build on this API instead of creating independent Swing timers.</p>
 */
public final class AnimationEngine {
    private static final AnimationEngine SHARED = new AnimationEngine(AnimationPulse.shared());
    private static final int DEFAULT_FRAME_DELAY_MS = 16;

    private final AnimationPulse pulse;

    @FunctionalInterface
    public interface FrameTask {
        /** @return {@code true} to remain active, or {@code false} when complete. */
        boolean onFrame(long nowNanos, long deltaNanos);
    }

    @FunctionalInterface
    public interface IntervalTask {
        /** @return {@code true} to continue recurring, or {@code false} to stop. */
        boolean run();
    }

    /** Cancelable lifecycle handle returned by every engine schedule operation. */
    public interface Handle extends AutoCloseable {
        boolean isActive();
        void cancel();
        @Override default void close() { cancel(); }
    }

    /** Immutable runtime diagnostics from the unified clock. */
    public record Metrics(
            int activeAnimations,
            int adaptiveFrameDelayMs,
            long tickCount,
            long maxFrameWorkNanos
    ) { }

    private AnimationEngine(AnimationPulse pulse) {
        this.pulse = Objects.requireNonNull(pulse, "pulse");
    }

    public static AnimationEngine shared() {
        return SHARED;
    }

    public Handle schedule(int frameDelayMs, FrameTask task) {
        Objects.requireNonNull(task, "task");
        AtomicBoolean active = new AtomicBoolean(true);
        AnimationPulse.Subscription subscription = pulse.schedule(
                Math.max(1, frameDelayMs),
                (now, delta) -> {
                    if (!active.get()) {
                        return false;
                    }
                    boolean keepRunning = task.onFrame(now, delta);
                    if (!keepRunning) {
                        active.set(false);
                    }
                    return keepRunning;
                }
        );
        return new Handle() {
            @Override
            public boolean isActive() {
                return active.get() && subscription.isActive();
            }

            @Override
            public void cancel() {
                if (active.getAndSet(false)) {
                    subscription.cancel();
                }
            }
        };
    }

    public Handle delay(int delayMs, Runnable action) {
        Objects.requireNonNull(action, "action");
        int safeDelayMs = Math.max(0, delayMs);
        if (safeDelayMs == 0) {
            action.run();
            return completedHandle();
        }
        long startedAt = System.nanoTime();
        long delayNanos = safeDelayMs * 1_000_000L;
        return schedule(Math.min(DEFAULT_FRAME_DELAY_MS, safeDelayMs), (now, delta) -> {
            if (now - startedAt < delayNanos) {
                return true;
            }
            action.run();
            return false;
        });
    }

    public Handle interval(int intervalMs, int initialDelayMs, IntervalTask task) {
        Objects.requireNonNull(task, "task");
        int safeIntervalMs = Math.max(1, intervalMs);
        int safeInitialDelayMs = Math.max(0, initialDelayMs);
        long startedAt = System.nanoTime();
        long firstAt = startedAt + safeInitialDelayMs * 1_000_000L;
        long intervalNanos = safeIntervalMs * 1_000_000L;
        long[] nextAt = {firstAt};
        return schedule(Math.min(DEFAULT_FRAME_DELAY_MS, safeIntervalMs), (now, delta) -> {
            if (now < nextAt[0]) {
                return true;
            }
            boolean keepRunning = task.run();
            if (!keepRunning) {
                return false;
            }
            long intervalsBehind = Math.max(1L, (now - nextAt[0]) / intervalNanos + 1L);
            nextAt[0] += intervalsBehind * intervalNanos;
            return true;
        });
    }

    public Handle tween(int durationMs,
                        int frameDelayMs,
                        AnimationCurve curve,
                        Consumer<Float> updater,
                        Runnable onComplete) {
        Objects.requireNonNull(updater, "updater");
        AnimationCurve resolvedCurve = curve == null ? AnimationCurve.named("linear") : curve;
        int safeDurationMs = Math.max(0, durationMs);
        if (safeDurationMs == 0) {
            updater.accept(resolvedCurve.apply(1.0f));
            if (onComplete != null) {
                onComplete.run();
            }
            return completedHandle();
        }
        long startedAt = System.nanoTime();
        long durationNanos = safeDurationMs * 1_000_000L;
        return schedule(Math.max(1, frameDelayMs), (now, delta) -> {
            float progress = Math.min(1.0f, (now - startedAt) / (float) durationNanos);
            updater.accept(resolvedCurve.apply(progress));
            if (progress < 1.0f) {
                return true;
            }
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        });
    }

    public ScriptedWindowAnimator scriptedWindow(FloatingWindow window,
                                                   ScriptedLoadingUi.Transition transition) {
        return new ScriptedWindowAnimator(window, transition);
    }

    public LayeredPaneOverlay layeredOverlay(JLayeredPane layeredPane,
                                              Supplier<Rectangle> boundsSupplier,
                                              Color color,
                                              String name,
                                              int frameDelayMs,
                                              Logger logger,
                                              String logPrefix) {
        return new LayeredPaneOverlay(
                layeredPane,
                boundsSupplier,
                color,
                name,
                frameDelayMs,
                logger,
                logPrefix
        );
    }

    public TimelineAnimator timeline(SwingTimerGroup resources, int frameDelayMs) {
        return new TimelineAnimator(resources, frameDelayMs);
    }

    public SnapshotDrawerAnimator.Builder drawer() {
        return SnapshotDrawerAnimator.builder();
    }

    public ProgressBarAnimator progressBar(ProgressBar progressBar,
                                            JLabel progressText,
                                            String messagesResource,
                                            String animationConfigResource,
                                            String logPrefix,
                                            ProgressBarAnimator.Options options,
                                            Supplier<List<String>> messageSupplier) {
        return new ProgressBarAnimator(
                progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options
        ) {
            @Override
            protected List<String> resolveMessages() {
                List<String> supplied = messageSupplier == null ? List.of() : messageSupplier.get();
                return supplied == null || supplied.isEmpty() ? super.resolveMessages() : supplied;
            }
        };
    }

    public ProgressBarAnimator progressBar(JProgressBar progressBar,
                                            JLabel progressText,
                                            String messagesResource,
                                            String animationConfigResource,
                                            String logPrefix,
                                            ProgressBarAnimator.Options options,
                                            Supplier<List<String>> messageSupplier) {
        return new ProgressBarAnimator(
                progressBar,
                progressText,
                messagesResource,
                animationConfigResource,
                logPrefix,
                options
        ) {
            @Override
            protected List<String> resolveMessages() {
                List<String> supplied = messageSupplier == null ? List.of() : messageSupplier.get();
                return supplied == null || supplied.isEmpty() ? super.resolveMessages() : supplied;
            }
        };
    }

    public AnimationCurve curve(String name) {
        return AnimationCurve.named(name);
    }

    public int adaptiveFrameDelayMs() {
        return pulse.adaptiveFrameDelayMs();
    }

    public Metrics metrics() {
        return new Metrics(
                pulse.activeAnimationCount(),
                pulse.adaptiveFrameDelayMs(),
                pulse.tickCount(),
                pulse.maxFrameWorkNanos()
        );
    }

    public Handle completed() {
        return completedHandle();
    }

    private static Handle completedHandle() {
        return new Handle() {
            @Override public boolean isActive() { return false; }
            @Override public void cancel() { }
        };
    }
}
