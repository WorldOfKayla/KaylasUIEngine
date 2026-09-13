package org.takesome.kaylasEngine.gui.components.utils.tooltip;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class CustomTooltip extends JWindow {
    private static final List<WeakReference<CustomTooltip>> activeTooltips = new ArrayList<>();
    private final JLabel label;
    private AnimationEngine.Handle autoHideAnimation;
    private AnimationEngine.Handle fadeOutAnimation;
    private float currentOpacity = 1.0f;

    public CustomTooltip(Color backgroundColor, Color textColor, int borderRadius, Font font) {
        setLayout(new BorderLayout());
        setBackground(new Color(0, 0, 0, 0));

        RoundedPanel panel = new RoundedPanel(borderRadius);
        panel.setBackground(backgroundColor);
        panel.setLayout(new BorderLayout());
        panel.setOpaque(false);

        label = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentOpacity));
                super.paintComponent(g2d);
                g2d.dispose();
            }
        };
        label.setForeground(textColor);
        label.setFont(font);
        label.setHorizontalAlignment(JLabel.CENTER);
        panel.add(label, BorderLayout.CENTER);

        add(panel, BorderLayout.CENTER);
        setSize(150, 50);
        setFocusableWindowState(false);
    }

    public void attachToComponent(Component component, String tooltipText, int autoHideDelay) {
        if (component.isEnabled()) {
            label.setText(tooltipText);
            setSize(Math.max(150, tooltipText.length() * 10), 50);
            activeTooltips.add(new WeakReference<>(this));

            component.addMouseListener(new MouseAdapter() {
                private AnimationEngine.Handle hoverDelayAnimation;

                @Override
                public void mouseEntered(MouseEvent e) {
                    cancel(hoverDelayAnimation);
                    hoverDelayAnimation = AnimationEngine.shared().delay("tooltip:hover-delay", 500, () -> {
                        hoverDelayAnimation = null;
                        if (component.isShowing()) {
                            Point location = component.getLocationOnScreen();
                            setLocation(location.x, location.y + component.getHeight() + 5);
                            currentOpacity = 1.0f;
                            repaint();
                            setVisible(true);
                            startAutoHideTimer(autoHideDelay);
                        }
                    });
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cancel(hoverDelayAnimation);
                    hoverDelayAnimation = null;
                    cancelAutoHideTimer();
                    fadeOutTooltip();
                }
            });
        }
    }

    private void startAutoHideTimer(int delay) {
        cancelAutoHideTimer();
        autoHideAnimation = AnimationEngine.shared().delay("tooltip:auto-hide", Math.max(0, delay), () -> {
            autoHideAnimation = null;
            fadeOutTooltip();
        });
    }

    private void cancelAutoHideTimer() {
        cancel(autoHideAnimation);
        autoHideAnimation = null;
    }

    private void fadeOutTooltip() {
        cancel(fadeOutAnimation);
        float startOpacity = currentOpacity;
        fadeOutAnimation = AnimationEngine.shared().tween(
                "tooltip:fade-out",
                300,
                16,
                AnimationEngine.shared().curve("easeOutCubic"),
                eased -> {
                    currentOpacity = Math.max(0.0f, startOpacity * (1.0f - eased));
                    repaint();
                },
                () -> {
                    fadeOutAnimation = null;
                    currentOpacity = 0.0f;
                    setVisible(false);
                    activeTooltips.removeIf(ref -> ref.get() == CustomTooltip.this);
                    dispose();
                }
        );
    }

    @Override
    public void dispose() {
        cancel(autoHideAnimation);
        autoHideAnimation = null;
        cancel(fadeOutAnimation);
        fadeOutAnimation = null;
        super.dispose();
    }

    private static void cancel(AnimationEngine.Handle animation) {
        if (animation != null) {
            animation.cancel();
        }
    }

    public void clearAllTooltips() {
        for (WeakReference<CustomTooltip> ref : new ArrayList<>(activeTooltips)) {
            CustomTooltip tooltip = ref.get();
            if (tooltip != null) {
                tooltip.setVisible(false);
                tooltip.dispose();
            }
        }
        activeTooltips.clear();
    }

    private static class RoundedPanel extends JPanel {
        private final int borderRadius;

        public RoundedPanel(int borderRadius) {
            this.borderRadius = borderRadius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Dimension arcs = new Dimension(borderRadius, borderRadius);
            int width = getWidth();
            int height = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, width, height, arcs.width, arcs.height);
            g2.dispose();
        }
    }
}
