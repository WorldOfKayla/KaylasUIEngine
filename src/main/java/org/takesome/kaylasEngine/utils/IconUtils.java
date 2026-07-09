package org.takesome.kaylasEngine.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class IconUtils {

    private final Engine engine;

    public IconUtils(Engine engine) {
        this.engine = engine;
    }

    public ImageIcon getIcon(ComponentAttributes componentAttributes) {
        ImageIcon icon = null;
        if(componentAttributes.getImageIcon() != null) {
            String iconPath = componentAttributes.getImageIcon();
            if (iconPath.endsWith(".png") || iconPath.endsWith(".jpg")) {
                icon = new ImageIcon(this.engine.getImageUtils().getScaledImage(this.engine.getImageUtils().getLocalImage(componentAttributes.getImageIcon()), componentAttributes.getIconWidth(), componentAttributes.getIconHeight()));
                if (componentAttributes.getBorderRadius() != 0) {
                    icon = new ImageIcon(this.engine.getImageUtils().getRoundedImage(icon.getImage(), componentAttributes.getBorderRadius()));
                }
            } else if (iconPath.endsWith(".svg")) {
                icon = this.getVectorIcon(iconPath, componentAttributes.getIconWidth(), componentAttributes.getIconHeight());
            } else if (iconPath.endsWith(".gif")) {
                Engine.LOGGER.error("GIF is not supported yet!");
            }
        }

        return icon;
    }


    public BufferedImage getVectorImage(String iconPath, int width, int height) {
        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        ImageIcon icon = getVectorIcon(iconPath, targetWidth, targetHeight);
        BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int x = Math.max(0, (targetWidth - icon.getIconWidth()) / 2);
            int y = Math.max(0, (targetHeight - icon.getIconHeight()) / 2);
            icon.paintIcon(null, graphics, x, y);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public ImageIcon getVectorIcon(String iconPath, float width, float height) {
        ImageIcon icon;
        ImageIcon tmpIcon = new FlatSVGIcon(iconPath);
        float scale = Math.min(height / tmpIcon.getIconHeight(), width / tmpIcon.getIconHeight());
        icon = new FlatSVGIcon(iconPath, scale);
        return icon;
    }
}
