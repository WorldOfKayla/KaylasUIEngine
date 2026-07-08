package org.takesome.kaylasEngine.utils.animation;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.FloatingWindow;

import javax.swing.SwingUtilities;
import java.awt.IllegalComponentStateException;
import java.awt.Point;

/**
 * Coordinates floating-window visibility transitions on the Swing EDT.
 *
 * <p>The loading window previously used a high-frequency timer that changed native window opacity
 * and position. On Windows this can stall the EDT while transparent top-level windows are being
 * recomposited. The manager now uses an instant transition and leaves any visual treatment to the
 * lighter application overlay.</p>
 */
public class AnimationManager {
    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;

    private final FloatingWindow floatingWindow;
    private AnimationStats animationStats;
    private final int animationDuration;
    private final int animationSpeed;

    public AnimationManager(FloatingWindow floatingWindow, int animationDuration, int animationSpeed) {
        this.floatingWindow = floatingWindow;
        this.animationDuration = Math.max(1, animationDuration);
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

        long startedAt = System.nanoTime();
        floatingWindow.setAnimating(true);

        Point mainFrameCenter = floatingWindow.getCenterPoint(floatingWindow.getEngine().getFrame());
        int targetX = mainFrameCenter.x - floatingWindow.getWidth() / 2;
        int targetY = mainFrameCenter.y - floatingWindow.getHeight() / 2;

        Engine.getLOGGER().debug(
                "[ANIMATION] instant transition: entry={}, configuredDuration={} ms, configuredSpeed={}, target=({}, {})",
                isEntry,
                animationDuration,
                animationSpeed,
                targetX,
                targetY
        );

        floatingWindow.setLocation(targetX, targetY);
        setOpacityIfSupported(isEntry ? 1.0f : 0.0f);

        if (isEntry && animationStats != null) {
            animationStats.fadeIn();
        }

        if (!isEntry && animationStats != null) {
            animationStats.fadeOut();
        }

        floatingWindow.setAnimating(false);
        Engine.getLOGGER().info(
                "[ANIMATION] complete: entry={}, mode=instant, elapsed={} ms",
                isEntry,
                nanosToMillis(System.nanoTime() - startedAt)
        );
    }

    private void setOpacityIfSupported(float opacity) {
        try {
            floatingWindow.setOpacity(Math.max(0f, Math.min(1f, opacity)));
        } catch (UnsupportedOperationException | IllegalComponentStateException ex) {
            Engine.getLOGGER().debug("[ANIMATION] window opacity is not supported by the current platform/state: {}", ex.getMessage());
        }
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

    public void setAnimationStats(AnimationStats animationStats) {
        this.animationStats = animationStats;
    }
}
