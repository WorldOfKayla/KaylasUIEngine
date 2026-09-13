package org.takesome.kaylasEngine.gui.animation;

import org.apache.logging.log4j.LogManager;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Unified demand-driven runtime for all KaylasUI animations.
 *
 * <p>The engine owns the shared frame clock, delayed and interval scheduling, easing evaluation,
 * tween execution and lifecycle handles. Higher-level window, overlay, drawer and progress
 * animators build on this API instead of creating independent Swing timers.</p>
 *
 * <p>Lifecycle telemetry is opt-in through {@link #listen(AnimationListener)}. When no listener is
 * registered, frame events are not allocated, keeping the normal rendering path lightweight.</p>
 */
public final class AnimationEngine {
    private static final Logger LOGGER = LogManager.getLogger(AnimationEngine.class);
    private static final AnimationEngine SHARED = new AnimationEngine(AnimationPulse.shared());
    private static final int DEFAULT_FRAME_DELAY_MS = 16;

    private final AnimationPulse pulse;
    private final CopyOnWriteArrayList<AnimationListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong animationSequence = new AtomicLong();

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

    /**
     * Registers a lightweight animation listener.
     *
     * @return a registration handle whose {@link AutoCloseable#close()} method removes the listener
     */
    public AutoCloseable listen(AnimationListener listener) {
        AnimationListener resolved = Objects.requireNonNull(listener, "listener");
        listeners.add(resolved);
        AtomicBoolean registered = new AtomicBoolean(true);
        return () -> {
            if (registered.getAndSet(false)) {
                listeners.remove(resolved);
            }
        };
    }

    /** Returns the number of active telemetry listeners. */
    public int listenerCount() {
        return listeners.size();
    }

    public Handle schedule(int frameDelayMs, FrameTask task) {
        return schedule("frame-task", frameDelayMs, task);
    }

    /** Schedules a named frame task so diagnostics can distinguish animation sources. */
    public Handle schedule(String name, int frameDelayMs, FrameTask task) {
        return scheduleObserved(name, frameDelayMs, "none", null, task);
    }

    private Handle scheduleObserved(String name,
                                    int frameDelayMs,
                                    String curve,
                                    DoubleSupplier progressSupplier,
                                    FrameTask task) {
        Objects.requireNonNull(task, "task");
        String resolvedName = normalizeName(name);
        String resolvedCurve = curve == null || curve.isBlank() ? "none" : curve.trim();
        long animationId = animationSequence.incrementAndGet();
        long scheduledAt = System.nanoTime();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean terminalEventPublished = new AtomicBoolean(false);
        AtomicLong frameIndex = new AtomicLong();

        publish(animationId, resolvedName, AnimationEvent.Phase.SCHEDULED, 0L,
                0L, 0L, progress(progressSupplier), resolvedCurve, "");

        AnimationPulse.Subscription subscription = pulse.schedule(
                Math.max(1, frameDelayMs),
                (now, delta) -> {
                    if (!active.get()) {
                        return false;
                    }
                    long currentFrame = frameIndex.incrementAndGet();
                    long elapsed = Math.max(0L, now - scheduledAt);
                    if (started.compareAndSet(false, true)) {
                        publish(animationId, resolvedName, AnimationEvent.Phase.STARTED, currentFrame,
                                elapsed, delta, progress(progressSupplier), resolvedCurve, "");
                    }

                    boolean keepRunning;
                    try {
                        keepRunning = task.onFrame(now, delta);
                    } catch (Throwable error) {
                        active.set(false);
                        if (terminalEventPublished.compareAndSet(false, true)) {
                            publish(animationId, resolvedName, AnimationEvent.Phase.FAILED, currentFrame,
                                    elapsed, delta, progress(progressSupplier), resolvedCurve,
                                    error.getClass().getSimpleName() + ": "
                                            + Objects.toString(error.getMessage(), ""));
                        }
                        throw error;
                    }

                    publish(animationId, resolvedName, AnimationEvent.Phase.FRAME, currentFrame,
                            elapsed, delta, progress(progressSupplier), resolvedCurve, "");
                    if (!keepRunning) {
                        active.set(false);
                        if (terminalEventPublished.compareAndSet(false, true)) {
                            publish(animationId, resolvedName, AnimationEvent.Phase.COMPLETED, currentFrame,
                                    elapsed, delta, progress(progressSupplier), resolvedCurve, "");
                        }
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
                    if (terminalEventPublished.compareAndSet(false, true)) {
                        long elapsed = Math.max(0L, System.nanoTime() - scheduledAt);
                        publish(animationId, resolvedName, AnimationEvent.Phase.CANCELLED, frameIndex.get(),
                                elapsed, 0L, progress(progressSupplier), resolvedCurve, "");
                    }
                }
            }
        };
    }

    public Handle delay(int delayMs, Runnable action) {
        return delay("delay", delayMs, action);
    }

    /** Schedules a named one-shot delay for lifecycle diagnostics. */
    public Handle delay(String name, int delayMs, Runnable action) {
        Objects.requireNonNull(action, "action");
        int safeDelayMs = Math.max(0, delayMs);
        if (safeDelayMs == 0) {
            action.run();
            return completedHandle();
        }
        long startedAt = System.nanoTime();
        long delayNanos = safeDelayMs * 1_000_000L;
        return schedule(normalizeName(name), Math.min(DEFAULT_FRAME_DELAY_MS, safeDelayMs), (now, delta) -> {
            if (now - startedAt < delayNanos) {
                return true;
            }
            action.run();
            return false;
        });
    }

    public Handle interval(int intervalMs, int initialDelayMs, IntervalTask task) {
        return interval("interval", intervalMs, initialDelayMs, task);
    }

    /** Schedules a named recurring task for lifecycle diagnostics. */
    public Handle interval(String name, int intervalMs, int initialDelayMs, IntervalTask task) {
        Objects.requireNonNull(task, "task");
        int safeIntervalMs = Math.max(1, intervalMs);
        int safeInitialDelayMs = Math.max(0, initialDelayMs);
        long startedAt = System.nanoTime();
        long firstAt = startedAt + safeInitialDelayMs * 1_000_000L;
        long intervalNanos = safeIntervalMs * 1_000_000L;
        long[] nextAt = {firstAt};
        return schedule(normalizeName(name), Math.min(DEFAULT_FRAME_DELAY_MS, safeIntervalMs), (now, delta) -> {
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
        AnimationCurve resolvedCurve = curve == null ? AnimationCurve.named("linear") : curve;
        return tween(
                "tween:" + resolvedCurve.name(),
                durationMs,
                frameDelayMs,
                resolvedCurve,
                updater,
                onComplete
        );
    }

    /** Runs a named tween with observable normalized progress and curve information. */
    public Handle tween(String name,
                        int durationMs,
                        int frameDelayMs,
                        AnimationCurve curve,
                        Consumer<Float> updater,
                        Runnable onComplete) {
        Objects.requireNonNull(updater, "updater");
        AnimationCurve resolvedCurve = curve == null ? AnimationCurve.named("linear") : curve;
        int safeDurationMs = Math.max(0, durationMs);
        if (safeDurationMs == 0) {
            publishImmediateTween(name, resolvedCurve, updater, onComplete);
            return completedHandle();
        }

        long startedAt = System.nanoTime();
        long durationNanos = safeDurationMs * 1_000_000L;
        double[] progressState = {0.0};
        return scheduleObserved(
                name,
                Math.max(1, frameDelayMs),
                resolvedCurve.name(),
                () -> progressState[0],
                (now, delta) -> {
                    float progress = Math.min(1.0f, (now - startedAt) / (float) durationNanos);
                    progressState[0] = progress;
                    updater.accept(resolvedCurve.apply(progress));
                    if (progress < 1.0f) {
                        return true;
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return false;
                }
        );
    }

    private void publishImmediateTween(String name,
                                       AnimationCurve curve,
                                       Consumer<Float> updater,
                                       Runnable onComplete) {
        String resolvedName = normalizeName(name);
        long id = animationSequence.incrementAndGet();
        publish(id, resolvedName, AnimationEvent.Phase.SCHEDULED, 0L,
                0L, 0L, 0.0, curve.name(), "");
        publish(id, resolvedName, AnimationEvent.Phase.STARTED, 1L,
                0L, 0L, 0.0, curve.name(), "");
        updater.accept(curve.apply(1.0f));
        publish(id, resolvedName, AnimationEvent.Phase.FRAME, 1L,
                0L, 0L, 1.0, curve.name(), "");
        if (onComplete != null) {
            onComplete.run();
        }
        publish(id, resolvedName, AnimationEvent.Phase.COMPLETED, 1L,
                0L, 0L, 1.0, curve.name(), "");
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

    /** Returns detailed timing data including smoothed frame cost and deadline lateness. */
    public AnimationPulse.Diagnostics diagnostics() {
        return pulse.diagnostics();
    }

    public Handle completed() {
        return completedHandle();
    }

    private void publish(long animationId,
                         String name,
                         AnimationEvent.Phase phase,
                         long frameIndex,
                         long elapsedNanos,
                         long deltaNanos,
                         double progress,
                         String curve,
                         String detail) {
        if (listeners.isEmpty()) {
            return;
        }
        AnimationEvent event = new AnimationEvent(
                animationId,
                name,
                phase,
                frameIndex,
                elapsedNanos,
                deltaNanos,
                progress,
                curve,
                detail
        );
        for (AnimationListener listener : listeners) {
            try {
                listener.onAnimationEvent(event);
            } catch (RuntimeException error) {
                LOGGER.warn("Animation listener failed and was isolated: {}", error.toString());
            }
        }
    }

    private static double progress(DoubleSupplier supplier) {
        if (supplier == null) {
            return Double.NaN;
        }
        try {
            return supplier.getAsDouble();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "animation" : name.trim();
    }

    private static Handle completedHandle() {
        return new Handle() {
            @Override public boolean isActive() { return false; }
            @Override public void cancel() { }
        };
    }
}
