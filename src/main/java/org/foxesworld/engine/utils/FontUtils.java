package org.foxesworld.engine.utils;

import org.foxesworld.engine.Engine;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontUtils {
    private static final String FONT_BASE_PATH = "/assets/fonts/";
    private static final Font FALLBACK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Map<String, Font> FONT_CACHE = new ConcurrentHashMap<>();

    private final Engine engine;

    public FontUtils(Engine engine) {
        this.engine = engine;
    }

    public Font getFont(String name, float size) {
        if (name == null || name.isBlank()) {
            return FALLBACK_FONT.deriveFont(size);
        }
        Font baseFont = FONT_CACHE.computeIfAbsent(name, this::loadFont);
        return baseFont.deriveFont(size > 0 ? size : 12f);
    }

    private Font loadFont(String name) {
        Font loaded = tryLoadFont(name, "ttf");
        if (loaded == null) {
            loaded = tryLoadFont(name, "otf");
        }
        if (loaded == null) {
            Engine.getLOGGER().warn("Font '{}' was not found; using fallback font.", name);
            return FALLBACK_FONT;
        }
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
        Engine.getLOGGER().info("Created font - {}", name);
        return loaded;
    }

    private Font tryLoadFont(String name, String extension) {
        String resourcePath = FONT_BASE_PATH + name + "." + extension;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception ex) {
            Engine.getLOGGER().debug("Could not load font resource {}", resourcePath, ex);
            return null;
        }
    }

    public static Color hexToColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return Color.WHITE;
        }

        String normalized = hex.trim();
        if (!normalized.matches("^#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            return Color.WHITE;
        }
        normalized = normalized.substring(1);
        if (normalized.length() == 3) {
            normalized = "" + normalized.charAt(0) + normalized.charAt(0)
                    + normalized.charAt(1) + normalized.charAt(1)
                    + normalized.charAt(2) + normalized.charAt(2);
        }

        try {
            int red = Integer.parseInt(normalized.substring(0, 2), 16);
            int green = Integer.parseInt(normalized.substring(2, 4), 16);
            int blue = Integer.parseInt(normalized.substring(4, 6), 16);
            int alpha = normalized.length() == 8 ? Integer.parseInt(normalized.substring(6, 8), 16) : 255;
            return new Color(red, green, blue, alpha);
        } catch (NumberFormatException ex) {
            return Color.WHITE;
        }
    }
}
