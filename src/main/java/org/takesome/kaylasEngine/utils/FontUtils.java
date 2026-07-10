package org.takesome.kaylasEngine.utils;

import org.takesome.kaylasEngine.Engine;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontUtils {
    public static final String FONT_AWESOME_SOLID = "fa-solid";

    private static final String FONT_BASE_PATH = "/assets/fonts/";
    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final Font FALLBACK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, (int) DEFAULT_FONT_SIZE);
    private static final Map<String, Font> FONT_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> FONT_ALIASES = Map.ofEntries(
            Map.entry("primary", List.of("Primary", "FSElliotPro")),
            Map.entry(FONT_AWESOME_SOLID, List.of(
                    "fa-solid-900",
                    "Font Awesome 6 Free-Solid-900",
                    "FontAwesome6Free-Solid",
                    "FontAwesome6FreeSolid",
                    "FontAwesome",
                    "Font Awesome 6 Free"
            ))
    );

    private final Engine engine;

    public FontUtils(Engine engine) {
        this.engine = engine;
    }

    public Font getFont(String name, float size) {
        return getFont(name, size, Font.PLAIN);
    }

    public Font getFont(String name, float size, String style) {
        return getFont(name, size, parseStyle(style));
    }

    public Font getFont(String name, float size, int style) {
        float effectiveSize = effectiveSize(size);
        int effectiveStyle = normalizeStyle(style);
        if (name == null || name.isBlank()) {
            return FALLBACK_FONT.deriveFont(effectiveStyle, effectiveSize);
        }
        String cacheKey = canonicalFontKey(name);
        Font baseFont = FONT_CACHE.computeIfAbsent(cacheKey, this::loadFont);
        return baseFont.deriveFont(effectiveStyle, effectiveSize);
    }

    public static int parseStyle(String style) {
        if (style == null || style.isBlank()) {
            return Font.PLAIN;
        }
        String normalized = style.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        return switch (normalized) {
            case "bold", "700", "800", "900" -> Font.BOLD;
            case "italic", "oblique" -> Font.ITALIC;
            case "bolditalic", "italicbold", "boldoblique", "obliquebold" -> Font.BOLD | Font.ITALIC;
            case "plain", "normal", "regular", "400" -> Font.PLAIN;
            default -> parseNumericStyle(normalized);
        };
    }

    public static String styleName(int style) {
        int normalized = normalizeStyle(style);
        if (normalized == (Font.BOLD | Font.ITALIC)) {
            return "boldItalic";
        }
        if (normalized == Font.BOLD) {
            return "bold";
        }
        if (normalized == Font.ITALIC) {
            return "italic";
        }
        return "plain";
    }

    private static int parseNumericStyle(String style) {
        try {
            return normalizeStyle(Integer.parseInt(style));
        } catch (NumberFormatException ignored) {
            return Font.PLAIN;
        }
    }

    private static int normalizeStyle(int style) {
        return style & (Font.BOLD | Font.ITALIC);
    }

    private Font loadFont(String cacheKey) {
        for (String candidate : fontCandidates(cacheKey)) {
            Font loaded = tryLoadFont(candidate, "ttf");
            if (loaded == null) {
                loaded = tryLoadFont(candidate, "otf");
            }
            if (loaded != null) {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                Engine.getLOGGER().info("Created font - {}", candidate);
                return loaded;
            }
        }

        Engine.getLOGGER().warn("Font '{}' was not found; using fallback font.", cacheKey);
        return FALLBACK_FONT;
    }

    private List<String> fontCandidates(String cacheKey) {
        List<String> aliases = FONT_ALIASES.get(cacheKey);
        if (aliases != null) {
            return aliases;
        }
        return List.of(cacheKey);
    }

    private String canonicalFontKey(String name) {
        String trimmed = name == null ? "" : name.trim();
        String compact = trimmed
                .toLowerCase(Locale.ROOT)
                .replace("_", "-")
                .replace(" ", "")
                .replace(".", "");

        if (compact.equals("primary")) {
            return "primary";
        }
        if (compact.equals("fa")
                || compact.equals("fas")
                || compact.equals("fa-solid")
                || compact.equals("fa-solid-900")
                || compact.startsWith("fa-solid-")
                || compact.equals("font-awesome")
                || compact.equals("font-awesome-solid")
                || compact.contains("fontawesome")) {
            return FONT_AWESOME_SOLID;
        }
        return trimmed;
    }

    private float effectiveSize(float size) {
        return size > 0 ? size : DEFAULT_FONT_SIZE;
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
