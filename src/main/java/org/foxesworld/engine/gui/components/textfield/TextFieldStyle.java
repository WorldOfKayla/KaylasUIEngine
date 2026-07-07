package org.foxesworld.engine.gui.components.textfield;

import org.foxesworld.engine.gui.components.ComponentFactory;
import org.foxesworld.engine.gui.styles.StyleAttributes;

import javax.swing.border.BevelBorder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.foxesworld.engine.utils.FontUtils.hexToColor;

public class TextFieldStyle {
    public Color foregroundColor, backgroundColor, caretColor;
    private final List<Color> borderColor = new ArrayList<>();
    public int width, height, bevel;
    public String font;
    public float fontSize;
    public BufferedImage texture;
    private final ComponentFactory componentFactory;

    public TextFieldStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        this.foregroundColor = hexToColor(componentFactory.getStyle().getColor());
        this.backgroundColor = hexToColor(componentFactory.getStyle().getBackground());
        this.setBorder(componentFactory.getStyle());
        this.caretColor = hexToColor(componentFactory.getStyle().getCaretColor());
        this.width = componentFactory.getStyle().getWidth();
        this.height = componentFactory.getStyle().getHeight();
        this.font = componentFactory.getStyle().getFont();
        this.fontSize = componentFactory.getStyle().getFontSize();
        this.texture = this.componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture());
        if (componentFactory.getStyle().getBorderRadius() != 0) {
            this.texture = this.componentFactory.getEngine().getImageUtils().getRoundedImage(this.texture, componentFactory.getStyle().getBorderRadius());
        }
    }

    private void setBorder(StyleAttributes styleAttributes) {
        String borderSpec = styleAttributes.getBorderColor();
        if (borderSpec == null || borderSpec.isBlank()) {
            return;
        }

        String[] parts = borderSpec.split(",");
        if (parts.length == 1 && styleAttributes.isHexColor(parts[0].trim())) {
            this.bevel = BevelBorder.LOWERED;
            this.borderColor.add(hexToColor(parts[0].trim()));
            this.borderColor.add(hexToColor(parts[0].trim()));
            return;
        }

        if (parts.length >= 3 && styleAttributes.isHexColor(parts[1].trim()) && styleAttributes.isHexColor(parts[2].trim())) {
            try {
                this.bevel = Integer.parseInt(parts[0].trim());
                this.borderColor.add(hexToColor(parts[1].trim()));
                this.borderColor.add(hexToColor(parts[2].trim()));
            } catch (NumberFormatException ignored) {
                this.borderColor.clear();
            }
        }
    }

    public void apply(TextField text) {
        text.texture = texture;
        text.setPaddingX(componentFactory.getStyle().getPaddingX());
        text.setPaddingY(componentFactory.getStyle().getPaddingY());
        text.setCaretColor(caretColor);
        text.setBackground(backgroundColor);
        text.setForeground(foregroundColor);
        if (borderColor.size() >= 2) {
            text.setBorder(new BevelBorder(this.bevel, borderColor.get(0), borderColor.get(1)));
        } else {
            text.setBorder(null);
        }
        text.setFont(componentFactory.getEngine().getFONTUTILS().getFont(font, fontSize));
    }
}
