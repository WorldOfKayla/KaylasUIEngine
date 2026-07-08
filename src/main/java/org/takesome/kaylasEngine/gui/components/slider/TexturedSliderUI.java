package org.takesome.kaylasEngine.gui.components.slider;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TexturedSliderUI extends BasicSliderUI {
    private final BufferedImage thumbImageNormal;
    private final BufferedImage thumbImageHover;
    private final BufferedImage thumbImageDisabled;
    private final ImageIcon trackImage;
    private final MouseAdapter mouseHandler;
    private BufferedImage currentThumbImage;

    public TexturedSliderUI(ComponentFactory componentFactory, JSlider slider, StyleAttributes style) {
        super(slider);
        ImageUtils imageUtils = componentFactory.getEngine().getImageUtils();
        StyleAttributes safeStyle = style == null ? StyleAttributes.defaults("slider") : style;

        BufferedImage thumbTexture = imageUtils.getLocalImage(safeStyle.getThumbImage());
        int thumbWidth = Math.max(1, thumbTexture.getWidth() / 3);
        int thumbHeight = Math.max(1, thumbTexture.getHeight());

        int radius = safeStyle.getBorderRadius();
        this.thumbImageNormal = imageUtils.getTexture(thumbTexture, radius, 0, 0, thumbWidth, thumbHeight);
        this.thumbImageHover = imageUtils.getTexture(thumbTexture, radius, thumbWidth, 0, thumbWidth, thumbHeight);
        this.thumbImageDisabled = imageUtils.getTexture(thumbTexture, radius, thumbWidth * 2, 0, thumbWidth, thumbHeight);

        int trackWidth = Math.max(1, safeStyle.getWidth() > 0 ? safeStyle.getWidth() : slider.getWidth());
        int trackHeight = Math.max(1, safeStyle.getHeight() > 0 ? Math.min(safeStyle.getHeight(), 10) : 10);
        Image scaledTrack = imageUtils.getScaledImage(imageUtils.getLocalImage(safeStyle.getTrackImage()), trackWidth, trackHeight);
        this.trackImage = new ImageIcon(scaledTrack);
        this.currentThumbImage = slider.isEnabled() ? thumbImageNormal : thumbImageDisabled;
        this.mouseHandler = createMouseHandler();

        int sliderWidth = safeStyle.getWidth() > 0 ? safeStyle.getWidth() : slider.getWidth();
        int sliderHeight = safeStyle.getHeight() > 0 ? safeStyle.getHeight() : slider.getHeight();
        if (sliderWidth > 0 && sliderHeight > 0) {
            slider.setSize(sliderWidth, sliderHeight);
        }
    }

    private MouseAdapter createMouseHandler() {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (slider.isEnabled()) {
                    currentThumbImage = thumbImageHover;
                    slider.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (slider.isEnabled()) {
                    currentThumbImage = thumbImageNormal;
                    slider.repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (slider.isEnabled()) {
                    currentThumbImage = thumbImageHover;
                    slider.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (slider.isEnabled()) {
                    currentThumbImage = thumbImageNormal;
                    slider.repaint();
                }
            }
        };
    }

    @Override
    public void installUI(JComponent component) {
        super.installUI(component);
        slider.addMouseListener(mouseHandler);
        slider.addMouseMotionListener(mouseHandler);
        currentThumbImage = slider.isEnabled() ? thumbImageNormal : thumbImageDisabled;
    }

    @Override
    public void uninstallUI(JComponent component) {
        slider.removeMouseListener(mouseHandler);
        slider.removeMouseMotionListener(mouseHandler);
        super.uninstallUI(component);
    }

    @Override
    public void paintThumb(Graphics graphics) {
        Rectangle knobBounds = thumbRect;
        int thumbWidth = currentThumbImage.getWidth();
        int thumbHeight = currentThumbImage.getHeight();
        int x = knobBounds.x + (knobBounds.width - thumbWidth) / 2;
        int y = knobBounds.y + (knobBounds.height - thumbHeight) / 2;

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.drawImage(currentThumbImage, x, y, thumbWidth, thumbHeight, null);
        } finally {
            g2d.dispose();
        }
    }

    @Override
    public void paintTrack(Graphics graphics) {
        Rectangle trackBounds = trackRect;
        int trackWidth = trackImage.getIconWidth();
        int trackHeight = trackImage.getIconHeight();
        int x = trackBounds.x + (trackBounds.width - trackWidth) / 2;
        int y = trackBounds.y + (trackBounds.height - trackHeight) / 2;

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.drawImage(trackImage.getImage(), x, y, trackWidth, trackHeight, null);
        } finally {
            g2d.dispose();
        }
    }

    @Override
    public void paintFocus(Graphics graphics) {
        // Focus ring intentionally disabled for textured slider.
    }

    @Override
    public void setThumbLocation(int x, int y) {
        super.setThumbLocation(x, y);
        currentThumbImage = slider.isEnabled() ? thumbImageNormal : thumbImageDisabled;
    }
}
