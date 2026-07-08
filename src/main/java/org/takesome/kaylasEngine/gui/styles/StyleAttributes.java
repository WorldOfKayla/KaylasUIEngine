package org.takesome.kaylasEngine.gui.styles;

import java.util.Objects;

@SuppressWarnings("unused")
public class StyleAttributes {
    private static final String DEFAULT_COLOR = "#ffffff";
    private static final String DEFAULT_BACKGROUND = "#00000000";
    private static final String DEFAULT_FONT = "Primary";
    private static final int DEFAULT_FONT_SIZE = 14;

    private String name;
    private String backgroundImage;
    private String background;
    private String color;
    private String hoverColor;
    private String caretColor;
    private String align;
    private String borderColor;
    private String trackImage;
    private String thumbImage;
    private String texture;
    private String font;
    private String selectionColor;
    private int width;
    private int height;
    private int paddingX;
    private int paddingY;
    private int fontSize;
    private int borderRadius;
    private int iconWidth;
    private int iconHeight;
    private boolean opaque;

    public static StyleAttributes defaults(String name) {
        StyleAttributes defaults = new StyleAttributes();
        defaults.name = name == null || name.isBlank() ? "default" : name;
        defaults.background = DEFAULT_BACKGROUND;
        defaults.color = DEFAULT_COLOR;
        defaults.hoverColor = DEFAULT_COLOR;
        defaults.caretColor = DEFAULT_COLOR;
        defaults.selectionColor = DEFAULT_COLOR;
        defaults.font = DEFAULT_FONT;
        defaults.fontSize = DEFAULT_FONT_SIZE;
        defaults.align = "left";
        defaults.opaque = false;
        return defaults;
    }

    public StyleAttributes normalized(String fallbackName) {
        StyleAttributes normalized = new StyleAttributes();
        normalized.name = valueOr(name, fallbackName == null ? "default" : fallbackName);
        normalized.backgroundImage = backgroundImage;
        normalized.background = valueOr(background, DEFAULT_BACKGROUND);
        normalized.color = valueOr(color, DEFAULT_COLOR);
        normalized.hoverColor = valueOr(hoverColor, normalized.color);
        normalized.caretColor = valueOr(caretColor, normalized.color);
        normalized.align = valueOr(align, "left");
        normalized.borderColor = emptyToNull(borderColor);
        normalized.trackImage = trackImage;
        normalized.thumbImage = thumbImage;
        normalized.texture = texture;
        normalized.font = valueOr(font, DEFAULT_FONT);
        normalized.selectionColor = valueOr(selectionColor, normalized.color);
        normalized.width = Math.max(0, width);
        normalized.height = Math.max(0, height);
        normalized.paddingX = Math.max(0, paddingX);
        normalized.paddingY = Math.max(0, paddingY);
        normalized.fontSize = fontSize > 0 ? fontSize : DEFAULT_FONT_SIZE;
        normalized.borderRadius = Math.max(0, borderRadius);
        normalized.iconWidth = Math.max(0, iconWidth);
        normalized.iconHeight = Math.max(0, iconHeight);
        normalized.opaque = opaque;
        return normalized;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getName() {
        return name;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public String getBackground() {
        return valueOr(background, DEFAULT_BACKGROUND);
    }

    public String getColor() {
        return valueOr(color, DEFAULT_COLOR);
    }

    public String getHoverColor() {
        return valueOr(hoverColor, getColor());
    }

    public String getCaretColor() {
        return valueOr(caretColor, getColor());
    }

    public String getAlign() {
        return valueOr(align, "left");
    }

    public String getSelectionColor() {
        return valueOr(selectionColor, getColor());
    }

    public String getBorderColor() {
        return emptyToNull(borderColor);
    }

    public boolean hasBorderColor() {
        return getBorderColor() != null;
    }

    public int getBorderRadius() {
        return Math.max(0, borderRadius);
    }

    public int getWidth() {
        return Math.max(0, width);
    }

    public int getHeight() {
        return Math.max(0, height);
    }

    public String getFont() {
        return valueOr(font, DEFAULT_FONT);
    }

    public int getFontSize() {
        return fontSize > 0 ? fontSize : DEFAULT_FONT_SIZE;
    }

    public String getTexture() {
        return texture;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public String getTrackImage() {
        return trackImage;
    }

    public int getPaddingX() {
        return Math.max(0, paddingX);
    }

    public int getPaddingY() {
        return Math.max(0, paddingY);
    }

    public String getThumbImage() {
        return thumbImage;
    }

    public boolean hasTransparentBackground() {
        return "transparent".equalsIgnoreCase(background) || "#00000000".equalsIgnoreCase(background);
    }

    public boolean hasBackgroundImage() {
        return backgroundImage != null && !backgroundImage.isBlank();
    }

    public boolean isValidCSSColor(String color) {
        return isHexColor(color);
    }

    public boolean isHexColor(String color) {
        return color != null && color.matches("^#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }

    public int getIconWidth() {
        return Math.max(0, iconWidth);
    }

    public int getIconHeight() {
        return Math.max(0, iconHeight);
    }

    @Override
    public String toString() {
        return "StyleAttributes{" +
                "name='" + name + '\'' +
                ", background='" + background + '\'' +
                ", color='" + color + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", opaque=" + opaque +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StyleAttributes that = (StyleAttributes) o;
        return width == that.width &&
                height == that.height &&
                opaque == that.opaque &&
                Objects.equals(name, that.name) &&
                Objects.equals(background, that.background) &&
                Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, background, color, width, height, opaque);
    }
}
