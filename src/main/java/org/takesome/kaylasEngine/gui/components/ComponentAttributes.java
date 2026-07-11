package org.takesome.kaylasEngine.gui.components;

import com.google.gson.Gson;
import org.takesome.kaylasEngine.gui.styles.StyleProvider;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative component configuration shared by XML descriptors and programmatic builders.
 *
 * <p>The descriptor keeps optional values nullable where absence has different semantics from an
 * explicit false value. Common descriptors can compose multiple style classes and apply final
 * per-instance overrides without creating another style declaration.</p>
 */
@SuppressWarnings("unused")
public class ComponentAttributes extends Attributes {
    private static final Gson COPY_GSON = new Gson();

    public ComponentAttributes() {
        this.childComponents = new ArrayList<>();
    }

    private int rowNum;
    private int colNum;
    private int imgCount;
    private int fontSize;
    private int selectedIndex;
    private int iconWidth;
    private int iconHeight;
    private int totalFrames;
    private int delay;
    private int minValue;
    private int minorSpacing;
    private int majorSpacing;
    private int maxValue;
    private int borderRadius;
    private int stepSize;

    private Boolean enabled;
    private Boolean opaque;
    private Boolean revealButton;
    private Boolean repeat;
    private Boolean lineWrap;
    private Boolean visible;
    private Boolean editable;
    private Boolean focusable;
    private Boolean doubleBuffered;

    private Object initialValue;
    private Map<String, String> styles;
    private Map<String, String> scripts;
    private Map<String, String> styleOverrides;
    private Map<String, Object> properties;
    private List<String> styleClasses;
    private List<String> configGroups;
    private List<String> fileExtensions;
    private LayoutConfig layoutConfig;
    private Gradient gradient;
    private Bounds bounds;

    private String keyCode;
    private String tooltipStyle;
    private String border;
    private String color;
    private String localeKey;
    private String imageIcon;
    private String readFrom;
    private String loadPanel;
    private String type;
    private String style;
    private String id;
    private String background;
    private String thumbImage;
    private String trackImage;
    private String alignment;
    private String toolTip;
    private String showIcon;
    private String hideIcon;
    private String iconFloat;
    private String selectionMode;
    private String script;
    private String accessibleName;
    private String accessibleDescription;
    private String cursor;

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

    public static Builder builder(String componentType) {
        return new Builder(componentType);
    }

    public void validateForCreation() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Component type must not be blank");
        }
        Rectangle rectangle = getBounds();
        if (rectangle.width < 0 || rectangle.height < 0) {
            throw new IllegalArgumentException(
                    "Component bounds must be non-negative for '" + valueOr(id, type) + "': " + rectangle
            );
        }
    }

    public List<String> getStyleChain() {
        LinkedHashSet<String> chain = new LinkedHashSet<>(StyleProvider.splitStyleChain(style));
        if (styleClasses != null) {
            for (String styleClass : styleClasses) {
                if (styleClass != null && !styleClass.isBlank()) {
                    chain.addAll(StyleProvider.splitStyleChain(styleClass));
                }
            }
        }
        return List.copyOf(chain);
    }

    public Map<String, String> getStyleOverrides() {
        return immutableMap(styleOverrides);
    }

    public Map<String, Object> getProperties() {
        return properties == null || properties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(properties);
    }

    public List<String> getConfigGroups() {
        return configGroups == null || configGroups.isEmpty()
                ? List.of()
                : List.copyOf(configGroups);
    }

    public ComponentAttributes addConfigGroup(String group) {
        if (group != null && !group.isBlank()) {
            if (configGroups == null) {
                configGroups = new ArrayList<>();
            }
            String normalized = group.trim();
            if (configGroups.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
                configGroups.add(normalized);
            }
        }
        return this;
    }

    public ComponentAttributes setConfigGroups(List<String> groups) {
        configGroups = new ArrayList<>();
        if (groups != null) {
            groups.forEach(this::addConfigGroup);
        }
        return this;
    }

    public ComponentAttributes copy() {
        return COPY_GSON.fromJson(COPY_GSON.toJsonTree(this), ComponentAttributes.class);
    }

    public ComponentAttributes setComponentType(String componentType) {
        this.type = componentType;
        return this;
    }

    public ComponentAttributes setComponentId(String componentId) {
        this.id = componentId;
        return this;
    }

    public ComponentAttributes setComponentStyle(String componentStyle) {
        this.style = componentStyle;
        return this;
    }

    /**
     * Adds an instance-level style override for a child node of a constructed composite.
     *
     * <p>The target may be a local node id ({@code label}), an explicit node selector
     * ({@code node:label}), a component type selector ({@code type:label}), or {@code *}.</p>
     */
    public ComponentAttributes putTargetStyle(String target, String styleName) {
        if (target != null && !target.isBlank() && styleName != null && !styleName.isBlank()) {
            if (styles == null) {
                styles = new LinkedHashMap<>();
            }
            styles.put(target.trim(), styleName.trim());
        }
        return this;
    }

    public ComponentAttributes addStyleClass(String styleClass) {
        if (styleClass != null && !styleClass.isBlank()) {
            if (styleClasses == null) {
                styleClasses = new ArrayList<>();
            }
            styleClasses.add(styleClass.trim());
        }
        return this;
    }

    public ComponentAttributes putStyleOverride(String property, Object value) {
        if (property != null && !property.isBlank()) {
            if (styleOverrides == null) {
                styleOverrides = new LinkedHashMap<>();
            }
            styleOverrides.put(property.trim(), value == null ? null : String.valueOf(value));
        }
        return this;
    }

    public ComponentAttributes putProperty(String property, Object value) {
        if (property != null && !property.isBlank()) {
            if (properties == null) {
                properties = new LinkedHashMap<>();
            }
            properties.put(property.trim(), value);
        }
        return this;
    }

    public ComponentAttributes putScript(String eventName, String scriptPath) {
        if (eventName != null && !eventName.isBlank() && scriptPath != null && !scriptPath.isBlank()) {
            if (scripts == null) {
                scripts = new LinkedHashMap<>();
            }
            scripts.put(eventName.trim(), scriptPath.trim());
        }
        return this;
    }

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
    public boolean isEnabled() { return enabled == null || enabled; }
    public boolean hasEnabled() { return enabled != null; }
    public String getKeyCode() { return keyCode; }
    public Object getInitialValue() { return initialValue; }
    public String getColor() { return color; }
    public String getLocaleKey() { return localeKey; }
    public String getImageIcon() { return imageIcon; }
    public boolean isOpaque() { return Boolean.TRUE.equals(opaque); }
    public boolean hasOpaque() { return opaque != null; }
    public boolean isrevealButton() { return Boolean.TRUE.equals(revealButton); }
    public int getIconWidth() { return iconWidth; }
    public int getIconHeight() { return iconHeight; }
    public int getTotalFrames() { return totalFrames; }
    public int getDelay() { return delay; }
    public List<String> getFileExtensions() { return fileExtensions == null ? List.of() : List.copyOf(fileExtensions); }
    public String getShowIcon() { return showIcon; }
    public String getHideIcon() { return hideIcon; }

    public Bounds setBounds(int x, int y, int width, int height) {
        this.bounds = new Bounds(x, y, width, height);
        return this.bounds;
    }

    public Rectangle getBounds() {
        return bounds == null ? new Rectangle() : bounds.getBounds();
    }

    public String getLoadPanel() { return loadPanel; }
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }
    public int getMinorSpacing() { return minorSpacing; }
    public int getMajorSpacing() { return majorSpacing; }
    public int getSelectedIndex() { return selectedIndex; }
    public String getToolTip() { return toolTip; }
    public boolean isVisible() { return visible == null || visible; }
    public boolean hasVisible() { return visible != null; }
    public boolean isLineWrap() { return Boolean.TRUE.equals(lineWrap); }
    public boolean isRepeat() { return Boolean.TRUE.equals(repeat); }
    public boolean isEditable() { return editable == null || editable; }
    public boolean hasEditable() { return editable != null; }
    public boolean isFocusable() { return Boolean.TRUE.equals(focusable); }
    public boolean hasFocusable() { return focusable != null; }
    public boolean isDoubleBuffered() { return doubleBuffered == null || doubleBuffered; }
    public boolean hasDoubleBuffered() { return doubleBuffered != null; }
    public String getTooltipStyle() { return tooltipStyle; }
    public Map<String, String> getStyles() { return immutableMap(styles); }
    public int getStepSize() { return stepSize; }
    public String getSelectionMode() { return selectionMode; }
    public String getScript() { return script; }
    public Map<String, String> getScripts() { return immutableMap(scripts); }
    public Gradient getGradient() { return gradient; }
    public LayoutConfig getLayoutConfig() { return layoutConfig; }
    public String getAccessibleName() { return accessibleName; }
    public String getAccessibleDescription() { return accessibleDescription; }
    public String getCursor() { return cursor; }

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

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return source == null || source.isEmpty() ? Map.of() : Collections.unmodifiableMap(source);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public String toString() {
        return "ComponentAttributes{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", styles=" + getStyleChain() +
                ", configGroups=" + getConfigGroups() +
                ", bounds=" + getBounds() +
                '}';
    }

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
            return switch (componentType.trim().toLowerCase()) {
                case "label" -> label;
                case "slider" -> slider;
                case "spinner" -> spinner;
                case "textfield" -> textField;
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
        private int zIndex;

        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getZIndex() { return zIndex; }
    }

    public static class Gradient {
        private String startColor;
        private String endColor;
        private boolean vertical;

        public String getStartColor() { return startColor; }
        public String getEndColor() { return endColor; }
        public boolean isVertical() { return vertical; }
    }

    public static final class Builder {
        private final ComponentAttributes attributes;

        private Builder(String componentType) {
            attributes = new ComponentAttributes();
            attributes.type = Objects.requireNonNull(componentType, "componentType").trim();
        }

        public Builder id(String id) {
            attributes.id = id;
            return this;
        }

        public Builder style(String... styles) {
            attributes.style = styles == null ? null : String.join(" ", styles);
            return this;
        }

        public Builder styleClass(String styleClass) {
            attributes.addStyleClass(styleClass);
            return this;
        }

        public Builder groups(String... groups) {
            if (groups != null) {
                for (String group : groups) {
                    attributes.addConfigGroup(group);
                }
            }
            return this;
        }

        public Builder targetStyle(String target, String styleName) {
            attributes.putTargetStyle(target, styleName);
            return this;
        }

        public Builder styleOverride(String property, Object value) {
            attributes.putStyleOverride(property, value);
            return this;
        }

        public Builder property(String property, Object value) {
            attributes.putProperty(property, value);
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            attributes.setBounds(x, y, width, height);
            return this;
        }

        public Builder localeKey(String localeKey) {
            attributes.localeKey = localeKey;
            return this;
        }

        public Builder initialValue(Object value) {
            attributes.initialValue = value;
            return this;
        }

        public Builder enabled(boolean enabled) {
            attributes.enabled = enabled;
            return this;
        }

        public Builder visible(boolean visible) {
            attributes.visible = visible;
            return this;
        }

        public Builder opaque(boolean opaque) {
            attributes.opaque = opaque;
            return this;
        }

        public Builder editable(boolean editable) {
            attributes.editable = editable;
            return this;
        }

        public Builder focusable(boolean focusable) {
            attributes.focusable = focusable;
            return this;
        }

        public Builder accessible(String name, String description) {
            attributes.accessibleName = name;
            attributes.accessibleDescription = description;
            return this;
        }

        public Builder tooltip(String localeKey, String style) {
            attributes.toolTip = localeKey;
            attributes.tooltipStyle = style;
            return this;
        }

        public Builder script(String script) {
            attributes.script = script;
            return this;
        }

        public Builder script(String eventName, String scriptPath) {
            attributes.putScript(eventName, scriptPath);
            return this;
        }

        public ComponentAttributes build() {
            attributes.validateForCreation();
            return attributes;
        }
    }
}
