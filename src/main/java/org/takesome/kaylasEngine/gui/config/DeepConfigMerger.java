package org.takesome.kaylasEngine.gui.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Deterministic deep merge for component configuration and extension fragments. */
public final class DeepConfigMerger {
    public static final String MERGE_KEY = "$merge";
    public static final String VALUE_KEY = "$value";

    private DeepConfigMerger() {
    }

    public static Map<String, Object> merge(Map<String, ?> base, Map<String, ?> overlay) {
        Map<String, Object> result = mutableMap(base);
        if (overlay == null) {
            return result;
        }
        overlay.forEach((key, value) -> result.put(key, mergeValue(result.get(key), value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Object mergeValue(Object base, Object overlay) {
        if (overlay instanceof Map<?, ?> rawOverlay) {
            Map<String, Object> overlayMap = stringMap(rawOverlay);
            if (overlayMap.containsKey(MERGE_KEY) && overlayMap.containsKey(VALUE_KEY)) {
                return mergeCollectionDirective(base, overlayMap);
            }
            Map<String, ?> baseMap = base instanceof Map<?, ?> rawBase
                    ? stringMap(rawBase)
                    : Map.of();
            return merge(baseMap, overlayMap);
        }
        if (overlay instanceof Collection<?> collection) {
            return mutableList(collection);
        }
        if (overlay != null && overlay.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(overlay);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(deepCopy(java.lang.reflect.Array.get(overlay, index)));
            }
            return values;
        }
        return deepCopy(overlay);
    }

    private static Object mergeCollectionDirective(Object base, Map<String, Object> directive) {
        ConfigMergeStrategy strategy = ConfigMergeStrategy.from(directive.get(MERGE_KEY));
        List<Object> current = listValue(base);
        List<Object> incoming = listValue(directive.get(VALUE_KEY));
        return switch (strategy) {
            case REPLACE -> incoming;
            case APPEND -> {
                List<Object> result = new ArrayList<>(current);
                result.addAll(incoming);
                yield result;
            }
            case PREPEND -> {
                List<Object> result = new ArrayList<>(incoming);
                result.addAll(current);
                yield result;
            }
            case UNIQUE_APPEND -> {
                LinkedHashSet<Object> unique = new LinkedHashSet<>(current);
                unique.addAll(incoming);
                yield new ArrayList<>(unique);
            }
        };
    }

    private static List<Object> listValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return mutableList(collection);
        }
        if (value == null) {
            return new ArrayList<>();
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(deepCopy(java.lang.reflect.Array.get(value, index)));
            }
            return result;
        }
        return new ArrayList<>(List.of(deepCopy(value)));
    }

    private static Map<String, Object> mutableMap(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> result.put(key, deepCopy(value)));
        }
        return result;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), deepCopy(value));
                }
            });
        }
        return result;
    }

    private static List<Object> mutableList(Collection<?> source) {
        List<Object> result = new ArrayList<>();
        if (source != null) {
            source.forEach(value -> result.add(deepCopy(value)));
        }
        return result;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return mutableList(collection);
        }
        if (value != null && value.getClass().isArray()) {
            return listValue(value);
        }
        return value;
    }
}
