package org.takesome.kaylasEngine.gui.components.button;

import org.takesome.kaylasEngine.gui.animation.AnimationPulse;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.util.Objects;

public class Button extends JButton implements MouseListener, MouseMotionListener {

    public enum IconFloat {
        LEFT, RIGHT, CENTER
    }

    private static final int ANIMATION_INTERVAL_MS = 16;
    private static final int HOVER_DURATION_MS = 140;

    private Color hoverColor;
    private boolean entered;
    private boolean pressed;
    public BufferedImage defaultTX, rolloverTX, pressedTX, lockedTX;
    private final ComponentFactory componentFactory;
    private final ComponentAttributes buttonAttributes;

    private float hoverProgress;
    private AnimationPulse.Subscription hoverAnimation;
    private IconFloat iconFloat = IconFloat.LEFT;

    public Button(ComponentFactory componentFactory, String text) {
        this.componentFactory = componentFactory;
        this.buttonAttributes = componentFactory.getComponentAttribute();

        addMouseListener(this);
        addMouseMotionListener(this);
        setText(text);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(componentFactory.getStyle().isOpaque());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public Button(ComponentFactory componentFactory, ImageIcon icon, String text) {
        this(componentFactory, text);
        setIcon(icon);
        if (buttonAttributes.getIconFloat() != null) {
            iconFloat = IconFloat.valueOf(buttonAttributes.getIconFloat());
        }
    }

    public void setIconFloat(IconFloat iconFloat) {
        this.iconFloat = Objects.requireNonNull(iconFloat);
        repaint();
    }

    public IconFloat getIconFloat() {
        return iconFloat;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int shiftY = 0;
        int width = getWidth();
        int height = getHeight();
        int leftOffset = 15;

        BufferedImage imageToDraw = defaultTX;
        if (!isEnabled()) {
            imageToDraw = lockedTX;
        } else if (pressed) {
            imageToDraw = pressedTX;
        } else if (entered) {
            if (hoverColor != null) {
                g.setColor(hoverColor);
            }
            imageToDraw = rolloverTX;
        }

        if (imageToDraw != null) {
            g.drawImage(imageToDraw, 0, shiftY, width, height, null);
        }

        shiftY = entered ? 1 : 0;
        Color foreground = getForeground() == null ? Color.WHITE : getForeground();
        Color textColor = interpolateColor(foreground, foreground.brighter(), hoverProgress);
        String text = getText();
        Icon icon = getIcon();

        float scaleFactor = 1.0f + hoverProgress * 0.1f;
        float alpha = 0.8f + hoverProgress * 0.2f;

        Graphics2D graphics2D = (Graphics2D) g.create();
        try {
            graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            if (text != null && !text.isEmpty() && icon != null) {
                FontMetrics metrics = g.getFontMetrics();
                int textWidth = metrics.stringWidth(text);
                int iconWidth = icon.getIconWidth();
                int totalWidth = iconWidth + textWidth;

                int groupX;
                int textX;
                int textY;
                int iconX;
                int iconY;

                switch (iconFloat) {
                    case LEFT -> {
                        groupX = leftOffset;
                        iconX = groupX;
                        iconY = (height - icon.getIconHeight()) / 2 + shiftY;
                        textX = groupX + iconWidth + 20;
                        textY = (height + metrics.getAscent() - metrics.getDescent()) / 2 + shiftY;
                        graphics2D.setColor(textColor);
                        graphics2D.drawString(text, textX, textY);
                        drawScaledIcon(graphics2D, icon, iconX, iconY, scaleFactor);
                    }
                    case RIGHT -> {
                        groupX = width - totalWidth + leftOffset;
                        textX = groupX;
                        textY = (height + metrics.getAscent() - metrics.getDescent()) / 2 + shiftY;
                        iconX = textX + textWidth;
                        iconY = (height - icon.getIconHeight()) / 2 + shiftY;
                        graphics2D.setColor(textColor);
                        graphics2D.drawString(text, textX, textY);
                        drawScaledIcon(graphics2D, icon, iconX, iconY, scaleFactor);
                    }
                    case CENTER -> {
                        groupX = (width - totalWidth) / 2 + leftOffset;
                        iconX = groupX;
                        iconY = (height - icon.getIconHeight()) / 2 + shiftY;
                        textX = groupX + iconWidth;
                        textY = (height + metrics.getAscent() - metrics.getDescent()) / 2 + shiftY;
                        graphics2D.setColor(textColor);
                        graphics2D.drawString(text, textX, textY);
                        drawScaledIcon(graphics2D, icon, iconX, iconY, scaleFactor);
                    }
                }
            } else if (text != null && !text.isEmpty()) {
                FontMetrics metrics = g.getFontMetrics();
                int textWidth = metrics.stringWidth(text);
                int textX = (width - textWidth) / 2;
                int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2 + shiftY;
                graphics2D.setColor(textColor);
                graphics2D.drawString(text, textX, textY);
            } else if (icon != null) {
                int iconX = (width - icon.getIconWidth()) / 2;
                int iconY = (height - icon.getIconHeight()) / 2 + shiftY;
                drawScaledIcon(graphics2D, icon, iconX, iconY, scaleFactor);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private Color interpolateColor(Color start, Color end, float progress) {
        float eased = easeInOutQuad(progress);
        int red = Math.round(start.getRed() + (end.getRed() - start.getRed()) * eased);
        int green = Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * eased);
        int blue = Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * eased);
        int alpha = Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * eased);
        return new Color(clampColor(red), clampColor(green), clampColor(blue), clampColor(alpha));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float easeInOutQuad(float value) {
        if (value < 0.5f) {
            return 2f * value * value;
        }
        float inverse = -2f * value + 2f;
        return 1f - inverse * inverse / 2f;
    }

    @Override
    public void mouseEntered(MouseEvent event) {
        if (isEnabled()) {
            entered = true;
            startAnimation();
            componentFactory.getEngine().emitSound("button", "hover");
            repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent event) {
        entered = false;
        startAnimation();
        repaint();
    }

    private void startAnimation() {
        float target = entered ? 1f : 0f;
        if (Math.abs(target - hoverProgress) <= 0.0001f) {
            hoverProgress = target;
            cancelHoverAnimation();
            return;
        }

        cancelHoverAnimation();
        float start = hoverProgress;
        float distance = Math.abs(target - start);
        long durationNanos = Math.max(1_000_000L, (long) (HOVER_DURATION_MS * distance * 1_000_000L));
        long startedAt = System.nanoTime();

        hoverAnimation = AnimationPulse.shared().schedule(ANIMATION_INTERVAL_MS, (nowNanos, deltaNanos) -> {
            if (!isDisplayable()) {
                hoverAnimation = null;
                return false;
            }

            float progress = Math.min(1f, (nowNanos - startedAt) / (float) durationNanos);
            float eased = easeInOutQuad(progress);
            hoverProgress = start + (target - start) * eased;
            repaint();

            if (progress >= 1f) {
                hoverProgress = target;
                hoverAnimation = null;
                return false;
            }
            return true;
        });
    }

    private void cancelHoverAnimation() {
        if (hoverAnimation != null) {
            hoverAnimation.cancel();
            hoverAnimation = null;
        }
    }

    @Override
    public void removeNotify() {
        cancelHoverAnimation();
        super.removeNotify();
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (isEnabled() && event.getButton() == MouseEvent.BUTTON1) {
            ButtonClick();
        }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (pressed && event.getButton() == MouseEvent.BUTTON1) {
            pressed = false;
            repaint();
        }
    }

    @Override
    public void mouseClicked(MouseEvent event) {
    }

    @Override
    public void mouseDragged(MouseEvent event) {
    }

    @Override
    public void mouseMoved(MouseEvent event) {
    }

    private void drawScaledIcon(Graphics2D graphics2D, Icon icon, int x, int y, float scale) {
        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();

        int scaledWidth = Math.round(iconWidth * scale);
        int scaledHeight = Math.round(iconHeight * scale);
        int offsetX = (scaledWidth - iconWidth) / 2;
        int offsetY = (scaledHeight - iconHeight) / 2;

        AffineTransform previousTransform = graphics2D.getTransform();
        try {
            graphics2D.translate(x - offsetX, y - offsetY);
            graphics2D.scale(scale, scale);
            icon.paintIcon(this, graphics2D, 0, 0);
        } finally {
            graphics2D.setTransform(previousTransform);
        }
    }

    public void ButtonClick() {
        String sound;
        String componentId = buttonAttributes.getComponentId() == null ? "" : buttonAttributes.getComponentId();
        if (componentId.contains("back")) {
            sound = "back";
        } else if (componentId.contains("small")) {
            sound = "clickSmall";
        } else {
            sound = "click";
        }
        componentFactory.getEngine().emitSound("button", sound);
        pressed = true;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
    }
}
