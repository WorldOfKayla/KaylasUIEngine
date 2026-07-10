package org.takesome.kaylasEngine.utils;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class FontAwesomeIconRenderer {
    private static final Color FALLBACK_COLOR = Color.WHITE;
    private static final float INITIAL_FONT_SCALE = 0.82f;

    private FontAwesomeIconRenderer() {
    }

    public static ImageIcon render(String glyph, Font baseFont, Color color, int width, int height) {
        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Font iconFont = fitFont(graphics, baseFont, glyph, targetWidth, targetHeight);
            graphics.setFont(iconFont);
            graphics.setColor(color == null ? FALLBACK_COLOR : color);

            FontMetrics metrics = graphics.getFontMetrics();
            int glyphWidth = metrics.stringWidth(glyph);
            int glyphHeight = metrics.getAscent() + metrics.getDescent();
            int x = Math.max(0, (targetWidth - glyphWidth) / 2);
            int y = Math.max(metrics.getAscent(), (targetHeight - glyphHeight) / 2 + metrics.getAscent());
            graphics.drawString(glyph, x, y);
        } finally {
            graphics.dispose();
        }

        return new ImageIcon(image);
    }

    private static Font fitFont(Graphics2D graphics, Font baseFont, String glyph, int width, int height) {
        Font sourceFont = baseFont == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : baseFont;
        float fontSize = Math.max(1f, Math.min(width, height) * INITIAL_FONT_SCALE);
        Font iconFont = sourceFont.deriveFont(Font.PLAIN, fontSize);
        graphics.setFont(iconFont);

        FontMetrics metrics = graphics.getFontMetrics();
        while (fontSize > 1f && (metrics.stringWidth(glyph) > width || metrics.getHeight() > height)) {
            fontSize -= 1f;
            iconFont = sourceFont.deriveFont(Font.PLAIN, fontSize);
            graphics.setFont(iconFont);
            metrics = graphics.getFontMetrics();
        }
        return iconFont;
    }
}
