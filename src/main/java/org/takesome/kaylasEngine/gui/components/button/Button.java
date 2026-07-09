package org.takesome.kaylasEngine.gui.components.button;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class Button extends JButton implements MouseListener, MouseMotionListener {

    // Возможные положения иконки относительно текста
    public enum IconFloat {
        LEFT, RIGHT, CENTER
    }

    private Color hoverColor;
    private boolean entered = false, pressed = false;
    public BufferedImage defaultTX, rolloverTX, pressedTX, lockedTX;
    private final ComponentFactory componentFactory;
    private final ComponentAttributes buttonAttributes;

    private float hoverProgress = 0f;
    private Timer animationTimer;
    private static final float ANIMATION_SPEED = 0.2f;
    private static final int ANIMATION_INTERVAL = 16;
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

        initAnimationTimer();
    }

    public Button(ComponentFactory componentFactory, ImageIcon icon, String text) {
        this(componentFactory, text);
        setIcon(icon);
        if(buttonAttributes.getIconFloat() != null) {
            iconFloat = IconFloat.valueOf(buttonAttributes.getIconFloat());
        }
    }

    private void initAnimationTimer() {
        animationTimer = new Timer(ANIMATION_INTERVAL, e -> {
            float target = entered ? 1f : 0f;
            float delta = (target - hoverProgress) * ANIMATION_SPEED;

            if (Math.abs(delta) > 0.0001f) {
                hoverProgress += delta;
                repaint();
            } else {
                hoverProgress = target;
                animationTimer.stop();
            }
        });
    }

    /**
     * Позволяет задать расположение иконки относительно текста.
     *
     * @param iconFloat Значение Enum: LEFT, RIGHT, CENTER.
     */
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
        int w = getWidth();
        int h = getHeight();
        int leftOffset = 15; // Сдвиг влево; можно изменить по необходимости

        BufferedImage imageToDraw = defaultTX;
        if (!isEnabled()) {
            imageToDraw = lockedTX;
        } else if (pressed) {
            imageToDraw = pressedTX;
        } else if (entered) {
            g.setColor(this.hoverColor);
            imageToDraw = rolloverTX;
        }

        g.drawImage(imageToDraw, 0, shiftY, w, h, null);

        //if (isEnabled()) {
            shiftY = entered ? 1 : 0; // Анимация смещения по Y при наведении
            Color textColor = interpolateColor(getForeground(), getForeground().brighter(), hoverProgress);
            String text = getText();
            Icon icon = getIcon();

            float scaleFactor = 1.0f + (hoverProgress * 0.1f); // Увеличение до 110%
            float alpha = 0.8f + (hoverProgress * 0.2f); // Прозрачность от 0.8 до 1.0

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            if (text != null && !text.isEmpty() && icon != null) {
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int iconWidth = icon.getIconWidth();
                int totalWidth = iconWidth + textWidth; // без дополнительного отступа

                int groupX;
                int textX, textY;
                int iconX, iconY;

                switch (iconFloat) {
                    case LEFT -> {
                        groupX = leftOffset;
                        iconX = groupX;
                        iconY = (h - icon.getIconHeight()) / 2 + shiftY;
                        // Рисуем иконку слева
                        // Рисуем текст сразу после иконки с дополнительным отступом (например, 20 пикселей)
                        textX = groupX + iconWidth + 20;
                        textY = (h + fm.getAscent() - fm.getDescent()) / 2 + shiftY;
                        g.setColor(textColor);
                        g.drawString(text, textX, textY);
                        drawScaledIcon(g2d, icon, iconX, iconY, scaleFactor);
                    }
                    case RIGHT -> {
                        // Выравнивание по правому краю с учётом сдвига
                        groupX = w - totalWidth + leftOffset;
                        // Рисуем текст слева от иконки
                        textX = groupX;
                        textY = (h + fm.getAscent() - fm.getDescent()) / 2 + shiftY;
                        iconX = textX + textWidth;
                        iconY = (h - icon.getIconHeight()) / 2 + shiftY;
                        g.setColor(textColor);
                        g.drawString(text, textX, textY);
                        // Рисуем иконку сразу после текста
                        drawScaledIcon(g2d, icon, iconX, iconY, scaleFactor);
                    }
                    case CENTER -> {
                        // Центрируем группу (иконка + текст) и затем смещаем влево
                        groupX = (w - totalWidth) / 2 + leftOffset;
                        iconX = groupX;
                        iconY = (h - icon.getIconHeight()) / 2 + shiftY;
                        // Рисуем иконку
                        // Рисуем текст сразу после иконки
                        textX = groupX + iconWidth;
                        textY = (h + fm.getAscent() - fm.getDescent()) / 2 + shiftY;
                        g.setColor(textColor);
                        g.drawString(text, textX, textY);
                        drawScaledIcon(g2d, icon, iconX, iconY, scaleFactor);
                    }
                    default -> System.err.println("Button icon float is not set!");
                }
            } else if (text != null && !text.isEmpty()) {
                // Если задан только текст – центрируем его по горизонтали
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textX = (w - textWidth) / 2;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2 + shiftY;
                g.setColor(textColor);
                g.drawString(text, textX, textY);
            } else if (icon != null) {
                // Если задана только иконка – центрируем её
                int iconX = (w - icon.getIconWidth()) / 2;
                int iconY = (h - icon.getIconHeight()) / 2 + shiftY;
                icon.paintIcon(this, g, iconX, iconY);
            }
            g2d.dispose();
        //}
    }


    private Color interpolateColor(Color start, Color end, float progress) {
        float[] startHSB = Color.RGBtoHSB(start.getRed(), start.getGreen(), start.getBlue(), null);
        float[] endHSB = Color.RGBtoHSB(end.getRed(), end.getGreen(), end.getBlue(), null);

        float hue = interpolate(startHSB[0], endHSB[0], progress);
        float saturation = interpolate(startHSB[1], endHSB[1], progress);
        float brightness = interpolate(startHSB[2], endHSB[2], progress);

        return Color.getHSBColor(hue, saturation, brightness);
    }

    private float interpolate(float start, float end, float progress) {
        return start + (end - start) * easeInOutQuad(progress);
    }

    private float easeInOutQuad(float x) {
        return x < 0.5f ? 2 * x * x : 1 - (float)Math.pow(-2 * x + 2, 2) / 2;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (isEnabled()) {
            entered = true;
            startAnimation();
            componentFactory.getEngine().emitSound("button", "hover");
            repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        entered = false;
        startAnimation();
        repaint();
    }

    private void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
            ButtonClick();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (pressed && e.getButton() == MouseEvent.BUTTON1) {
            pressed = false;
            repaint();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) { }

    @Override
    public void mouseDragged(MouseEvent e) { }

    @Override
    public void mouseMoved(MouseEvent e) { }

    private void drawScaledIcon(Graphics2D g2d, Icon icon, int x, int y, float scale) {
        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();

        int scaledWidth = Math.round(iconWidth * scale);
        int scaledHeight = Math.round(iconHeight * scale);

        int offsetX = (scaledWidth - iconWidth) / 2;
        int offsetY = (scaledHeight - iconHeight) / 2;

        g2d.translate(x - offsetX, y - offsetY);
        g2d.scale(scale, scale);
        icon.paintIcon(this, g2d, 0, 0);
        g2d.scale(1.0 / scale, 1.0 / scale);
        g2d.translate(-(x - offsetX), -(y - offsetY));
    }


    public void ButtonClick() {
        String sound;
        String componentId = this.buttonAttributes.getComponentId() == null ? "" : this.buttonAttributes.getComponentId();
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
