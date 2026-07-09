package org.takesome.kaylasEngine.gui.components.passfield;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class PassField extends JPasswordField {
    BufferedImage texture;
    private final ComponentFactory componentFactory;
    private final String placeholder;
    private boolean caretVisible = true;
    private int paddingX;
    private int paddingY;
    private Timer caretTimer;
    private boolean isPasswordVisible = false;
    private JLabel iconLabel;
    private Icon showIcon;
    private Icon hideIcon;

    public PassField(ComponentFactory componentFactory, String placeholder) {
        this.componentFactory = componentFactory;
        this.placeholder = placeholder;

        this.setOpaque(false);

        if (componentFactory.getComponentAttribute().isrevealButton()) {
            this.showIcon = componentFactory.getIconUtils().getVectorIcon("assets/ui/icons/show.svg", 16, 16);
            this.hideIcon = componentFactory.getIconUtils().getVectorIcon("assets/ui/icons/hide.svg", 16, 16);
            // Initialize the icon label
            iconLabel = new JLabel(showIcon);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 10));
            iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            iconLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    togglePasswordVisibility();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    togglePasswordVisibility();
                }
            });
            setLayout(new BorderLayout());
            add(iconLabel, BorderLayout.EAST);
        }

        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
                startCaretBlinking();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
                stopCaretBlinking();
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

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        setEchoChar(isPasswordVisible ? (char) 0 : '*');
        iconLabel.setIcon(isPasswordVisible ? hideIcon : showIcon);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (texture != null) {
            g2.drawImage(texture, 0, 0, getWidth(), getHeight(), null);
        }

        g2.setColor(getForeground());
        FontMetrics fontMetrics = g2.getFontMetrics();
        int x = getInsets().left + paddingX;
        int y = getInsets().top + paddingY + fontMetrics.getAscent();

        char[] password = getPassword();
        if (!hasFocus() && password.length == 0 && placeholder != null) {
            g2.drawString(placeholder, x, y);
            g2.dispose();
            return;
        }

        String renderedPassword = isPasswordVisible
                ? new String(password)
                : maskedPassword(password.length);

        g2.drawString(renderedPassword, x, y);

        if (isFocusOwner() && caretVisible) {
            int caretPosition = Math.max(0, Math.min(getCaretPosition(), renderedPassword.length()));
            int caretX = x + fontMetrics.stringWidth(renderedPassword.substring(0, caretPosition));
            g2.drawLine(caretX, y - fontMetrics.getAscent(), caretX, y + fontMetrics.getDescent());
        }

        g2.dispose();
    }

    private String maskedPassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append('*');
        }
        return builder.toString();
    }

    public void setPaddingX(int paddingX) {
        this.paddingX = paddingX;
    }

    public void setPaddingY(int paddingY) {
        this.paddingY = paddingY;
    }

    public void resetText() {
        setText("");
        repaint();
        revalidate();
    }
}
