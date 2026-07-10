package org.takesome.kaylasEngine.gui.components.label;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;

import java.awt.Color;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

public class LabelStyle {
    private StyleAttributes style;
    private ComponentFactory componentFactory;
    private String fontName;
    private float fontSize;
    private Color idleColor;
    private Color activeColor;
    private Color startColor;
    private Color endColor;
    private boolean isGradient;

    public LabelStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        style = componentFactory.getStyle();
        if (componentFactory.getComponentAttribute().getGradient() != null) {
            this.startColor = hexToColor(componentFactory.getComponentAttribute().getGradient().getStartColor());
            this.endColor = hexToColor(componentFactory.getComponentAttribute().getGradient().getEndColor());
            this.isGradient = true;
        } else {
            this.idleColor = hexToColor(style.getColor());
            this.activeColor = idleColor;
            this.isGradient = false;
        }
    }

    public LabelStyle(StyleAttributes styleAttributes) {
        style = styleAttributes;
        this.idleColor = hexToColor(style.getColor());
        this.activeColor = idleColor;
        this.isGradient = false;
    }

    public void apply(Label label) {
        this.fontName = style.getFont();
        this.fontSize = style.getFontSize();
        if (componentFactory != null) {
            label.setFont(componentFactory.getEngine().getFONTUTILS().getFont(
                    fontName,
                    fontSize,
                    style.getFontStyle()
            ));
        }
        label.setForeground(idleColor);
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public Color getIdleColor() {
        return idleColor;
    }

    public void setIdleColor(Color idleColor) {
        this.idleColor = idleColor;
    }

    public Color getActiveColor() {
        return activeColor;
    }

    public void setActiveColor(Color activeColor) {
        this.activeColor = activeColor;
    }

    public void setStyle(StyleAttributes style) {
        this.style = style;
    }
}
