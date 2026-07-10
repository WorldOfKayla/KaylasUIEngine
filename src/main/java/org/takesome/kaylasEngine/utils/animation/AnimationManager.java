package org.takesome.kaylasEngine.utils.animation;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.animation.AnimationPulse;

import javax.swing.SwingUtilities;
import java.awt.Point;

/**
 * Coordinates floating-window entry and exit animation on the Swing EDT.
 *
 * <p>Window motion shares the global animation pulse and evaluates its cubic path with primitive
 * arithmetic, avoiding a timer, temporary point array and multiple {@code Point2D} allocations per
 * frame.</p>
 */
public class AnimationManager {
    private static final int FRAME_DELAY_MS = 16;
    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;
    private static final long TIMER_LAG_WARN_NANOS = 80_000_000L;
    private static final long TIMER_LAG_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final FloatingWindow floatingWindow;
    private AnimationStats animationStats;
    private final int animationDuration;
    private final int frameDelayMs;
    private AnimationPulse.Subscription activeAnimation;

    public AnimationManager(FloatingWindow floatingWindow, int animationDuration, int animationSpeed) {
        this.floatingWindow = floatingWindow;
        this.animationDuration = Math.max(FRAME_DELAY_MS, animationDuration);
        int normalizedSpeed = Math.max(1, animationSpeed);
        this.frameDelayMs = normalizedSpeed >= 60
                ? Math.max(8, 1000 / normalizedSpeed)
                : FRAME_DELAY_MS;
    }

    public void animate(boolean isEntry) {
        long requestedAt = System.nanoTime();
        if (SwingUtilities.isEventDispatchThread()) {
            animateOnEdt(isEntry);
        } else {
            SwingUtilities.invokeLater(() -> {
                logUiQueueDelay("animate", requestedAt);
                animateOnEdt(isEntry);
            });
        }
    }

    private void animateOnEdt(boolean isEntry) {
        if (floatingWindow.isAnimating()) {
            Engine.getLOGGER().debug("[ANIMATION] skipped: already animating, entry={}", isEntry);
            return;
        }

        if (activeAnimation != null) {
            activeAnimation.cancel();
            activeAnimation = null;
        }

        floatingWindow.setAnimating(true);
        if (isEntry && animationStats != null) {
            animationStats.fadeIn();
        }

        Point mainFrameCenter = floatingWindow.getCenterPoint(floatingWindow.getEngine().getFrame());
        int startX = mainFrameCenter.x - floatingWindow.getWidth() / 2;
        int startY = isEntry
                ? floatingWindow.getEngine().getFrame().getY() - floatingWindow.getHeight()
                : floatingWindow.getY();
        int targetY = mainFrameCenter.y - floatingWindow.getHeight() / 2;
        int endX = isEntry
                ? startX
                : floatingWindow.getEngine().getFrame().getX() + floatingWindow.getEngine().getFrame().getWidth();
        double middleY = (startY + targetY) / 2.0;
        float startOpacity = isEntry ? 0.0f : floatingWindow.getOpacity();
        float targetOpacity = isEntry ? 1.0f : 0.0f;

        Engine.getLOGGER().info(
                "[ANIMATION] start: entry={}, duration={} ms, frameDelay={} ms, start=({},{}), end=({},{}), opacity={} -> {}",
                isEntry,
                animationDuration,
                frameDelayMs,
                startX,
                startY,
                endX,
                targetY,
                startOpacity,
                targetOpacity
        );

        long startedAt = System.nanoTime();
        long durationNanos = animationDuration * 1_000_000L;
        long[] lastLagLogAt = {0L};
        long[] maxLagNanos = {0L};
        int[] tickCount = {0};

        activeAnimation = AnimationPulse.shared().schedule(frameDelayMs, (now, deltaNanos) -> {
            if (!floatingWindow.isDisplayable()) {
                floatingWindow.setAnimating(false);
                activeAnimation = null;
                return false;
            }

            long expectedDelayNanos = Math.max(
                    frameDelayMs,
                    AnimationPulse.shared().adaptiveFrameDelayMs()
            ) * 1_000_000L;
            long tickDelay = Math.max(0L, deltaNanos - expectedDelayNanos);
            tickCount[0]++;
            maxLagNanos[0] = Math.max(maxLagNanos[0], tickDelay);

            if (tickDelay >= TIMER_LAG_WARN_NANOS && now - lastLagLogAt[0] >= TIMER_LAG_LOG_INTERVAL_NANOS) {
                lastLagLogAt[0] = now;
                Engine.getLOGGER().warn(
                        "[ANIMATION][PULSE-LAG] entry={}, tick={}, delay={} ms",
                        isEntry,
                        tickCount[0],
                        nanosToMillis(tickDelay)
                );
            }

            float progress = Math.min(1f, (now - startedAt) / (float) durationNanos);
            float eased = easeInOut(progress);
            double x = cubic(startX, startX, endX, endX, eased);
            double y = cubic(startY, middleY, middleY, targetY, eased);
            float opacity = startOpacity + (targetOpacity - startOpacity) * eased;

            floatingWindow.setOpacity(clamp01(opacity));
            floatingWindow.setLocation((int) Math.round(x), (int) Math.round(y));

            if (progress >= 1f) {
                floatingWindow.setOpacity(targetOpacity);
                floatingWindow.setLocation(endX, targetY);
                floatingWindow.setAnimating(false);
                activeAnimation = null;
                long elapsed = System.nanoTime() - startedAt;
                Engine.getLOGGER().info(
                        "[ANIMATION] complete: entry={}, elapsed={} ms, ticks={}, maxPulseLag={} ms",
                        isEntry,
                        nanosToMillis(elapsed),
                        tickCount[0],
                        nanosToMillis(maxLagNanos[0])
                );
                if (!isEntry && animationStats != null) {
                    animationStats.fadeOut();
                }
                return false;
            }
            return true;
        });
    }

    private void logUiQueueDelay(String operation, long queuedAtNanos) {
        long delay = System.nanoTime() - queuedAtNanos;
        if (delay >= UI_QUEUE_WARN_NANOS) {
            Engine.getLOGGER().warn("[ANIMATION][EDT-QUEUE] {} waited {} ms", operation, nanosToMillis(delay));
        }
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        double inverse = 1.0 - t;
        double inverseSquared = inverse * inverse;
        double tSquared = t * t;
        return inverseSquared * inverse * p0
                + 3.0 * inverseSquared * t * p1
                + 3.0 * inverse * tSquared * p2
                + tSquared * t * p3;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static float easeInOut(float value) {
        return (float) (-0.5 * (Math.cos(Math.PI * value) - 1));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public void setAnimationStats(AnimationStats animationStats) {
        this.animationStats = animationStats;
    }
}
