package org.takesome.kaylasEngine.gui.components.scrollBar;

/*
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class CustomScrollBar extends JComponent {

    // EXPERIMENTAL
    private final int minValue;
    private final int maxValue;
    private final int extent;
    private int value;
    private boolean isDragging = false;
    private static final int SCROLLBAR_WIDTH = 16;

    private final Image thumbImage;
    private final Image trackImage;

    public CustomScrollBar(int minValue, int maxValue, int extent) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.extent = extent;
        this.value = minValue;

        thumbImage = ImageUtils.getLocalImage("assets/ui/scrollPane/thumb.png");
        trackImage = ImageUtils.getLocalImage("assets/ui/scrollPane/track.png");

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (thumbHit(e.getX(), e.getY())) {
                    isDragging = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging) {
                    updateValue(e.getY());
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            int notches = e.getWheelRotation();
            if (notches < 0) {
                value = Math.max(minValue, value - 1);
            } else {
                value = Math.min(maxValue - extent, value + 1);
            }
            repaint();
        });
    }

    private boolean thumbHit(int x, int y) {
        int thumbY = calculateThumbPosition();
        return (x >= getWidth() - SCROLLBAR_WIDTH && x <= getWidth() && y >= thumbY && y <= thumbY + getThumbSize());
    }

    private void updateValue(int mouseY) {
        int trackHeight = getHeight() - getThumbSize();
        int thumbHeight = getThumbSize();
        int newValue = (int) ((double) (mouseY - thumbHeight / 2) / (trackHeight - thumbHeight) * (maxValue - minValue - extent) + minValue);
        newValue = Math.max(minValue, Math.min(maxValue - extent, newValue));

        value = newValue;
    }

    private int calculateThumbPosition() {
        int trackHeight = getHeight() - getThumbSize();
        int thumbHeight = getThumbSize();

        return (int) ((double) (value - minValue) / (maxValue - minValue - extent) * trackHeight);
    }

    private int getThumbSize() {
        int trackHeight = getHeight();
        int thumbSize = (int) ((double) extent / (maxValue - minValue) * trackHeight);
        return Math.max(thumbSize, SCROLLBAR_WIDTH); // Ensure the thumb size is at least SCROLLBAR_WIDTH
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int trackX = getWidth() - SCROLLBAR_WIDTH;
        int trackY = 0;
        int trackWidth = SCROLLBAR_WIDTH;
        int trackHeight = getHeight();

        int thumbX = trackX;
        int thumbY = calculateThumbPosition();
        int thumbWidth = SCROLLBAR_WIDTH;
        int thumbHeight = getThumbSize();

        if (trackImage != null) {
            g.drawImage(trackImage, trackX, trackY, trackWidth, trackHeight, null);
        } else {
            g.setColor(Color.GRAY);
            g.fillRect(trackX, trackY, trackWidth, trackHeight);
        }

        if (thumbImage != null) {
            g.drawImage(thumbImage, thumbX, thumbY, thumbWidth, thumbHeight, null);
        } else {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(thumbX, thumbY, thumbWidth, thumbHeight);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Custom ScrollBar Example");
            frame.setSize(30, 300);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            CustomScrollBar scrollBar = new CustomScrollBar(0, 100, 10);
            frame.getContentPane().add(scrollBar);

            frame.setVisible(true);
        });
    }

}
 */