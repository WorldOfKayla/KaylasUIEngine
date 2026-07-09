package org.takesome.kaylasEngine.gui.components.combobox;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

public class ComboboxStyle {

    private final ComponentFactory componentFactory;
    public String fontName;
    public float fontSize;
    public Color color;
    public BufferedImage texture;

    public ComboboxStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        this.fontName = componentFactory.getStyle().getFont();
        this.fontSize = componentFactory.getStyle().getFontSize();
        this.color = hexToColor(componentFactory.getStyle().getColor());
        this.texture = componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture());
    }

    public void apply(Combobox combobox) {
        combobox.setForeground(this.color);
        combobox.setFont(componentFactory.getEngine().getFONTUTILS().getFont(this.fontName, this.fontSize));
        int comboboxH = this.texture.getHeight() / 7;
        int comboboxW = this.texture.getWidth();
        combobox.setColor(this.color);
        combobox.setHoverColor(hexToColor(componentFactory.getStyle().getHoverColor()));
        combobox.setDefaultTX(getTexture(0, 0, comboboxW, comboboxH));
        combobox.setRolloverTX(getTexture(0, comboboxH, comboboxW, comboboxH));
        combobox.setOpenedTX(getTexture(0, comboboxH * 2, comboboxW, comboboxH));
        combobox.setPanelTX(getTexture(0, comboboxH * 3, comboboxW - 45, comboboxH));
        combobox.setSelectedTX(getTexture(0, comboboxH * 4, comboboxW - 45, comboboxH));
        combobox.setPoint(this.componentFactory.getEngine().getImageUtils().getLocalImage("assets/ui/icons/point.png"));
    }

    private BufferedImage getTexture(int x, int y, int width, int height) {
        BufferedImage clippedTexture = this.texture.getSubimage(x, y, width, height);
        if (componentFactory.getStyle().getBorderRadius() != 0) {
            clippedTexture = this.componentFactory.getEngine().getImageUtils().getRoundedImage(
                    clippedTexture,
                    componentFactory.getStyle().getBorderRadius()
            );
        }
        return clippedTexture;
    }
}
