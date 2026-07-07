package org.foxesworld.engine.gui.components;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ComponentAttributes extends Attributes {

    public ComponentAttributes() {
        this.childComponents = new ArrayList<>();
    }
    private int rowNum, colNum, imgCount, fontSize, selectedIndex =0;
    private boolean enabled, opaque, revealButton, repeat, lineWrap, visible, editable;
    private  Object initialValue;
    private Map<String, String> styles;
    private List<String> fileExtensions;
    private LayoutConfig layoutConfig;
    private Gradient gradient;
    private String keyCode, tooltipStyle, border, color, localeKey, imageIcon, readFrom, loadPanel, type, style, id,background, thumbImage, trackImage, alignment, toolTip, showIcon, hideIcon,  iconFloat, selectionMode;
    private int iconWidth, iconHeight, totalFrames, delay, minValue, minorSpacing, majorSpacing, maxValue, borderRadius, stepSize;
    private Bounds bounds;
    public void setInitialValue(Object initialValue) {
        this.initialValue = initialValue;
    }
    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }
    public String getReadFrom() {
        return readFrom;
    }
    public String getBackground() {
        return background;
    }
    public String getComponentType() {
        return type;
    }

    public String getIconFloat() {
        return iconFloat;
    }

    public String getComponentStyle() {
        return style;
    }
    public String getComponentId() {
        return id;
    }
    public int getRowNum() {
        return rowNum;
    }
    public int getColNum() {
        return colNum;
    }
    public int getBorderRadius() {
        return borderRadius;
    }
    public String getAlignment() {
        return alignment;
    }
    public String getBorder() {
        return border;
    }
    public int getImgCount() {
        return imgCount;
    }
    public int getFontSize() {
        return fontSize;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public String getKeyCode() {
        return keyCode;
    }
    public Object getInitialValue() {
        return initialValue;
    }
    public String getColor() {
        return color;
    }
    public String getLocaleKey() {
        return localeKey;
    }
    public String getImageIcon() {
        return imageIcon;
    }
    public boolean isOpaque() {
        return opaque;
    }
    public boolean isrevealButton() {
        return revealButton;
    }
    public int getIconWidth() {
        return iconWidth;
    }
    public int getIconHeight() {
        return iconHeight;
    }
    public int getTotalFrames() {
        return totalFrames;
    }
    public int getDelay() {
        return delay;
    }
    public List<String> getFileExtensions() {
        return fileExtensions;
    }
    public String getShowIcon() {
        return showIcon;
    }
    public String getHideIcon() {
        return hideIcon;
    }

    public Bounds setBounds(int x, int y, int width, int height){
        return new Bounds(x, y, width, height);
    }
    public Rectangle getBounds() {
        if (bounds == null) {
            return new Rectangle(0, 0, 0, 0);
        }
        return bounds.getBounds();
    }
    public String getLoadPanel() {
        return loadPanel;
    }
    public int getMinValue() {
        return minValue;
    }
    public int getMaxValue() {
        return maxValue;
    }
    public int getMinorSpacing() {
        return minorSpacing;
    }
    public int getMajorSpacing() {
        return majorSpacing;
    }
    public int getSelectedIndex() {
        return selectedIndex;
    }
    public String getToolTip() {
        return toolTip;
    }
    public boolean isVisible() {
        return visible;
    }
    public boolean isLineWrap() {
        return lineWrap;
    }
    public boolean isRepeat() {
        return repeat;
    }

    public boolean isEditable() {
        return editable;
    }

    public String getTooltipStyle() {
        return tooltipStyle;
    }
    public Map<String, String> getStyles() {
        return styles;
    }
    public int getStepSize() {
        return stepSize;
    }
    public String getSelectionMode() {
        return selectionMode;
    }

    public Gradient getGradient() {
        return gradient;
    }

    public LayoutConfig getLayoutConfig() {
        return layoutConfig;
    }

    public static class LayoutConfig {
        private ComponentConfig label;
        private ComponentConfig slider;
        private ComponentConfig spinner;
        private ComponentConfig textField;
        private ComponentConfig button;

        public ComponentConfig getLabel() {
            return label;
        }

        public ComponentConfig getSlider() {
            return slider;
        }

        public ComponentConfig getSpinner() {
            return spinner;
        }

        public ComponentConfig getTextField() {
            return textField;
        }

        public ComponentConfig getButton() {
            return button;
        }
    }

    public static class ComponentConfig {
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex = 0;

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getZIndex() {
            return zIndex;
        }
    }

    public static  class Gradient {
        private String startColor, endColor;
        private boolean vertical;

        public String getStartColor() {
            return startColor;
        }

        public String getEndColor() {
            return endColor;
        }

        public boolean isVertical() {
            return vertical;
        }
    }

}