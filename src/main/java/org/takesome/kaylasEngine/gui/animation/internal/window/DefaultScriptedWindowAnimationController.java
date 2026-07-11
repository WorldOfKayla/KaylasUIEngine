package org.takesome.kaylasEngine.gui.animation.internal.window;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.animation.AnimationPulse;
import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.loadingManager.ScriptedLoadingUi;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.Objects;

/**
 * Executes window transition policy supplied by an application-owned Lua loading UI script.
 */
final class DefaultScriptedWindowAnimationController implements ScriptedWindowAnimationController {
    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;
    private static final long PULSE_LAG_WARN_NANOS = 80_000_000L;
    private static final long PULSE_LAG_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final FloatingWindow window;
    private final ScriptedLoadingUi.Transition transition;

    private AnimationStats animationStats;
    private AnimationPulse.Subscription activeAnimation;
    private boolean opacitySupported = true;

    DefaultScriptedWindowAnimationController(FloatingWindow window, ScriptedLoadingUi.Transition transition) {
        this.window = Objects.requireNonNull(window, "window");
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    public void setAnimationStats(AnimationStats animationStats) {
        this.animationStats = animationStats;
    }

    public void toggleVisibility() {
        runOnEdt(() -> {
            if (window.isAnimating()) {
                Engine.getLOGGER().debug("[SCRIPTED-WINDOW] toggle skipped: animation is already active");
                return;
            }
            animateOnEdt(!window.isVisible());
        }, "toggleVisibility");
    }

    public void animate(boolean entry) {
        runOnEdt(() -> animateOnEdt(entry), "animate");
    }

    public void cancel() {
        runOnEdt(this::cancelOnEdt, "cancel");
    }

    private void animateOnEdt(boolean entry) {
        if (window.isAnimating()) {
            Engine.getLOGGER().debug("[SCRIPTED-WINDOW] animation skipped: already active, entry={}", entry);
            return;
        }

        cancelOnEdt();
        ScriptedLoadingUi.Phase phase = transition.phase(entry);
        Point current = window.getLocation();
        Point start = phase.motion().enabled()
                ? phase.motion().from().resolve(window, current)
                : current;
        Point end = phase.motion().enabled()
                ? phase.motion().to().resolve(window, current)
                : current;
        float startOpacity = phase.opacity().enabled()
                ? phase.opacity().from()
                : safeOpacity();
        float endOpacity = phase.opacity().enabled()
                ? phase.opacity().to()
                : startOpacity;

        window.setAnimating(true);
        window.setLocation(start);
        setOpacity(startOpacity);

        if (entry) {
            if (animationStats != null) {
                animationStats.fadeIn();
            } else {
                window.setVisible(true);
            }
        }

        int totalDurationMs = transition.enabled() ? phase.totalDurationMs() : 0;
        Engine.getLOGGER().info(
                "[SCRIPTED-WINDOW] start: entry={}, duration={} ms, frameDelay={} ms, motion={} {} -> {}, opacity={} {} -> {}",
                entry,
                totalDurationMs,
                phase.frameDelayMs(),
                phase.motion().enabled(),
                start,
                end,
                phase.opacity().enabled(),
                startOpacity,
                endOpacity
        );

        if (totalDurationMs <= 0) {
            applyFinalState(phase, end, endOpacity);
            complete(entry, 0L, 0, 0L);
            return;
        }

        long startedAt = System.nanoTime();
        long[] lastLagLogAt = {0L};
        long[] maxLagNanos = {0L};
        int[] tickCount = {0};

        activeAnimation = AnimationPulse.shared().schedule(phase.frameDelayMs(), (now, deltaNanos) -> {
            if (!window.isDisplayable()) {
                window.setAnimating(false);
                activeAnimation = null;
                return false;
            }

            long expectedDelayNanos = Math.max(
                    phase.frameDelayMs(),
                    AnimationPulse.shared().adaptiveFrameDelayMs()
            ) * 1_000_000L;
            long pulseLag = Math.max(0L, deltaNanos - expectedDelayNanos);
            tickCount[0]++;
            maxLagNanos[0] = Math.max(maxLagNanos[0], pulseLag);
            if (pulseLag >= PULSE_LAG_WARN_NANOS
                    && now - lastLagLogAt[0] >= PULSE_LAG_LOG_INTERVAL_NANOS) {
                lastLagLogAt[0] = now;
                Engine.getLOGGER().warn(
                        "[SCRIPTED-WINDOW][PULSE-LAG] entry={}, tick={}, delay={} ms",
                        entry,
                        tickCount[0],
                        nanosToMillis(pulseLag)
                );
            }

            long elapsedMs = Math.max(0L, (now - startedAt) / 1_000_000L);
            applyMotion(phase.motion(), elapsedMs, start, end);
            applyOpacity(phase.opacity(), elapsedMs, startOpacity, endOpacity);

            if (elapsedMs >= totalDurationMs) {
                applyFinalState(phase, end, endOpacity);
                activeAnimation = null;
                complete(entry, System.nanoTime() - startedAt, tickCount[0], maxLagNanos[0]);
                return false;
            }
            return true;
        });
    }

    private void applyMotion(ScriptedLoadingUi.Motion motion,
                             long elapsedMs,
                             Point start,
                             Point end) {
        if (!motion.enabled()) {
            return;
        }
        float progress = channelProgress(elapsedMs, motion.delayMs(), motion.durationMs());
        float eased = motion.curve().apply(progress);
        int x = Math.round(start.x + (end.x - start.x) * eased);
        int y = Math.round(start.y + (end.y - start.y) * eased);
        window.setLocation(x, y);
    }

    private void applyOpacity(ScriptedLoadingUi.Opacity opacity,
                              long elapsedMs,
                              float start,
                              float end) {
        if (!opacity.enabled()) {
            return;
        }
        float progress = channelProgress(elapsedMs, opacity.delayMs(), opacity.durationMs());
        float eased = opacity.curve().apply(progress);
        setOpacity(start + (end - start) * eased);
    }

    private void applyFinalState(ScriptedLoadingUi.Phase phase, Point end, float endOpacity) {
        if (phase.motion().enabled()) {
            window.setLocation(end);
        }
        if (phase.opacity().enabled()) {
            setOpacity(endOpacity);
        }
    }

    private void complete(boolean entry, long elapsedNanos, int ticks, long maxLagNanos) {
        window.setAnimating(false);
        Engine.getLOGGER().info(
                "[SCRIPTED-WINDOW] complete: entry={}, elapsed={} ms, ticks={}, maxPulseLag={} ms",
                entry,
                nanosToMillis(elapsedNanos),
                ticks,
                nanosToMillis(maxLagNanos)
        );
        if (!entry) {
            if (animationStats != null) {
                animationStats.fadeOut();
            } else {
                window.setVisible(false);
            }
        }
    }

    private void cancelOnEdt() {
        if (activeAnimation != null) {
            activeAnimation.cancel();
            activeAnimation = null;
        }
    }

    private float safeOpacity() {
        if (!opacitySupported) {
            return 1.0f;
        }
        try {
            return window.getOpacity();
        } catch (RuntimeException error) {
            disableOpacity(error);
            return 1.0f;
        }
    }

    private void setOpacity(float value) {
        if (!opacitySupported) {
            return;
        }
        try {
            window.setOpacity(clamp01(value));
        } catch (RuntimeException error) {
            disableOpacity(error);
        }
    }

    private void disableOpacity(RuntimeException error) {
        opacitySupported = false;
        Engine.getLOGGER().warn(
                "[SCRIPTED-WINDOW] per-window opacity is unsupported; motion animation remains enabled.",
                error
        );
    }

    private void runOnEdt(Runnable action, String operation) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        long queuedAt = System.nanoTime();
        SwingUtilities.invokeLater(() -> {
            long delay = System.nanoTime() - queuedAt;
            if (delay >= UI_QUEUE_WARN_NANOS) {
                Engine.getLOGGER().warn(
                        "[SCRIPTED-WINDOW][EDT-QUEUE] {} waited {} ms",
                        operation,
                        nanosToMillis(delay)
                );
            }
            action.run();
        });
    }

    private static float channelProgress(long elapsedMs, int delayMs, int durationMs) {
        if (elapsedMs <= delayMs) {
            return durationMs == 0 && elapsedMs == delayMs ? 1.0f : 0.0f;
        }
        if (durationMs <= 0) {
            return 1.0f;
        }
        return clamp01((elapsedMs - delayMs) / (float) durationMs);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
