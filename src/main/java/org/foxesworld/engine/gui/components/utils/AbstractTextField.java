package org.foxesworld.engine.gui.components.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

public abstract class AbstractTextField extends JComponent {
    protected BufferedImage texture;
    protected String placeholder;
    protected boolean caretVisible = true;
    protected boolean hasFocus = false;
    protected int paddingX = 0;
    protected int paddingY = 0;
    protected Timer caretTimer;

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
        if (caretTimer == null || !caretTimer.isRunning()) {
            caretTimer = new Timer(500, new ActionListener() {
                private boolean caretVisibleState = true;

                @Override
                public void actionPerformed(ActionEvent e) {
                    caretVisible = caretVisibleState;
                    caretVisibleState = !caretVisibleState;
                    repaint();
                }
            });
            caretTimer.start();
        }
    }

    private void stopCaretBlinking() {
        if (caretTimer != null) {
            caretTimer.stop();
        }
        caretVisible = true;
        repaint();
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
