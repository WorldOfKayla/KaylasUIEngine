package org.takesome.kaylasEngine.gui.components.slider;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Textured engine slider UI with a procedural fallback renderer.
 *
 * <p>The component never requires texture files to exist. If thumb/track images are absent or broken,
 * it still renders a polished engine-native slider instead of falling back to raw Swing visuals.</p>
 */
public class TexturedSliderUI extends BasicSliderUI {
    private final BufferedImage thumbImageNormal;
    private final BufferedImage thumbImageHover;
    private final BufferedImage thumbImageDisabled;
    private final BufferedImage trackImage;
    private final MouseAdapter mouseHandler;
    private final Color activeColor;
    private final Color disabledColor;
    private BufferedImage currentThumbImage;

    public TexturedSliderUI(ComponentFactory componentFactory, JSlider slider, StyleAttributes style) {
        super(slider);
        StyleAttributes safeStyle = (style == null ? StyleAttributes.defaults("slider") : style).normalized("slider");
        ImageUtils imageUtils = componentFactory.getEngine().getImageUtils();

        int sliderWidth = safeStyle.getWidth() > 0 ? safeStyle.getWidth() : Math.max(160, slider.getWidth());
        int sliderHeight = safeStyle.getHeight() > 0 ? safeStyle.getHeight() : Math.max(36, slider.getHeight());
        if (sliderWidth > 0 && sliderHeight > 0) {
            Dimension size = new Dimension(sliderWidth, sliderHeight);
            slider.setPreferredSize(size);
            slider.setMinimumSize(size);
            if (slider.getWidth() <= 0 || slider.getHeight() <= 0) {
                slider.setSize(size);
            }
        }

        this.activeColor = parseColor(safeStyle.getSelectionColor(), new Color(186, 83, 255));
        this.disabledColor = new Color(95, 88, 108, 180);
        this.trackImage = resolveTrackImage(imageUtils, safeStyle, Math.max(120, sliderWidth), Math.max(8, Math.min(14, sliderHeight / 4)));
        BufferedImage[] thumbs = resolveThumbImages(imageUtils, safeStyle, Math.max(18, sliderHeight - 12), Math.max(18, sliderHeight - 12));
        this.thumbImageNormal = thumbs[0];
        this.thumbImageHover = thumbs[1];
        this.thumbImageDisabled = thumbs[2];
        this.currentThumbImage = slider.isEnabled() ? thumbImageNormal : thumbImageDisabled;
        this.mouseHandler = createMouseHandler();
    }

    private BufferedImage resolveTrackImage(ImageUtils imageUtils, StyleAttributes style, int width, int height) {
        BufferedImage texture = loadImage(imageUtils, style.getTrackImage());
        if (texture != null) {
            return toBufferedImage(texture.getScaledInstance(width, height, Image.SCALE_SMOOTH), width, height);
        }
        return createFallbackTrack(width, height, style);
    }

    private BufferedImage[] resolveThumbImages(ImageUtils imageUtils, StyleAttributes style, int fallbackWidth, int fallbackHeight) {
        BufferedImage texture = loadImage(imageUtils, style.getThumbImage());
        if (texture != null && texture.getWidth() >= 3 && texture.getHeight() > 0) {
            int frameWidth = Math.max(1, texture.getWidth() / 3);
            int frameHeight = Math.max(1, texture.getHeight());
            int radius = style.getBorderRadius();
            try {
                return new BufferedImage[]{
                        imageUtils.getTexture(texture, radius, 0, 0, frameWidth, frameHeight),
                        imageUtils.getTexture(texture, radius, frameWidth, 0, frameWidth, frameHeight),
                        imageUtils.getTexture(texture, radius, frameWidth * 2, 0, frameWidth, frameHeight)
                };
            } catch (RuntimeException error) {
                Engine.LOGGER.warn("Unable to slice slider thumb texture '{}': {}", style.getThumbImage(), error.getMessage());
            }
        }
        return new BufferedImage[]{
                createFallbackThumb(fallbackWidth, fallbackHeight, style, false, false),
                createFallbackThumb(fallbackWidth, fallbackHeight, style, true, false),
                createFallbackThumb(fallbackWidth, fallbackHeight, style, false, true)
        };
    }

    private BufferedImage loadImage(ImageUtils imageUtils, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            BufferedImage image = imageUtils.getLocalImage(path);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
            return image;
        } catch (RuntimeException error) {
            Engine.LOGGER.warn("Unable to load slider texture '{}': {}", path, error.getMessage());
            return null;
        }
    }

    private BufferedImage createFallbackTrack(int width, int height, StyleAttributes style) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableQuality(graphics);
            Color background = parseColor(style.getBackground(), new Color(38, 28, 54));
            Color border = parseColor(style.getBorderColor(), new Color(122, 74, 166));
            int arc = Math.max(6, height);
            graphics.setColor(background.darker());
            graphics.fillRoundRect(0, 0, width, height, arc, arc);
            graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 185));
            graphics.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            graphics.setColor(new Color(255, 255, 255, 38));
            graphics.drawLine(4, 2, Math.max(4, width - 5), 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage createFallbackThumb(int width, int height, StyleAttributes style, boolean hover, boolean disabled) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableQuality(graphics);
            Color base = disabled ? disabledColor : parseColor(hover ? style.getHoverColor() : style.getColor(), new Color(196, 74, 255));
            Color border = parseColor(style.getBorderColor(), new Color(255, 185, 94));
            int arc = Math.max(6, Math.min(width, height) / 2);
            graphics.setColor(new Color(0, 0, 0, 75));
            graphics.fillRoundRect(2, 3, width - 4, height - 4, arc, arc);
            graphics.setColor(base);
            graphics.fillRoundRect(1, 1, width - 3, height - 4, arc, arc);
            graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), disabled ? 95 : 210));
            graphics.drawRoundRect(1, 1, width - 3, height - 4, arc, arc);
            graphics.setColor(new Color(255, 255, 255, disabled ? 35 : 95));
            graphics.drawLine(5, 4, Math.max(5, width - 6), 4);
        } finally {
            graphics.dispose();
        }
        return image;
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
    protected Dimension getThumbSize() {
        BufferedImage image = currentThumbImage == null ? thumbImageNormal : currentThumbImage;
        return new Dimension(image.getWidth(), image.getHeight());
    }

    @Override
    public void paintThumb(Graphics graphics) {
        BufferedImage image = slider.isEnabled() ? currentThumbImage : thumbImageDisabled;
        Rectangle knobBounds = thumbRect;
        int thumbWidth = image.getWidth();
        int thumbHeight = image.getHeight();
        int x = knobBounds.x + (knobBounds.width - thumbWidth) / 2;
        int y = knobBounds.y + (knobBounds.height - thumbHeight) / 2;

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            enableQuality(g2d);
            g2d.drawImage(image, x, y, thumbWidth, thumbHeight, null);
        } finally {
            g2d.dispose();
        }
    }

    @Override
    public void paintTrack(Graphics graphics) {
        Rectangle bounds = trackRect;
        int trackHeight = trackImage.getHeight();
        int x = bounds.x;
        int y = bounds.y + (bounds.height - trackHeight) / 2;
        int width = Math.max(1, bounds.width);

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            enableQuality(g2d);
            g2d.drawImage(trackImage, x, y, width, trackHeight, null);
            if (slider.isEnabled() && slider.getMaximum() > slider.getMinimum()) {
                int activeWidth = Math.max(0, thumbRect.x + thumbRect.width / 2 - x);
                g2d.setClip(x, y, Math.min(activeWidth, width), trackHeight);
                g2d.setColor(new Color(activeColor.getRed(), activeColor.getGreen(), activeColor.getBlue(), 115));
                g2d.fillRoundRect(x, y, width, trackHeight, trackHeight, trackHeight);
                g2d.setClip(null);
            }
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
        currentThumbImage = slider.isEnabled() ? currentThumbImage : thumbImageDisabled;
    }

    public Rectangle getTrackBoundsSnapshot() {
        return new Rectangle(trackRect);
    }

    public Rectangle getThumbBoundsSnapshot() {
        return new Rectangle(thumbRect);
    }

    public Dimension getTrackTextureSize() {
        return new Dimension(trackImage.getWidth(), trackImage.getHeight());
    }

    public Dimension getThumbTextureSize() {
        return getThumbSize();
    }

    private BufferedImage toBufferedImage(Image image, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            enableQuality(graphics);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private void enableQuality(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private Color parseColor(String value, Color fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String hex = value.trim();
        if (!hex.startsWith("#")) {
            return fallback;
        }
        try {
            if (hex.length() == 7) {
                return new Color(Integer.parseInt(hex.substring(1), 16));
            }
            if (hex.length() == 9) {
                int red = Integer.parseInt(hex.substring(1, 3), 16);
                int green = Integer.parseInt(hex.substring(3, 5), 16);
                int blue = Integer.parseInt(hex.substring(5, 7), 16);
                int alpha = Integer.parseInt(hex.substring(7, 9), 16);
                return new Color(red, green, blue, alpha);
            }
        } catch (NumberFormatException ignored) {
            return fallback;
        }
        return fallback;
    }
}
