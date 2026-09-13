package org.takesome.kaylasEngine.gui.animation.internal.overlay;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.gui.animation.AnimationCurve;
import org.takesome.kaylasEngine.gui.animation.AnimationEngine;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable translucent overlay for Swing layered panes.
 *
 * <p>The controller owns panel creation, coalesced alpha animation, dynamic bounds, and cleanup.
 * Callers only provide visual configuration and completion hooks.</p>
 */
final class DefaultLayeredOverlayController implements LayeredOverlayController {
    private static final AnimationCurve DEFAULT_FADE_CURVE = AnimationCurve.named("easeOutQuad");

    private final JLayeredPane layeredPane;
    private final Supplier<Rectangle> boundsSupplier;
    private final Color color;
    private final String name;
    private final int frameDelayMs;
    private final Logger logger;
    private final String logPrefix;

    private JPanel overlay;
    private AnimationEngine.Handle fadeAnimation;
    private BufferedImage frozenSnapshot;
    private int alpha;

    DefaultLayeredOverlayController(
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
        this.frameDelayMs = Math.max(1, frameDelayMs);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.logPrefix = logPrefix == null || logPrefix.isBlank() ? "[OVERLAY]" : logPrefix.trim();
    }

    @Override
    public void fadeIn(int targetAlpha, int durationMs, Runnable onComplete) {
        fadeIn(targetAlpha, durationMs, frameDelayMs, DEFAULT_FADE_CURVE, onComplete);
    }

    @Override
    public void fadeIn(int targetAlpha,
                       int durationMs,
                       int requestedFrameDelayMs,
                       AnimationCurve curve,
                       Runnable onComplete) {
        runOnEdt(() -> fadeTo(
                clampAlpha(targetAlpha),
                durationMs,
                requestedFrameDelayMs,
                curve,
                false,
                onComplete
        ));
    }

    @Override
    public void fadeOut(int durationMs, Runnable onComplete) {
        fadeOut(durationMs, frameDelayMs, DEFAULT_FADE_CURVE, onComplete);
    }

    @Override
    public void fadeOut(int durationMs,
                        int requestedFrameDelayMs,
                        AnimationCurve curve,
                        Runnable onComplete) {
        runOnEdt(() -> {
            if (overlay == null) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            fadeTo(0, durationMs, requestedFrameDelayMs, curve, true, onComplete);
        });
    }

    @Override
    public void refreshBounds() {
        runOnEdt(() -> {
            if (overlay != null) {
                overlay.setBounds(safeBounds());
                overlay.revalidate();
                overlay.repaint();
            }
        });
    }

    @Override
    public void dispose() {
        runOnEdt(() -> {
            stopTimer();
            removeOverlay();
        });
    }

    @Override
    public boolean isVisible() {
        return overlay != null && overlay.isVisible();
    }

    private void fadeTo(int targetAlpha,
                        int durationMs,
                        int requestedFrameDelayMs,
                        AnimationCurve requestedCurve,
                        boolean removeAfterFade,
                        Runnable onComplete) {
        ensureOverlay();
        stopTimer();

        int startAlpha = alpha;
        int delta = targetAlpha - startAlpha;
        int safeDurationMs = Math.max(0, durationMs);
        int safeFrameDelayMs = Math.max(1, requestedFrameDelayMs);
        AnimationCurve curve = requestedCurve == null ? DEFAULT_FADE_CURVE : requestedCurve;
        if (safeDurationMs == 0 || delta == 0) {
            setAlpha(targetAlpha);
            complete(removeAfterFade, onComplete);
            return;
        }

        long startedAt = System.nanoTime();
        logger.debug(
                "{} fade start: alpha {} -> {}, duration={} ms, frameDelay={} ms, easing={}",
                logPrefix,
                startAlpha,
                targetAlpha,
                safeDurationMs,
                safeFrameDelayMs,
                curve.name()
        );

        fadeAnimation = AnimationEngine.shared().tween(
                "overlay:" + name,
                safeDurationMs,
                safeFrameDelayMs,
                curve,
                eased -> setAlpha(Math.round(startAlpha + delta * eased)),
                () -> {
                    fadeAnimation = null;
                    setAlpha(targetAlpha);
                    logger.debug(
                            "{} fade complete: targetAlpha={}, elapsed={} ms",
                            logPrefix,
                            targetAlpha,
                            (System.nanoTime() - startedAt) / 1_000_000L
                    );
                    complete(removeAfterFade, onComplete);
                }
        );
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
        Rectangle bounds = safeBounds();
        if (overlay == null) {
            frozenSnapshot = captureSnapshot(bounds);
            overlay = new JPanel() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    Graphics2D graphics2D = (Graphics2D) graphics.create();
                    try {
                        if (frozenSnapshot != null) {
                            graphics2D.drawImage(
                                    frozenSnapshot,
                                    0,
                                    0,
                                    getWidth(),
                                    getHeight(),
                                    null
                            );
                        }
                        if (alpha > 0) {
                            graphics2D.setComposite(AlphaComposite.SrcOver.derive(alpha / 255f));
                            graphics2D.setColor(color);
                            graphics2D.fillRect(0, 0, getWidth(), getHeight());
                        }
                    } finally {
                        graphics2D.dispose();
                    }
                }
            };
            overlay.setName(name);
            overlay.setOpaque(false);
            overlay.setDoubleBuffered(true);
            overlay.setFocusable(true);
            overlay.setFocusTraversalKeysEnabled(false);
            installInputBlockers(overlay);
            logger.debug(
                    "{} frozen overlay '{}' created: bounds={} snapshot={}x{}",
                    logPrefix,
                    name,
                    bounds,
                    frozenSnapshot == null ? 0 : frozenSnapshot.getWidth(),
                    frozenSnapshot == null ? 0 : frozenSnapshot.getHeight()
            );
        }

        overlay.setBounds(bounds);
        if (overlay.getParent() != layeredPane) {
            layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        }
        layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER);
        overlay.setVisible(true);
        overlay.requestFocusInWindow();
        overlay.repaint();
    }

    private BufferedImage captureSnapshot(Rectangle bounds) {
        int width = Math.max(1, bounds.width);
        int height = Math.max(1, bounds.height);
        BufferedImage snapshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = snapshot.createGraphics();
        try {
            graphics.translate(-bounds.x, -bounds.y);
            layeredPane.printAll(graphics);
        } catch (RuntimeException error) {
            logger.warn("{} unable to capture frozen overlay snapshot: {}", logPrefix, error.getMessage());
            return null;
        } finally {
            graphics.dispose();
        }
        return snapshot;
    }

    private void installInputBlockers(JPanel target) {
        MouseAdapter mouseBlocker = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) { event.consume(); }

            @Override
            public void mouseReleased(MouseEvent event) { event.consume(); }

            @Override
            public void mouseClicked(MouseEvent event) { event.consume(); }

            @Override
            public void mouseEntered(MouseEvent event) { event.consume(); }

            @Override
            public void mouseExited(MouseEvent event) { event.consume(); }
        };
        target.addMouseListener(mouseBlocker);
        target.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) { event.consume(); }

            @Override
            public void mouseMoved(MouseEvent event) { event.consume(); }
        });
        target.addMouseWheelListener((MouseWheelEvent event) -> event.consume());
        target.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) { event.consume(); }

            @Override
            public void keyPressed(KeyEvent event) { event.consume(); }

            @Override
            public void keyReleased(KeyEvent event) { event.consume(); }
        });
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
        frozenSnapshot = null;
        overlay = null;
    }

    private void setAlpha(int value) {
        alpha = clampAlpha(value);
        if (overlay != null) {
            overlay.repaint();
        }
    }

    private void stopTimer() {
        if (fadeAnimation != null) {
            fadeAnimation.cancel();
            fadeAnimation = null;
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
