package org.takesome.kaylasEngine.gui.animation;

import org.apache.logging.log4j.Logger;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable translucent overlay for Swing layered panes.
 *
 * <p>The controller owns panel creation, coalesced alpha animation, dynamic bounds, and cleanup.
 * Callers only provide visual configuration and completion hooks.</p>
 */
public final class LayeredPaneOverlay {
    private final JLayeredPane layeredPane;
    private final Supplier<Rectangle> boundsSupplier;
    private final Color color;
    private final String name;
    private final int frameDelayMs;
    private final Logger logger;
    private final String logPrefix;

    private JPanel overlay;
    private Timer fadeTimer;
    private int alpha;

    public LayeredPaneOverlay(
            JLayeredPane layeredPane,
            Supplier<Rectangle> boundsSupplier,
            Color color,
            String name,
            int frameDelayMs,
            Logger logger,
            String logPrefix
    ) {
        this.layeredPane = Objects.requireNonNull(layeredPane, "layeredPane");
        this.boundsSupplier = Objects.requireNonNull(boundsSupplier, "boundsSupplier");
        this.color = Objects.requireNonNull(color, "color");
        this.name = name == null || name.isBlank() ? "engineOverlay" : name.trim();
        this.frameDelayMs = Math.max(16, frameDelayMs);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.logPrefix = logPrefix == null || logPrefix.isBlank() ? "[OVERLAY]" : logPrefix.trim();
    }

    public void fadeIn(int targetAlpha, int durationMs, Runnable onComplete) {
        runOnEdt(() -> fadeTo(clampAlpha(targetAlpha), durationMs, false, onComplete));
    }

    public void fadeOut(int durationMs, Runnable onComplete) {
        runOnEdt(() -> {
            if (overlay == null) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            fadeTo(0, durationMs, true, onComplete);
        });
    }

    public void refreshBounds() {
        runOnEdt(() -> {
            if (overlay != null) {
                overlay.setBounds(safeBounds());
                overlay.revalidate();
                overlay.repaint();
            }
        });
    }

    public void dispose() {
        runOnEdt(() -> {
            stopTimer();
            removeOverlay();
        });
    }

    public boolean isVisible() {
        return overlay != null && overlay.isVisible();
    }

    private void fadeTo(int targetAlpha, int durationMs, boolean removeAfterFade, Runnable onComplete) {
        ensureOverlay();
        stopTimer();

        int startAlpha = alpha;
        int delta = targetAlpha - startAlpha;
        int safeDurationMs = Math.max(0, durationMs);
        if (safeDurationMs == 0 || delta == 0) {
            setAlpha(targetAlpha);
            complete(removeAfterFade, onComplete);
            return;
        }

        long startedAt = System.nanoTime();
        long durationNanos = safeDurationMs * 1_000_000L;
        logger.debug("{} fade start: alpha {} -> {}, duration={} ms", logPrefix, startAlpha, targetAlpha, safeDurationMs);

        fadeTimer = new Timer(frameDelayMs, event -> {
            float progress = Math.min(1f, (System.nanoTime() - startedAt) / (float) durationNanos);
            float eased = 1f - (1f - progress) * (1f - progress);
            setAlpha(Math.round(startAlpha + delta * eased));
            if (progress >= 1f) {
                stopTimer();
                setAlpha(targetAlpha);
                logger.debug(
                        "{} fade complete: targetAlpha={}, elapsed={} ms",
                        logPrefix,
                        targetAlpha,
                        (System.nanoTime() - startedAt) / 1_000_000L
                );
                complete(removeAfterFade, onComplete);
            }
        });
        fadeTimer.setInitialDelay(0);
        fadeTimer.setCoalesce(true);
        fadeTimer.start();
    }

    private void complete(boolean removeAfterFade, Runnable onComplete) {
        if (removeAfterFade) {
            removeOverlay();
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void ensureOverlay() {
        if (overlay == null) {
            overlay = new JPanel() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    if (alpha <= 0) {
                        return;
                    }
                    Graphics2D graphics2D = (Graphics2D) graphics.create();
                    try {
                        graphics2D.setComposite(AlphaComposite.SrcOver.derive(alpha / 255f));
                        graphics2D.setColor(color);
                        graphics2D.fillRect(0, 0, getWidth(), getHeight());
                    } finally {
                        graphics2D.dispose();
                    }
                }
            };
            overlay.setName(name);
            overlay.setOpaque(false);
            overlay.setDoubleBuffered(true);
            logger.debug("{} overlay '{}' created", logPrefix, name);
        }

        overlay.setBounds(safeBounds());
        if (overlay.getParent() != layeredPane) {
            layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        }
        layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER);
        overlay.setVisible(true);
        overlay.repaint();
    }

    private Rectangle safeBounds() {
        Rectangle bounds = boundsSupplier.get();
        if (bounds == null) {
            return new Rectangle(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        }
        return new Rectangle(bounds);
    }

    private void removeOverlay() {
        if (overlay == null) {
            return;
        }
        Container parent = overlay.getParent();
        if (parent != null) {
            parent.remove(overlay);
            parent.revalidate();
            parent.repaint();
        }
        alpha = 0;
        overlay = null;
    }

    private void setAlpha(int value) {
        alpha = clampAlpha(value);
        if (overlay != null) {
            overlay.repaint();
        }
    }

    private void stopTimer() {
        if (fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;
        }
    }

    private static int clampAlpha(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
