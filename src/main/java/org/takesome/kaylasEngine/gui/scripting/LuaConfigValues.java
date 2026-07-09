package org.takesome.kaylasEngine.gui.scripting;

import org.takesome.kaylasEngine.Engine;

import java.awt.Color;
import java.util.Map;

/** Utility readers for Java maps returned by {@link LuaConfigScript}. */
public final class LuaConfigValues {
    private LuaConfigValues() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> root, String key) {
        Object value = root == null ? null : root.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public static String string(Map<String, Object> table, String key, String fallback) {
        Object value = table == null ? null : table.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static int integer(Map<String, Object> table, String key, int fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> table, String key, boolean fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    public static int alpha(Map<String, Object> table, int fallback) {
        if (table == null) {
            return fallback;
        }
        Object alpha = table.get("alpha");
        if (alpha instanceof Number number) {
            return clamp(number.intValue(), 0, 255);
        }
        Object opacity = table.get("opacity");
        if (opacity instanceof Number number) {
            return clamp((int) Math.round(number.doubleValue() * 255.0), 0, 255);
        }
        if (opacity instanceof String text) {
            try {
                return clamp((int) Math.round(Double.parseDouble(text.trim()) * 255.0), 0, 255);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static Color color(Map<String, Object> table, String key, Color fallback) {
        Object value = table == null ? null : table.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            String raw = String.valueOf(value).trim();
            return Color.decode(raw.startsWith("#") ? raw : "#" + raw);
        } catch (Exception error) {
            Engine.getLOGGER().warn("Invalid Lua config color '{}'.", value);
            return fallback;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
