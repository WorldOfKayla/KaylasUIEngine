package org.takesome.kaylasEngine.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class IconUtils {

    private static final int DEFAULT_ICON_SIZE = 16;

    private final Engine engine;

    public IconUtils(Engine engine) {
        this.engine = engine;
    }

    public ImageIcon getIcon(ComponentAttributes componentAttributes) {
        if (componentAttributes == null || componentAttributes.getImageIcon() == null) {
            return null;
        }

        String iconPath = componentAttributes.getImageIcon().trim();
        if (iconPath.isEmpty()) {
            return null;
        }

        Optional<FontAwesomeIconRegistry.FontAwesomeIconSpec> fontAwesomeIcon = FontAwesomeIconRegistry.resolve(iconPath);
        if (fontAwesomeIcon.isPresent()) {
            return getFontAwesomeIcon(fontAwesomeIcon.get(), componentAttributes);
        }

        if (iconPath.endsWith(".png") || iconPath.endsWith(".jpg") || iconPath.endsWith(".jpeg")) {
            ImageIcon icon = new ImageIcon(this.engine.getImageUtils().getScaledImage(
                    this.engine.getImageUtils().getLocalImage(iconPath),
                    effectiveWidth(componentAttributes),
                    effectiveHeight(componentAttributes)
            ));
            if (componentAttributes.getBorderRadius() != 0) {
                icon = new ImageIcon(this.engine.getImageUtils().getRoundedImage(icon.getImage(), componentAttributes.getBorderRadius()));
            }
            return icon;
        }

        if (iconPath.endsWith(".svg")) {
            return this.getVectorIcon(iconPath, effectiveWidth(componentAttributes), effectiveHeight(componentAttributes));
        }

        if (iconPath.endsWith(".gif")) {
            Engine.LOGGER.error("GIF is not supported yet!");
            return null;
        }

        Engine.LOGGER.warn("Unsupported icon reference: {}", iconPath);
        return null;
    }

    private ImageIcon getFontAwesomeIcon(FontAwesomeIconRegistry.FontAwesomeIconSpec iconSpec,
                                         ComponentAttributes componentAttributes) {
        if (!iconSpec.isKnown()) {
            Engine.LOGGER.warn(
                    "Unknown Font Awesome icon '{}'; fallback icon '{}' will be used.",
                    iconSpec.getRequestedName(),
                    iconSpec.getName()
            );
        }

        int width = effectiveWidth(componentAttributes);
        int height = effectiveHeight(componentAttributes);
        float fontSize = Math.max(1f, Math.min(width, height));
        Font font = engine.getFONTUTILS().getFont(FontUtils.FONT_AWESOME_SOLID, fontSize);
        Color color = FontUtils.hexToColor(componentAttributes.getColor());
        return FontAwesomeIconRenderer.render(iconSpec.getGlyph(), font, color, width, height);
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

    public BufferedImage getVectorImage(URL source, int width, int height) {
        Objects.requireNonNull(source, "source");
        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        return renderVectorIcon(new FlatSVGIcon(source).derive(targetWidth, targetHeight), targetWidth, targetHeight);
    }

    public CompletableFuture<BufferedImage> getVectorImageAsync(URL source, int width, int height) {
        return engine.getExecutorServiceProvider().supplyAsync(
                () -> getVectorImage(source, width, height),
                "render-svg-" + Math.max(1, width) + 'x' + Math.max(1, height)
        );
    }

    public ImageIcon getVectorIcon(String iconPath, float width, float height) {
        ImageIcon tmpIcon = new FlatSVGIcon(iconPath);
        float sourceWidth = Math.max(1f, tmpIcon.getIconWidth());
        float sourceHeight = Math.max(1f, tmpIcon.getIconHeight());
        float targetWidth = Math.max(1f, width);
        float targetHeight = Math.max(1f, height);
        float scale = Math.min(targetHeight / sourceHeight, targetWidth / sourceWidth);
        return new FlatSVGIcon(iconPath, scale);
    }

    private BufferedImage renderVectorIcon(ImageIcon icon, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int x = Math.max(0, (width - icon.getIconWidth()) / 2);
            int y = Math.max(0, (height - icon.getIconHeight()) / 2);
            icon.paintIcon(null, graphics, x, y);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private int effectiveWidth(ComponentAttributes componentAttributes) {
        int iconWidth = componentAttributes.getIconWidth();
        if (iconWidth > 0) {
            return iconWidth;
        }
        if (componentAttributes.getIconHeight() > 0) {
            return componentAttributes.getIconHeight();
        }
        return DEFAULT_ICON_SIZE;
    }

    private int effectiveHeight(ComponentAttributes componentAttributes) {
        int iconHeight = componentAttributes.getIconHeight();
        if (iconHeight > 0) {
            return iconHeight;
        }
        if (componentAttributes.getIconWidth() > 0) {
            return componentAttributes.getIconWidth();
        }
        return DEFAULT_ICON_SIZE;
    }
}
