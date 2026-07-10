package org.takesome.kaylasEngine.gui.components;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ComponentAttributes extends Attributes {

    public ComponentAttributes() {
        this.childComponents = new ArrayList<>();
    }

    private int rowNum, colNum, imgCount, fontSize, selectedIndex = 0;
    private boolean enabled, opaque, revealButton, repeat, lineWrap, visible, editable;
    private Object initialValue;
    private Map<String, String> styles;
    private Map<String, String> scripts;
    private List<String> fileExtensions;
    private LayoutConfig layoutConfig;
    private Gradient gradient;

    private String keyCode, tooltipStyle, border, color, localeKey, imageIcon, readFrom, loadPanel,
            type, style, id, background, thumbImage, trackImage, alignment, toolTip, showIcon,
            hideIcon, iconFloat, selectionMode, script;

    private int iconWidth, iconHeight, totalFrames, delay, minValue, minorSpacing, majorSpacing,
            maxValue, borderRadius, stepSize;
    private Bounds bounds;

    // Progress-bar component overrides. Boxed values preserve "not specified" semantics.
    private String trackColor;
    private String fillColor;
    private String borderColor;
    private String font;
    private String fontStyle;
    private String textColor;
    private String textShadowColor;
    private String stripeColor;
    private String progressText;
    private String progressTextFormat;
    private String orientation;
    private String textureMode;
    private String fillImage;

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
    private Boolean indeterminate;
    private Boolean inverted;
    private Boolean striped;
    private Boolean animateValue;
    private Boolean antialias;

    public void setInitialValue(Object initialValue) { this.initialValue = initialValue; }
    public void setSelectedIndex(int selectedIndex) { this.selectedIndex = selectedIndex; }
    public String getReadFrom() { return readFrom; }
    public String getBackground() { return background; }
    public String getComponentType() { return type; }
    public String getIconFloat() { return iconFloat; }
    public String getComponentStyle() { return style; }
    public String getComponentId() { return id; }
    public int getRowNum() { return rowNum; }
    public int getColNum() { return colNum; }
    public int getBorderRadius() { return borderRadius; }
    public String getAlignment() { return alignment; }
    public String getBorder() { return border; }
    public int getImgCount() { return imgCount; }
    public int getFontSize() { return fontSize; }
    public boolean isEnabled() { return enabled; }
    public String getKeyCode() { return keyCode; }
    public Object getInitialValue() { return initialValue; }
    public String getColor() { return color; }
    public String getLocaleKey() { return localeKey; }
    public String getImageIcon() { return imageIcon; }
    public boolean isOpaque() { return opaque; }
    public boolean isrevealButton() { return revealButton; }
    public int getIconWidth() { return iconWidth; }
    public int getIconHeight() { return iconHeight; }
    public int getTotalFrames() { return totalFrames; }
    public int getDelay() { return delay; }
    public List<String> getFileExtensions() { return fileExtensions; }
    public String getShowIcon() { return showIcon; }
    public String getHideIcon() { return hideIcon; }

    public Bounds setBounds(int x, int y, int width, int height) {
        return new Bounds(x, y, width, height);
    }

    public Rectangle getBounds() {
        if (bounds == null) {
            return new Rectangle(0, 0, 0, 0);
        }
        return bounds.getBounds();
    }

    public String getLoadPanel() { return loadPanel; }
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }
    public int getMinorSpacing() { return minorSpacing; }
    public int getMajorSpacing() { return majorSpacing; }
    public int getSelectedIndex() { return selectedIndex; }
    public String getToolTip() { return toolTip; }
    public boolean isVisible() { return visible; }
    public boolean isLineWrap() { return lineWrap; }
    public boolean isRepeat() { return repeat; }
    public boolean isEditable() { return editable; }
    public String getTooltipStyle() { return tooltipStyle; }
    public Map<String, String> getStyles() { return styles; }
    public int getStepSize() { return stepSize; }
    public String getSelectionMode() { return selectionMode; }
    public String getScript() { return script; }
    public Map<String, String> getScripts() { return scripts; }
    public Gradient getGradient() { return gradient; }
    public LayoutConfig getLayoutConfig() { return layoutConfig; }

    public String getTrackColor() { return trackColor; }
    public String getFillColor() { return fillColor; }
    public String getBorderColor() { return borderColor; }
    public String getFont() { return font; }
    public String getFontStyle() { return fontStyle; }
    public String getTextColor() { return textColor; }
    public String getTextShadowColor() { return textShadowColor; }
    public String getStripeColor() { return stripeColor; }
    public String getProgressText() { return progressText; }
    public String getProgressTextFormat() { return progressTextFormat; }
    public String getOrientation() { return orientation; }
    public String getTextureMode() { return textureMode; }
    public String getFillImage() { return fillImage; }
    public String getTrackImage() { return trackImage; }
    public String getThumbImage() { return thumbImage; }
    public Integer getBorderWidth() { return borderWidth; }
    public Integer getFillBorderRadius() { return fillBorderRadius; }
    public Integer getTrackPadding() { return trackPadding; }
    public Integer getTextShadowOffsetX() { return textShadowOffsetX; }
    public Integer getTextShadowOffsetY() { return textShadowOffsetY; }
    public Integer getAnimationDurationMs() { return animationDurationMs; }
    public Integer getAnimationFrameDelayMs() { return animationFrameDelayMs; }
    public Integer getIndeterminateCycleMs() { return indeterminateCycleMs; }
    public Integer getIndeterminateSizePercent() { return indeterminateSizePercent; }
    public Integer getStripeWidth() { return stripeWidth; }
    public Integer getStripeGap() { return stripeGap; }
    public Integer getStripeSpeedMs() { return stripeSpeedMs; }
    public Boolean getBorderPainted() { return borderPainted; }
    public Boolean getStringPainted() { return stringPainted; }
    public Boolean getShowPercent() { return showPercent; }
    public Boolean getTextShadow() { return textShadow; }
    public Boolean getIndeterminate() { return indeterminate; }
    public Boolean getInverted() { return inverted; }
    public Boolean getStriped() { return striped; }
    public Boolean getAnimateValue() { return animateValue; }
    public Boolean getAntialias() { return antialias; }

    public static class LayoutConfig {
        private ComponentConfig label;
        private ComponentConfig slider;
        private ComponentConfig spinner;
        private ComponentConfig textField;
        private ComponentConfig button;

        public ComponentConfig getLabel() { return label; }
        public ComponentConfig getSlider() { return slider; }
        public ComponentConfig getSpinner() { return spinner; }
        public ComponentConfig getTextField() { return textField; }
        public ComponentConfig getButton() { return button; }

        public ComponentConfig getForType(String componentType) {
            if (componentType == null) {
                return null;
            }
            return switch (componentType) {
                case "label" -> label;
                case "slider" -> slider;
                case "spinner" -> spinner;
                case "textField" -> textField;
                case "button" -> button;
                default -> null;
            };
        }
    }

    public static class ComponentConfig {
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex = 0;

        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getZIndex() { return zIndex; }
    }

    public static class Gradient {
        private String startColor, endColor;
        private boolean vertical;

        public String getStartColor() { return startColor; }
        public String getEndColor() { return endColor; }
        public boolean isVertical() { return vertical; }
    }
}
