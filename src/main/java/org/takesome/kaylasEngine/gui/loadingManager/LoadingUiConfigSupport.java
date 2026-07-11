package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.animation.AnimationCurve;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared coercion helpers used while interpreting loading UI Lua configuration. */
final class LoadingUiConfigSupport {
    private LoadingUiConfigSupport() {
    }

    static AnimationCurve curve(Map<String, Object> table,
                                String key,
                                AnimationCurve fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof String text) {
            return AnimationCurve.named(text);
        }
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> easing = (Map<String, Object>) rawMap;
            String type = LuaConfigValues.string(
                    easing,
                    "type",
                    LuaConfigValues.string(easing, "name", fallback.name())
            );
            String normalized = type.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("-", "")
                    .replace("_", "");
            if (normalized.equals("cubicbezier") || normalized.equals("bezier")) {
                return AnimationCurve.cubicBezier(
                        number(easing, "x1", 0.25),
                        number(easing, "y1", 0.1),
                        number(easing, "x2", 0.25),
                        number(easing, "y2", 1.0)
                );
            }
            return AnimationCurve.named(type);
        }
        return fallback;
    }

    static List<String> stringList(Map<String, Object> table,
                                   String key,
                                   List<String> fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? fallback : List.of(trimmed);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return fallback;
        }

        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(entry -> numericKey(entry.getKey())));
        List<String> values = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries) {
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                continue;
            }
            String stringValue = String.valueOf(rawValue).trim();
            if (!stringValue.isEmpty()) {
                values.add(stringValue);
            }
        }
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(entry -> numericKey(entry.getKey())));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries) {
            if (entry.getValue() instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) rawMap;
                result.add(typedMap);
            }
        }
        return List.copyOf(result);
    }

    static double number(Map<String, Object> table, String key, double fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalizeColorString(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int numericKey(Object key) {
        if (key instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(key));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
