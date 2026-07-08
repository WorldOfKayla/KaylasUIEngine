package org.takesome.kaylasEngine.gui.components.checkbox;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

public class CheckboxStyle {
    private int cols = 4, rows = 1;
    public boolean visible = true;
    public int width, height;
    public String fontName, color;
    public float fontSize;
    public BufferedImage texture;
    private final ComponentFactory componentFactory;

    public CheckboxStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;

        this.width = componentFactory.getStyle().getWidth();
        this.height = componentFactory.getStyle().getHeight();
        this.fontName = componentFactory.getStyle().getFont();
        this.fontSize = componentFactory.getStyle().getFontSize();
        this.color = componentFactory.getStyle().getColor();
        this.texture = this.componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture());

        //validateTextureSize(this.texture);
    }

    private void validateTextureSize(BufferedImage texture) {
        if (texture.getWidth() < width || texture.getHeight() < height) {
            throw new IllegalArgumentException(
                    "The texture size must be at least equal to the checkbox size: " +
                            "width=" + width + ", height=" + height + ". " +
                            "Current texture size: " + texture.getWidth() + "x" + texture.getHeight()
            );
        }
    }


    public void apply(Checkbox checkbox) {
        ImageUtils imageUtils = this.componentFactory.getEngine().getImageUtils();
        int iconWidth = componentFactory.getStyle().getIconWidth(), iconHeight = componentFactory.getStyle().getIconHeight();
        checkbox.setVisible(visible);
        checkbox.setForeground(hexToColor(color));
        checkbox.setFont(componentFactory.getEngine().getFONTUTILS().getFont(this.fontName, this.fontSize));

        int stateWidth = texture.getWidth() / cols;
        int stateHeight = texture.getHeight() / rows;

        validateGridSize(texture, cols, rows);

        // Extract textures for each checkbox state
        checkbox.defaultTX = (BufferedImage) imageUtils.getScaledImage(texture.getSubimage(0, 0, stateWidth, stateHeight), iconWidth, iconHeight);
        checkbox.rolloverTX = (BufferedImage) imageUtils.getScaledImage(texture.getSubimage(stateWidth, 0, stateWidth, stateHeight), iconWidth, iconHeight);
        checkbox.selectedTX = (BufferedImage) imageUtils.getScaledImage(texture.getSubimage(stateWidth * 2, 0, stateWidth, stateHeight), iconWidth, iconHeight);
        checkbox.selectedRolloverTX = (BufferedImage) imageUtils.getScaledImage(texture.getSubimage(stateWidth * 3, 0, stateWidth, stateHeight), iconWidth, iconHeight);

        // Set the icons for the checkbox
        checkbox.setIcon(new ImageIcon(checkbox.defaultTX));
        checkbox.setRolloverIcon(new ImageIcon(checkbox.rolloverTX));
        checkbox.setSelectedIcon(new ImageIcon(checkbox.selectedTX));
        checkbox.setRolloverSelectedIcon(new ImageIcon(checkbox.selectedRolloverTX));
    }

    private void validateGridSize(BufferedImage texture, int columns, int rows) {
        if (texture.getWidth() % columns != 0 || texture.getHeight() % rows != 0) {
           // throw new IllegalArgumentException("Texture dimensions are not divisible by the grid size.");
        }
    }


    public BufferedImage getTexture(int startX, int startY, int subWidth, int subHeight) {
        return texture.getSubimage(startX, startY, subWidth, subHeight);
    }
}
