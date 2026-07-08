package org.takesome.kaylasEngine.gui.components.passfield;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;


public class PassFieldStyle {
    private ComponentFactory componentFactory;
    public String fontName = "";
    public String echoChar = "";
    public float fontSize = 1.0f;

    public BufferedImage texture;
    public Color textColor;
    public Color caretColor;
    public Border border;

    public PassFieldStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        this.texture = this.componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture());
        if(componentFactory.getStyle().getBorderRadius() != 0) {
            this.texture = this.componentFactory.getEngine().getImageUtils().getRoundedImage(this.texture, componentFactory.getStyle().getBorderRadius());
        }
        this.textColor = hexToColor(componentFactory.getStyle().getColor());
        this.caretColor = hexToColor(componentFactory.getStyle().getCaretColor());
        this.echoChar = "*";
    }

    public void apply(PassField pass) {
        pass.texture = this.texture;
        pass.setPaddingX(this.componentFactory.getStyle().getPaddingX());
        pass.setPaddingY(this.componentFactory.getStyle().getPaddingY());
        pass.setCaretColor(this.caretColor);
        pass.setBackground(this.textColor);
        pass.setForeground(this.textColor);
        pass.setBorder(this.border);
    }
}
