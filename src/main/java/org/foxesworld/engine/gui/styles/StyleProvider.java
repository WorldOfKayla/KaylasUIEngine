package org.foxesworld.engine.gui.styles;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.foxesworld.engine.Engine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class StyleProvider {
    private static final String DEFAULT_STYLE_PATH = "assets/styles/";
    private static final String DEFAULT_STYLE_NAME = "default";

    private final Map<String, Map<String, StyleAttributes>> elementStyles = new ConcurrentHashMap<>();
    private final String stylePath;
    private final Gson gson;

    public StyleProvider(String[] styles) {
        this(styles, DEFAULT_STYLE_PATH);
    }

    public StyleProvider(String[] styles, String stylePath) {
        this.stylePath = normalizeStylePath(stylePath);
        this.gson = new Gson();
        Engine.LOGGER.info("Initializing StyleProvider with path: {}", this.stylePath);
        loadStyles(styles);
    }

    private String normalizeStylePath(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_STYLE_PATH;
        }
        return path.endsWith("/") ? path : path + "/";
    }

    private void loadStyles(String[] styles) {
        if (styles == null) {
            return;
        }
        for (String style : styles) {
            if (style == null || style.isBlank()) {
                continue;
            }
            loadStyleIfAbsent(style);
        }
    }

    private Map<String, StyleAttributes> loadStyleIfAbsent(String component) {
        return elementStyles.computeIfAbsent(component, key -> {
            try {
                return loadStyle(key);
            } catch (StyleLoadingException e) {
                if (e.isMissingResource()) {
                    Engine.getLOGGER().warn("Style '{}' is not defined; using default fallback style.", key);
                } else {
                    Engine.getLOGGER().error("Failed to load style '{}': {}", key, e.getMessage(), e);
                }
                return Map.of(DEFAULT_STYLE_NAME, StyleAttributes.defaults(key));
            }
        });
    }

    private Map<String, StyleAttributes> loadStyle(String component) throws StyleLoadingException {
        String fullPath = stylePath + component + ".json";
        JsonObject jsonRoot = loadJson(fullPath);

        JsonObject stylesObject = jsonRoot.getAsJsonObject("styles");
        if (stylesObject == null) {
            throw new StyleLoadingException("Missing 'styles' section in " + fullPath);
        }

        JsonObject componentStyles = stylesObject.getAsJsonObject(component);
        if (componentStyles == null) {
            componentStyles = findComponentStylesIgnoreCase(stylesObject, component);
        }
        if (componentStyles == null) {
            throw new StyleLoadingException("Missing component styles for " + component + " in " + fullPath);
        }

        Map<String, StyleAttributes> styleMap = parseComponentStyles(componentStyles);
        if (styleMap.isEmpty()) {
            styleMap.put(DEFAULT_STYLE_NAME, StyleAttributes.defaults(component));
        }
        return Collections.unmodifiableMap(styleMap);
    }

    private JsonObject findComponentStylesIgnoreCase(JsonObject stylesObject, String component) {
        for (Map.Entry<String, JsonElement> entry : stylesObject.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(component) && entry.getValue().isJsonObject()) {
                return entry.getValue().getAsJsonObject();
            }
        }
        return null;
    }

    private JsonObject loadJson(String path) throws StyleLoadingException {
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new StyleLoadingException("Style file not found: " + path, true);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                if (json == null) {
                    throw new StyleLoadingException("Style file is empty: " + path);
                }
                return json;
            }
        } catch (JsonSyntaxException e) {
            throw new StyleLoadingException("Invalid JSON format in " + path, e);
        } catch (StyleLoadingException e) {
            throw e;
        } catch (Exception e) {
            throw new StyleLoadingException("Error reading JSON file: " + path, e);
        }
    }

    private Map<String, StyleAttributes> parseComponentStyles(JsonObject componentStyles) {
        Map<String, StyleAttributes> styleMap = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : componentStyles.entrySet()) {
            String styleName = entry.getKey();
            JsonElement styleData = entry.getValue();

            if (styleData.isJsonObject()) {
                StyleAttributes parsed = gson.fromJson(styleData, StyleAttributes.class);
                styleMap.put(styleName, parsed == null ? StyleAttributes.defaults(styleName) : parsed.normalized(styleName));
            } else if (styleData.isJsonArray()) {
                parseStyleArray(styleName, styleData.getAsJsonArray(), styleMap);
            } else {
                Engine.getLOGGER().warn("Unexpected JSON type for style: {}", styleName);
            }
        }
        return styleMap;
    }

    private void parseStyleArray(String styleName, JsonArray styleArray, Map<String, StyleAttributes> styleMap) {
        int index = 0;
        for (JsonElement element : styleArray) {
            if (element.isJsonObject()) {
                String indexedName = styleName + "_" + index;
                StyleAttributes parsed = gson.fromJson(element, StyleAttributes.class);
                styleMap.put(indexedName, parsed == null ? StyleAttributes.defaults(indexedName) : parsed.normalized(indexedName));
                index++;
            } else {
                Engine.getLOGGER().warn("Non-object element in array for style: {}", styleName);
            }
        }
    }

    public StyleAttributes getStyle(String componentType, String styleName) {
        if (componentType == null || componentType.isBlank()) {
            return StyleAttributes.defaults(DEFAULT_STYLE_NAME);
        }
        Map<String, StyleAttributes> styles = loadStyleIfAbsent(componentType);
        if (styleName != null && styles.containsKey(styleName)) {
            return styles.get(styleName);
        }
        if (styles.containsKey(DEFAULT_STYLE_NAME)) {
            return styles.get(DEFAULT_STYLE_NAME);
        }
        return styles.values().stream().findFirst().orElseGet(() -> StyleAttributes.defaults(componentType));
    }

    public boolean hasStyle(String componentType, String styleName) {
        if (componentType == null || styleName == null) {
            return false;
        }
        return loadStyleIfAbsent(componentType).containsKey(styleName);
    }

    public void reload(String componentType) {
        if (componentType != null && !componentType.isBlank()) {
            elementStyles.remove(componentType);
            loadStyleIfAbsent(componentType);
        }
    }

    public Map<String, Map<String, StyleAttributes>> getElementStyles() {
        return Collections.unmodifiableMap(elementStyles);
    }
}
