package org.takesome.kaylasEngine.gui.components.utils.tooltip;

import javax.swing.*;
import java.awt.*;

public class RoundedPanel extends JPanel {
    private int borderRadius;

    public RoundedPanel(int borderRadius) {
        this.borderRadius = borderRadius;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRoundRect(0, 0, getWidth(), getHeight(), borderRadius, borderRadius);
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        super.paintBorder(g);
    }
}
