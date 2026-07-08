package org.takesome.kaylasEngine.gui.components.panel;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.Bounds;
import org.takesome.kaylasEngine.gui.components.frame.FrameAttributes;
import org.takesome.kaylasEngine.gui.components.frame.FrameConstructor;
import org.takesome.kaylasEngine.utils.CurrentMonth;
import org.takesome.kaylasEngine.utils.DragListener;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/**
 * A custom Swing {@link JPanel} implementation used by the GUI frame system.
 *
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Act as a base panel that supports an alpha transparency field (0.0 — fully transparent, 1.0 — fully opaque).</li>
 *     <li>Provide a root panel that respects the {@code alpha} composite when painting and optionally
 *     draws a seasonal or configured background texture.</li>
 *     <li>Create configured group panels with rounded corners, borders, background images or textures,
 *     custom layouts, focus and drag listeners.</li>
 * </ul>
 *
 *
 * <p>
 * Painting operations apply the panel-wide {@code alpha} value so that children and backgrounds
 * are rendered with the specified transparency.
 * </p>
 */
public class Panel extends JPanel {
    private FrameAttributes frameAttributes;
    private JPanel groupPanel;
    private final FrameConstructor frameConstructor;
    private BufferedImage texture;

    // Alpha transparency field: 1.0 = fully opaque, 0.0 = fully transparent.
    private float alpha = 1.0f;

    /**
     * Constructs a Panel backed by the provided {@link FrameConstructor}.
     *
     * @param frameConstructor frame constructor used to resolve resources (fonts, images, etc.).
     */
    public Panel(FrameConstructor frameConstructor) {
        this.frameConstructor = frameConstructor;
        // Set opacity according to alpha
        this.setOpaque(alpha >= 1.0f);
    }

    /**
     * Returns the current alpha (transparency) value.
     *
     * @return alpha in range [0.0, 1.0].
     */
    public float getAlpha() {
        return alpha;
    }

    /**
     * Sets the panel alpha (transparency).
     *
     * <p>
     * Values outside the [0.0, 1.0] range are clamped. Changing the alpha updates the panel's
     * opaque flag and triggers a repaint.
     * </p>
     *
     * @param alpha new alpha value (0.0 — fully transparent, 1.0 — fully opaque).
     */
    public void setAlpha(float alpha) {
        if (alpha < 0f) {
            alpha = 0f;
        } else if (alpha > 1f) {
            alpha = 1f;
        }
        this.alpha = alpha;
        this.setOpaque(alpha >= 1.0f);
        repaint();
    }

    /**
     * Creates and returns the root panel for the frame using the provided {@link FrameAttributes}.
     *
     * <p>
     * The returned panel paints using the parent's {@link #alpha} composite. If a texture is set
     * via {@link #setTexture(BufferedImage)} it will be drawn stretched to the panel size;
     * otherwise a seasonally chosen background is loaded and darkened according to frame attributes.
     * </p>
     *
     * @param frameAttributes frame attributes describing background and seasonal images.
     * @return a configured root {@link JPanel} instance (named "rootPanel").
     */
    public JPanel setRootPanel(FrameAttributes frameAttributes) {
        this.frameAttributes = frameAttributes;

        // Example root panel that respects the alpha field
        JPanel rootPanel = new JPanel(null, true) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    // Apply panel alpha
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Panel.this.alpha));
                    super.paintComponent(g2d);
                    if (texture != null) {
                        g2d.drawImage(texture, 0, 0, getWidth(), getHeight(), this);
                    } else {
                        drawDarkenedBackground(g2d);
                    }
                } finally {
                    g2d.dispose();
                }
            }
        };

        rootPanel.setOpaque(false);
        rootPanel.setName("rootPanel");
        return rootPanel;
    }

    /**
     * Sets a custom texture image for the panel. The texture will be used when painting the panel.
     *
     * @param newTexture texture image to apply; may be {@code null} to revert to the seasonal background.
     */
    public void setTexture(BufferedImage newTexture) {
        this.texture = newTexture;
        repaint(); // Repaint panel with the new texture
    }

    /**
     * Draws a darkened background based on the frame's seasonal background image.
     *
     * @param g graphics context to draw into.
     */
    private void drawDarkenedBackground(Graphics g) {
        BufferedImage backgroundImage = frameConstructor.getAppFrame()
                .getImageUtils()
                .getLocalImage(getSeasonalBackground());
        g.drawImage(applyDarkening(backgroundImage, hexToColor(frameAttributes.getBackgroundBlur())), 0, 0, null);
    }

    /**
     * Selects a seasonal background image path according to the current month.
     *
     * @return the configured seasonal image key from {@link FrameAttributes}.
     */
    private String getSeasonalBackground() {
        return switch (CurrentMonth.getCurrentMonth()) {
            case DECEMBER, JANUARY, FEBRUARY -> frameAttributes.getWinterImage();
            case MARCH, APRIL, MAY -> frameAttributes.getSpringImage();
            case JUNE, JULY, AUGUST -> frameAttributes.getSummerImage();
            case SEPTEMBER, OCTOBER, NOVEMBER -> frameAttributes.getAutumnImage();
        };
    }

    /**
     * Returns a new image that is visually darkened by overlaying a semi-transparent color.
     *
     * @param image          original source image.
     * @param darkeningColor overlay color used to darken (alpha of overlay is set to 0.5f).
     * @return a new {@link BufferedImage} with the darkening applied.
     */
    private BufferedImage applyDarkening(BufferedImage image, Color darkeningColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage darkenedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = darkenedImage.createGraphics();

        g2d.drawImage(image, 0, 0, null);

        BufferedImage alphaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gAlpha = alphaImage.createGraphics();

        gAlpha.setColor(new Color(0, 0, 0, 0));
        gAlpha.fillRect(0, 0, width, height);

        gAlpha.setColor(darkeningColor);
        gAlpha.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        gAlpha.fillRect(0, 0, width, height);

        g2d.drawImage(alphaImage, 0, 0, null);

        g2d.dispose();
        gAlpha.dispose();

        return darkenedImage;
    }

    /**
     * Creates a configured group panel according to {@link PanelAttributes}.
     *
     * <p>
     * The created panel supports:
     * <ul>
     *     <li>Optional background texture or image (darkened by provided color).</li>
     *     <li>Rounded corners controlled by {@code cornerRadius}.</li>
     *     <li>Custom border parsed from a comma-separated string.</li>
     *     <li>Focusable and dragger listener support.</li>
     *     <li>Bounds and layout application.</li>
     * </ul>
     *
     *
     * @param panelOptions   configuration options for the panel.
     * @param groupName      name to assign to the created group panel.
     * @param frameConstructor frame constructor used for resource lookup.
     * @return configured {@link JPanel} instance.
     */
    public JPanel createGroupPanel(PanelAttributes panelOptions, String groupName, FrameConstructor frameConstructor) {
        groupPanel = new JPanel(null, panelOptions.isDoubleBuffered()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Apply panel alpha
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Panel.this.alpha));
                super.paintComponent(g2d);

                if (texture != null) {
                    g2d.drawImage(texture, 0, 0, getWidth(), getHeight(), this);
                } else if (panelOptions.getBackgroundImage() != null) {
                    BufferedImage backgroundImage = frameConstructor.getAppFrame()
                            .getImageUtils()
                            .getLocalImage(panelOptions.getBackgroundImage());
                    g2d.drawImage(applyDarkening(backgroundImage, hexToColor(panelOptions.getBackground())), 0, 0, null);
                }

                if (panelOptions.getCornerRadius() != 0) {
                    int cornerRadius = panelOptions.getCornerRadius();
                    RoundRectangle2D roundedRectangle = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
                    g2d.setColor(getBackground());
                    g2d.fill(roundedRectangle);
                    g2d.setColor(getForeground());
                    g2d.draw(roundedRectangle);
                }
                g2d.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
                if (panelOptions.getCornerRadius() != 0) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cornerRadius = panelOptions.getCornerRadius();
                    RoundRectangle2D roundedRectangle = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
                    g2d.setColor(getForeground());
                    g2d.draw(roundedRectangle);
                    g2d.dispose();
                }
            }
        };

        groupPanel.setName(groupName);
        groupPanel.setOpaque(panelOptions.getCornerRadius() == 0 && panelOptions.isOpaque());
        groupPanel.setBackground(hexToColor(panelOptions.getBackground()));
        if (panelOptions.getBorder() != null && !panelOptions.getBorder().isEmpty()) {
            createBorder(groupPanel, panelOptions.getBorder());
        }

        if (panelOptions.getListener() != null) {

            if ("dragger".equals(panelOptions.getListener())) {
                DragListener dragListener = new DragListener(frameConstructor);
                dragListener.apply(groupPanel, this.frameConstructor.getAppFrame().getFrame());
            }
        }

        if (panelOptions.isFocusable()) {
            groupPanel.setFocusable(true);
            groupPanel.requestFocus();
        }

        Bounds bounds = panelOptions.getBounds();
        groupPanel.setBounds(bounds.getX(), bounds.getY(), bounds.getSize().getWidth(), bounds.getSize().getHeight());
        if (panelOptions.getLayout() != null) {
            groupPanel.setLayout(getLayout(panelOptions.getLayout(), groupPanel));
        }

        return groupPanel;
    }

    /**
     * Resolves a layout manager from a string identifier.
     *
     * @param layout layout name (case-insensitive), e.g. "flow", "border", "grid", "gridbag", "box".
     * @param panel  panel required by some layout constructors (BoxLayout).
     * @return {@link LayoutManager} instance or {@code null} if the layout type is invalid.
     */
    private LayoutManager getLayout(String layout, JPanel panel) {
        return switch (layout.toLowerCase()) {
            case "flow" -> new FlowLayout();
            case "border" -> new BorderLayout();
            case "grid" -> new GridLayout();
            case "gridbag" -> new GridBagLayout();
            case "box" -> new BoxLayout(panel, BoxLayout.X_AXIS);
            default -> {
                Engine.LOGGER.error("Invalid layout type: " + layout);
                yield null;
            }
        };
    }

    /**
     * Parses a comma-separated border definition and applies a {@link MatteBorder} to the panel.
     *
     * <p>
     * Expected format: "top,left,bottom,right,#RRGGBB" where the first four values are integer thicknesses
     * and the last value is a hex color string.
     * </p>
     *
     * @param groupPanel target panel to apply the border to.
     * @param border     comma-separated border descriptor.
     */
    private void createBorder(JPanel groupPanel, String border) {
        String[] borderData = border.split(",");
        int top = Integer.parseInt(borderData[0]);
        int left = Integer.parseInt(borderData[1]);
        int bottom = Integer.parseInt(borderData[2]);
        int right = Integer.parseInt(borderData[3]);
        Color borderColor = hexToColor(borderData[4]);
        groupPanel.setBorder(new MatteBorder(top, left, bottom, right, borderColor));
    }
}
