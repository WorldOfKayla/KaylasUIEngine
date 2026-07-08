package org.takesome.kaylasEngine.utils.animation;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.FloatingWindow;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * Coordinates floating-window entry and exit animation on the Swing EDT.
 *
 * <p>The manager emits throttled diagnostics for delayed timer ticks and delayed EDT dispatch,
 * which makes loading-window stalls visible in logs without logging every animation frame.</p>
 */
public class AnimationManager {
    private static final int FRAME_DELAY_MS = 16;
    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;
    private static final long TIMER_LAG_WARN_NANOS = 80_000_000L;
    private static final long TIMER_LAG_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final FloatingWindow floatingWindow;
    private AnimationStats animationStats;
    private final int animationDuration;
    private final int animationSpeed;

    public AnimationManager(FloatingWindow floatingWindow, int animationDuration, int animationSpeed) {
        this.floatingWindow = floatingWindow;
        this.animationDuration = Math.max(FRAME_DELAY_MS, animationDuration);
        this.animationSpeed = Math.max(1, animationSpeed);
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

        floatingWindow.setAnimating(true);
        if (isEntry && animationStats != null) {
            animationStats.fadeIn();
        }

        Point mainFrameCenter = floatingWindow.getCenterPoint(floatingWindow.getEngine().getFrame());
        int startX = mainFrameCenter.x - floatingWindow.getWidth() / 2;
        int startY = isEntry ? floatingWindow.getEngine().getFrame().getY() - floatingWindow.getHeight() : floatingWindow.getY();
        int targetY = mainFrameCenter.y - floatingWindow.getHeight() / 2;
        int endX = isEntry ? startX : floatingWindow.getEngine().getFrame().getX() + floatingWindow.getEngine().getFrame().getWidth();
        float startOpacity = isEntry ? 0.0f : floatingWindow.getOpacity();
        float targetOpacity = isEntry ? 1.0f : 0.0f;

        Engine.getLOGGER().info(
                "[ANIMATION] start: entry={}, duration={} ms, frameDelay={} ms, start=({},{}), end=({},{}), opacity={} -> {}",
                isEntry,
                animationDuration,
                FRAME_DELAY_MS,
                startX,
                startY,
                endX,
                targetY,
                startOpacity,
                targetOpacity
        );

        Point2D[] controlPoints = {
                new Point2D.Double(startX, startY),
                new Point2D.Double(startX, (startY + targetY) / 2.0),
                new Point2D.Double(endX, (startY + targetY) / 2.0),
                new Point2D.Double(endX, targetY)
        };
        BezierCurve bezierCurve = new BezierCurve(controlPoints);
        long startedAt = System.nanoTime();
        long durationNanos = animationDuration * 1_000_000L;
        long[] lastTickAt = {startedAt};
        long[] lastLagLogAt = {0L};
        long[] maxLagNanos = {0L};
        int[] tickCount = {0};

        Timer timer = new Timer(FRAME_DELAY_MS, null);
        timer.setCoalesce(true);
        timer.addActionListener(event -> {
            long now = System.nanoTime();
            long tickDelay = Math.max(0L, now - lastTickAt[0] - FRAME_DELAY_MS * 1_000_000L);
            lastTickAt[0] = now;
            tickCount[0]++;
            maxLagNanos[0] = Math.max(maxLagNanos[0], tickDelay);

            if (tickDelay >= TIMER_LAG_WARN_NANOS && now - lastLagLogAt[0] >= TIMER_LAG_LOG_INTERVAL_NANOS) {
                lastLagLogAt[0] = now;
                Engine.getLOGGER().warn(
                        "[ANIMATION][TIMER-LAG] entry={}, tick={}, delay={} ms",
                        isEntry,
                        tickCount[0],
                        nanosToMillis(tickDelay)
                );
            }

            float progress = Math.min(1f, (now - startedAt) / (float) durationNanos);
            float eased = easeInOut(progress);
            Point2D point = bezierCurve.calculatePoint(eased);
            float opacity = startOpacity + (targetOpacity - startOpacity) * eased;

            floatingWindow.setOpacity(Math.max(0f, Math.min(1f, opacity)));
            floatingWindow.setLocation(new Point((int) point.getX(), (int) point.getY()));

            if (progress >= 1f) {
                timer.stop();
                floatingWindow.setAnimating(false);
                long elapsed = System.nanoTime() - startedAt;
                Engine.getLOGGER().info(
                        "[ANIMATION] complete: entry={}, elapsed={} ms, ticks={}, maxTimerLag={} ms",
                        isEntry,
                        nanosToMillis(elapsed),
                        tickCount[0],
                        nanosToMillis(maxLagNanos[0])
                );
                if (!isEntry && animationStats != null) {
                    animationStats.fadeOut();
                }
            }
        });
        timer.start();
    }

    private void logUiQueueDelay(String operation, long queuedAtNanos) {
        long delay = System.nanoTime() - queuedAtNanos;
        if (delay >= UI_QUEUE_WARN_NANOS) {
            Engine.getLOGGER().warn("[ANIMATION][EDT-QUEUE] {} waited {} ms", operation, nanosToMillis(delay));
        }
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private float easeInOut(float t) {
        return (float) (-0.5 * (Math.cos(Math.PI * t) - 1));
    }

    public void setAnimationStats(AnimationStats animationStats) {
        this.animationStats = animationStats;
    }
}
