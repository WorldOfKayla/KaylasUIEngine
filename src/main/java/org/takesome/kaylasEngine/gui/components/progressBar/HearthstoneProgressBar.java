package org.takesome.kaylasEngine.gui.components.progressBar;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Standalone compatibility component that uses the same animated arcane-energy painter as the
 * declarative {@link ProgressBar} {@code hearthstone} style.
 */
public class HearthstoneProgressBar extends JProgressBar {
    private static final int FRAME_DELAY_MS = 16;

    private AnimationEngine.Handle visualAnimation;
    private long visualStartedAt = System.nanoTime();

    public HearthstoneProgressBar() {
        setMinimum(0);
        setMaximum(100);
        setBorderPainted(false);
        setOpaque(false);
        setDoubleBuffered(true);
    }

    /** Sets progress in the inclusive range {@code 0.0..1.0}. */
    public void setProgress(double progress) {
        double normalized = Math.max(0.0, Math.min(1.0, progress));
        setValue((int) Math.round(normalized * 100.0));
    }

    /** Returns progress in the inclusive range {@code 0.0..1.0}. */
    public double getProgress() {
        int span = getMaximum() - getMinimum();
        if (span <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (getValue() - getMinimum()) / (double) span));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        startVisualAnimation();
    }

    @Override
    public void removeNotify() {
        stopVisualAnimation();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            long elapsed = Math.max(0L, System.nanoTime() - visualStartedAt);
            HearthstoneProgressEffect.paintTrack(g2, width, height, elapsed);

            Rectangle content = HearthstoneProgressEffect.contentBounds(width, height);
            int fillWidth = Math.max(0, (int) Math.round(content.width * getProgress()));
            if (fillWidth > 0) {
                Rectangle fillBounds = new Rectangle(content.x, content.y, fillWidth, content.height);
                java.awt.Shape fill = new java.awt.geom.RoundRectangle2D.Double(
                        fillBounds.x,
                        fillBounds.y,
                        fillBounds.width,
                        fillBounds.height,
                        Math.max(5, content.height * 0.55),
                        Math.max(5, content.height * 0.55)
                );
                HearthstoneProgressEffect.paintFill(
                        g2,
                        fill,
                        width,
                        height,
                        elapsed,
                        0.42f,
                        -1.0f
                );
                HearthstoneProgressEffect.paintLeadingEdge(
                        g2,
                        fillBounds,
                        width,
                        height,
                        SwingConstants.HORIZONTAL,
                        false,
                        elapsed,
                        0.42f
                );
            }
        } finally {
            g2.dispose();
        }
    }

    private void startVisualAnimation() {
        if (!isDisplayable() || visualAnimation != null && visualAnimation.isActive()) {
            return;
        }
        visualStartedAt = System.nanoTime();
        visualAnimation = AnimationEngine.shared().schedule("progress:hearthstone-visual", FRAME_DELAY_MS, (now, delta) -> {
            if (!isDisplayable()) {
                visualAnimation = null;
                return false;
            }
            repaint();
            return true;
        });
    }

    private void stopVisualAnimation() {
        if (visualAnimation != null) {
            visualAnimation.cancel();
            visualAnimation = null;
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hearthstone Progress Bar Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(440, 160);
        frame.setLocationRelativeTo(null);

        HearthstoneProgressBar progressBar = new HearthstoneProgressBar();
        progressBar.setPreferredSize(new Dimension(370, 42));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 30));
        panel.add(progressBar);
        frame.add(panel);
        frame.setVisible(true);

        AnimationEngine.shared().interval("progress:hearthstone-demo", 38, 250, () -> {
            double progress = progressBar.getProgress();
            if (progress >= 1.0) {
                return false;
            }
            progressBar.setProgress(progress + 0.008);
            return true;
        });
    }
}
