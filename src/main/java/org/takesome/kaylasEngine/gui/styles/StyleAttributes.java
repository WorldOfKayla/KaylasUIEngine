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
    private String fontStyle;
    private String selectionColor;

    // Progress-specific style properties. They remain optional for every other component type.
    private String trackColor;
    private String fillColor;
    private String textColor;
    private String textShadowColor;
    private String stripeColor;
    private String textFormat;
    private String orientation;
    private String textureMode;

    private int width;
    private int height;
    private int paddingX;
    private int paddingY;
    private int fontSize;
    private int borderRadius;
    private int iconWidth;
    private int iconHeight;
    private boolean opaque;

    private Integer borderWidth;
    private Integer fillBorderRadius;
    private Integer trackPadding;
    private Integer textShadowOffsetX;
    private Integer textShadowOffsetY;
    private Integer animationDurationMs;
    private Integer animationFrameDelayMs;
    private Integer indeterminateCycleMs;
    private Integer indeterminateSizePercent;
    private Integer stripeWidth;
    private Integer stripeGap;
    private Integer stripeSpeedMs;

    private Boolean borderPainted;
    private Boolean stringPainted;
    private Boolean showPercent;
    private Boolean textShadow;
    private Boolean inverted;
    private Boolean striped;
    private Boolean animateValue;
    private Boolean antialias;

    public static StyleAttributes defaults(String name) {
        StyleAttributes defaults = new StyleAttributes();
        defaults.name = name == null || name.isBlank() ? "default" : name;
        defaults.background = DEFAULT_BACKGROUND;
        defaults.color = DEFAULT_COLOR;
        defaults.hoverColor = DEFAULT_COLOR;
        defaults.caretColor = DEFAULT_COLOR;
        defaults.selectionColor = DEFAULT_COLOR;
        defaults.font = DEFAULT_FONT;
        defaults.fontStyle = "plain";
        defaults.fontSize = DEFAULT_FONT_SIZE;
        defaults.align = "left";
        defaults.opaque = false;

        defaults.trackColor = DEFAULT_BACKGROUND;
        defaults.fillColor = DEFAULT_COLOR;
        defaults.textColor = DEFAULT_COLOR;
        defaults.textShadowColor = "#000000a0";
        defaults.stripeColor = "#ffffff26";
        defaults.textFormat = "{percent}%";
        defaults.orientation = "horizontal";
        defaults.textureMode = "stretch";
        defaults.borderWidth = 0;
        defaults.fillBorderRadius = 0;
        defaults.trackPadding = 0;
        defaults.textShadowOffsetX = 1;
        defaults.textShadowOffsetY = 1;
        defaults.animationDurationMs = 140;
        defaults.animationFrameDelayMs = 16;
        defaults.indeterminateCycleMs = 1200;
        defaults.indeterminateSizePercent = 28;
        defaults.stripeWidth = 12;
        defaults.stripeGap = 8;
        defaults.stripeSpeedMs = 0;
        defaults.borderPainted = false;
        defaults.stringPainted = false;
        defaults.showPercent = true;
        defaults.textShadow = true;
        defaults.inverted = false;
        defaults.striped = false;
        defaults.animateValue = true;
        defaults.antialias = true;
        return defaults;
    }

    public StyleAttributes normalized(String fallbackName) {
        StyleAttributes normalized = new StyleAttributes();
        normalized.name = valueOr(name, fallbackName == null ? "default" : fallbackName);
        normalized.backgroundImage = emptyToNull(backgroundImage);
        normalized.background = valueOr(background, DEFAULT_BACKGROUND);
        normalized.color = valueOr(color, DEFAULT_COLOR);
        normalized.hoverColor = valueOr(hoverColor, normalized.color);
        normalized.caretColor = valueOr(caretColor, normalized.color);
        normalized.align = valueOr(align, "left");
        normalized.borderColor = emptyToNull(borderColor);
        normalized.trackImage = emptyToNull(trackImage);
        normalized.thumbImage = emptyToNull(thumbImage);
        normalized.texture = emptyToNull(texture);
        normalized.font = valueOr(font, DEFAULT_FONT);
        normalized.fontStyle = valueOr(fontStyle, "plain");
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

        normalized.trackColor = valueOr(trackColor, normalized.background);
        normalized.fillColor = valueOr(fillColor, normalized.color);
        normalized.textColor = valueOr(textColor, DEFAULT_COLOR);
        normalized.textShadowColor = valueOr(textShadowColor, "#000000a0");
        normalized.stripeColor = valueOr(stripeColor, "#ffffff26");
        normalized.textFormat = valueOr(textFormat, "{percent}%");
        normalized.orientation = valueOr(orientation, "horizontal");
        normalized.textureMode = valueOr(textureMode, "stretch");
        normalized.borderWidth = nonNegative(borderWidth, normalized.borderColor == null ? 0 : 1);
        normalized.fillBorderRadius = nonNegative(fillBorderRadius, normalized.borderRadius);
        normalized.trackPadding = nonNegative(trackPadding, 0);
        normalized.textShadowOffsetX = integerOr(textShadowOffsetX, 1);
        normalized.textShadowOffsetY = integerOr(textShadowOffsetY, 1);
        normalized.animationDurationMs = positive(animationDurationMs, 140);
        normalized.animationFrameDelayMs = positive(animationFrameDelayMs, 16);
        normalized.indeterminateCycleMs = positive(indeterminateCycleMs, 1200);
        normalized.indeterminateSizePercent = clamp(integerOr(indeterminateSizePercent, 28), 5, 100);
        normalized.stripeWidth = positive(stripeWidth, 12);
        normalized.stripeGap = nonNegative(stripeGap, 8);
        normalized.stripeSpeedMs = nonNegative(stripeSpeedMs, 0);
        normalized.borderPainted = boolOr(borderPainted, normalized.borderColor != null && normalized.borderWidth > 0);
        normalized.stringPainted = boolOr(stringPainted, false);
        normalized.showPercent = boolOr(showPercent, true);
        normalized.textShadow = boolOr(textShadow, true);
        normalized.inverted = boolOr(inverted, false);
        normalized.striped = boolOr(striped, false);
        normalized.animateValue = boolOr(animateValue, true);
        normalized.antialias = boolOr(antialias, true);
        return normalized;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int integerOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int nonNegative(Integer value, int fallback) {
        return value == null ? Math.max(0, fallback) : Math.max(0, value);
    }

    private boolean boolOr(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public String getName() { return name; }
    public String getBackgroundImage() { return backgroundImage; }
    public String getBackground() { return valueOr(background, DEFAULT_BACKGROUND); }
    public String getColor() { return valueOr(color, DEFAULT_COLOR); }
    public String getHoverColor() { return valueOr(hoverColor, getColor()); }
    public String getCaretColor() { return valueOr(caretColor, getColor()); }
    public String getAlign() { return valueOr(align, "left"); }
    public String getSelectionColor() { return valueOr(selectionColor, getColor()); }
    public String getBorderColor() { return emptyToNull(borderColor); }
    public boolean hasBorderColor() { return getBorderColor() != null; }
    public int getBorderRadius() { return Math.max(0, borderRadius); }
    public int getWidth() { return Math.max(0, width); }
    public int getHeight() { return Math.max(0, height); }
    public String getFont() { return valueOr(font, DEFAULT_FONT); }
    public String getFontStyle() { return valueOr(fontStyle, "plain"); }
    public int getFontSize() { return fontSize > 0 ? fontSize : DEFAULT_FONT_SIZE; }
    public String getTexture() { return emptyToNull(texture); }
    public boolean isOpaque() { return opaque; }
    public String getTrackImage() { return emptyToNull(trackImage); }
    public int getPaddingX() { return Math.max(0, paddingX); }
    public int getPaddingY() { return Math.max(0, paddingY); }
    public String getThumbImage() { return emptyToNull(thumbImage); }

    public String getTrackColor() { return valueOr(trackColor, getBackground()); }
    public String getFillColor() { return valueOr(fillColor, getColor()); }
    public String getTextColor() { return valueOr(textColor, DEFAULT_COLOR); }
    public String getTextShadowColor() { return valueOr(textShadowColor, "#000000a0"); }
    public String getStripeColor() { return valueOr(stripeColor, "#ffffff26"); }
    public String getTextFormat() { return valueOr(textFormat, "{percent}%"); }
    public String getOrientation() { return valueOr(orientation, "horizontal"); }
    public String getTextureMode() { return valueOr(textureMode, "stretch"); }
    public int getBorderWidth() { return nonNegative(borderWidth, getBorderColor() == null ? 0 : 1); }
    public int getFillBorderRadius() { return nonNegative(fillBorderRadius, getBorderRadius()); }
    public int getTrackPadding() { return nonNegative(trackPadding, 0); }
    public int getTextShadowOffsetX() { return integerOr(textShadowOffsetX, 1); }
    public int getTextShadowOffsetY() { return integerOr(textShadowOffsetY, 1); }
    public int getAnimationDurationMs() { return positive(animationDurationMs, 140); }
    public int getAnimationFrameDelayMs() { return positive(animationFrameDelayMs, 16); }
    public int getIndeterminateCycleMs() { return positive(indeterminateCycleMs, 1200); }
    public int getIndeterminateSizePercent() { return clamp(integerOr(indeterminateSizePercent, 28), 5, 100); }
    public int getStripeWidth() { return positive(stripeWidth, 12); }
    public int getStripeGap() { return nonNegative(stripeGap, 8); }
    public int getStripeSpeedMs() { return nonNegative(stripeSpeedMs, 0); }
    public boolean isBorderPainted() { return boolOr(borderPainted, getBorderColor() != null && getBorderWidth() > 0); }
    public boolean isStringPainted() { return boolOr(stringPainted, false); }
    public boolean isShowPercent() { return boolOr(showPercent, true); }
    public boolean isTextShadow() { return boolOr(textShadow, true); }
    public boolean isInverted() { return boolOr(inverted, false); }
    public boolean isStriped() { return boolOr(striped, false); }
    public boolean isAnimateValue() { return boolOr(animateValue, true); }
    public boolean isAntialias() { return boolOr(antialias, true); }

    public boolean hasTransparentBackground() {
        return "transparent".equalsIgnoreCase(background) || "#00000000".equalsIgnoreCase(background);
    }

    public boolean hasBackgroundImage() {
        return backgroundImage != null && !backgroundImage.isBlank();
    }

    public boolean isValidCSSColor(String color) { return isHexColor(color); }

    public boolean isHexColor(String color) {
        return color != null && color.matches("^#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }

    public int getIconWidth() { return Math.max(0, iconWidth); }
    public int getIconHeight() { return Math.max(0, iconHeight); }

    @Override
    public String toString() {
        return "StyleAttributes{" +
                "name='" + name + '\'' +
                ", background='" + background + '\'' +
                ", color='" + color + '\'' +
                ", textColor='" + textColor + '\'' +
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
        return width == that.width
                && height == that.height
                && opaque == that.opaque
                && Objects.equals(name, that.name)
                && Objects.equals(background, that.background)
                && Objects.equals(color, that.color)
                && Objects.equals(textColor, that.textColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, background, color, textColor, width, height, opaque);
    }
}
