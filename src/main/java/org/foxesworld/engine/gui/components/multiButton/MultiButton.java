package org.foxesworld.engine.gui.components.multiButton;

import org.foxesworld.engine.Engine;
import org.foxesworld.engine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class MultiButton extends JButton implements MouseListener, MouseMotionListener {
    public List<BufferedImage> img = new ArrayList<>();
    private boolean entered = false;
    private boolean pressed = false;
    private Engine engine;

    public MultiButton(ComponentFactory componentFactory) {
        this.engine = componentFactory.getEngine();
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.setBorderPainted(false);
        this.setContentAreaFilled(false);
        this.setFocusPainted(false);
        //this.setOpaque(false);
        this.setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics gmain) {
        Graphics2D g = (Graphics2D) gmain.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (this.entered && !this.pressed) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g.drawImage(this.img.get(1), 0, 0, this.getWidth(), this.getHeight(), null);
        }
        if (!this.entered) {
            g.drawImage(this.img.get(0), 0, 0, this.getWidth(), this.getHeight(), null);
        }
        if (this.pressed && this.entered) {
            this.entered = false;
            g.drawImage(this.img.get(2), 0, 0, this.getWidth(), this.getHeight(), null);
            this.pressed = false;
        }
        g.dispose();
        super.paintComponent(gmain);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
            engine.emitSound("button", "close");
            pressed = true;
            repaint();
            revalidate();
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
    public void mouseEntered(MouseEvent e) {
        engine.emitSound("button", "hover");
        this.entered = true;
    }

    @Override
    public void mouseExited(MouseEvent e) {
        this.entered = false;
    }
}
