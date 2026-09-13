package org.takesome.kaylasEngine.gui.components.utils;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;

public abstract class AbstractTextField extends JComponent {
    protected BufferedImage texture;
    protected String placeholder;
    protected boolean caretVisible = true;
    protected boolean hasFocus = false;
    protected int paddingX = 0;
    protected int paddingY = 0;
    protected AnimationEngine.Handle caretAnimation;

    public AbstractTextField(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(false);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                hasFocus = true;
                startCaretBlinking();
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                hasFocus = false;
                stopCaretBlinking();
                repaint();
            }
        });
    }

    private void startCaretBlinking() {
        if (caretAnimation != null && caretAnimation.isActive()) {
            return;
        }
        caretVisible = true;
        caretAnimation = AnimationEngine.shared().interval("abstract-textfield:caret", 500, 500, () -> {
            if (!hasFocus || !isDisplayable()) {
                caretAnimation = null;
                return false;
            }
            caretVisible = !caretVisible;
            repaint();
            return true;
        });
    }

    private void stopCaretBlinking() {
        if (caretAnimation != null) {
            caretAnimation.cancel();
            caretAnimation = null;
        }
        caretVisible = true;
        repaint();
    }

    @Override
    public void removeNotify() {
        stopCaretBlinking();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawBackground(g2);
        drawPlaceholder(g2);
        drawText(g2);
        drawCaret(g2);

        g2.dispose();
    }

    protected void drawBackground(Graphics2D g) {
        if (texture != null) {
            g.drawImage(texture, 0, 0, getWidth(), getHeight(), null);
        }
    }

    protected void drawPlaceholder(Graphics2D g) {
        if (!hasFocus && isEmpty() && placeholder != null) {
            g.setColor(Color.GRAY);
            g.drawString(placeholder, paddingX, paddingY + g.getFontMetrics().getAscent());
        }
    }

    protected void drawCaret(Graphics2D g) {
        if (hasFocus && caretVisible) {
            int caretX = calculateCaretX(g);
            int y = paddingY + g.getFontMetrics().getAscent();
            g.drawLine(caretX, y - g.getFontMetrics().getAscent(), caretX, y + g.getFontMetrics().getDescent());
        }
    }

    protected abstract void drawText(Graphics2D g);

    protected abstract int calculateCaretX(Graphics2D g);

    protected abstract boolean isEmpty();

    public void setPaddingX(int paddingX) {
        this.paddingX = paddingX;
    }

    public void setPaddingY(int paddingY) {
        this.paddingY = paddingY;
    }

    public void setTexture(BufferedImage texture) {
        this.texture = texture;
    }
}
