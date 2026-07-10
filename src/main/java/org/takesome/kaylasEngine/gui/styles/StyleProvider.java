package org.takesome.kaylasEngine.gui.styles;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.takesome.kaylasEngine.Engine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, inherits, composes and caches component styles.
 *
 * <p>Style declarations may use {@code extends}, {@code inherits} or {@code parent}. A declaration
 * may inherit from one style or from an ordered array of styles. Component descriptors can compose
 * multiple resolved styles and finally apply inline overrides.</p>
 */
@SuppressWarnings("unused")
public class StyleProvider {
    private static final String DEFAULT_STYLE_PATH = "assets/styles/";
    private static final String DEFAULT_STYLE_NAME = "default";
    private static final Set<String> INHERITANCE_KEYS = Set.of("extends", "inherits", "parent");

    private final Map<String, StyleSheet> styleSheets = new ConcurrentHashMap<>();
    private final Map<String, String> componentResourceNames = new ConcurrentHashMap<>();
    private final Map<String, Map<String, StyleAttributes>> elementStyles = new ConcurrentHashMap<>();
    private final Map<StyleCacheKey, StyleAttributes> composedStyleCache = new ConcurrentHashMap<>();
    private final Set<String> missingStyleWarnings = ConcurrentHashMap.newKeySet();
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
            if (style != null && !style.isBlank()) {
                loadStyleIfAbsent(style.trim());
            }
        }
    }

    private StyleSheet loadStyleIfAbsent(String component) {
        String normalizedComponent = normalizeComponent(component);
        componentResourceNames.putIfAbsent(normalizedComponent, component.trim());
        return styleSheets.computeIfAbsent(normalizedComponent, key -> {
            String resourceName = componentResourceNames.getOrDefault(key, component);
            try {
                StyleSheet sheet = loadStyle(resourceName);
                elementStyles.put(normalizedComponent, sheet.styles());
                return sheet;
            } catch (StyleLoadingException error) {
                if (error.isMissingResource()) {
                    Engine.getLOGGER().warn("Style '{}' is not defined; using default fallback style.", resourceName);
                } else {
                    Engine.getLOGGER().error("Failed to load style '{}': {}", resourceName, error.getMessage(), error);
                }
                StyleAttributes fallback = StyleAttributes.defaults(resourceName);
                JsonObject fallbackJson = gson.toJsonTree(fallback).getAsJsonObject();
                StyleSheet sheet = new StyleSheet(
                        Map.of(DEFAULT_STYLE_NAME, fallbackJson),
                        Map.of(DEFAULT_STYLE_NAME, fallback)
                );
                elementStyles.put(normalizedComponent, sheet.styles());
                return sheet;
            }
        });
    }

    private StyleSheet loadStyle(String component) throws StyleLoadingException {
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

        Map<String, JsonObject> declarations = parseDeclarations(componentStyles);
        if (declarations.isEmpty()) {
            StyleAttributes fallback = StyleAttributes.defaults(component);
            return new StyleSheet(
                    Map.of(DEFAULT_STYLE_NAME, gson.toJsonTree(fallback).getAsJsonObject()),
                    Map.of(DEFAULT_STYLE_NAME, fallback)
            );
        }

        Map<String, JsonObject> resolvedJson = new LinkedHashMap<>();
        for (String styleName : declarations.keySet()) {
            resolveDeclaration(styleName, declarations, resolvedJson, new ArrayDeque<>());
        }

        Map<String, StyleAttributes> resolvedStyles = new LinkedHashMap<>();
        resolvedJson.forEach((styleName, json) -> {
            StyleAttributes parsed = gson.fromJson(json, StyleAttributes.class);
            resolvedStyles.put(
                    styleName,
                    parsed == null ? StyleAttributes.defaults(styleName) : parsed.normalized(styleName)
            );
        });

        return new StyleSheet(
                immutableJsonMap(resolvedJson),
                Collections.unmodifiableMap(resolvedStyles)
        );
    }

    private Map<String, JsonObject> parseDeclarations(JsonObject componentStyles) {
        Map<String, JsonObject> declarations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : componentStyles.entrySet()) {
            String styleName = entry.getKey();
            JsonElement styleData = entry.getValue();
            if (styleData.isJsonObject()) {
                declarations.put(styleName, styleData.getAsJsonObject().deepCopy());
            } else if (styleData.isJsonArray()) {
                int index = 0;
                for (JsonElement element : styleData.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        declarations.put(styleName + "_" + index, element.getAsJsonObject().deepCopy());
                        index++;
                    } else {
                        Engine.getLOGGER().warn("Non-object element in style array '{}'.", styleName);
                    }
                }
            } else {
                Engine.getLOGGER().warn("Unexpected JSON value for style '{}'.", styleName);
            }
        }
        return declarations;
    }

    private JsonObject resolveDeclaration(String styleName,
                                          Map<String, JsonObject> declarations,
                                          Map<String, JsonObject> resolved,
                                          Deque<String> path) {
        JsonObject cached = resolved.get(styleName);
        if (cached != null) {
            return cached;
        }
        if (path.contains(styleName)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(styleName);
            throw new IllegalStateException("Cyclic style inheritance: " + String.join(" -> ", cycle));
        }

        JsonObject declaration = declarations.get(styleName);
        if (declaration == null) {
            throw new IllegalArgumentException("Unknown inherited style: " + styleName);
        }

        path.addLast(styleName);
        JsonObject merged = new JsonObject();
        for (String parentName : inheritanceNames(declaration)) {
            String resolvedParent = findStyleName(declarations.keySet(), parentName);
            if (resolvedParent == null) {
                throw new IllegalArgumentException(
                        "Style '" + styleName + "' inherits missing style '" + parentName + "'"
                );
            }
            mergeInto(merged, resolveDeclaration(resolvedParent, declarations, resolved, path));
        }

        JsonObject child = declaration.deepCopy();
        INHERITANCE_KEYS.forEach(child::remove);
        mergeInto(merged, child);
        path.removeLast();
        resolved.put(styleName, merged);
        return merged;
    }

    private List<String> inheritanceNames(JsonObject declaration) {
        for (String key : INHERITANCE_KEYS) {
            JsonElement inheritance = declaration.get(key);
            if (inheritance == null || inheritance.isJsonNull()) {
                continue;
            }
            if (inheritance.isJsonPrimitive()) {
                String value = inheritance.getAsString();
                return value.isBlank() ? List.of() : splitStyleChain(value);
            }
            if (inheritance.isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement item : inheritance.getAsJsonArray()) {
                    if (item.isJsonPrimitive() && !item.getAsString().isBlank()) {
                        names.add(item.getAsString().trim());
                    }
                }
                return names;
            }
            throw new IllegalArgumentException("Style inheritance must be a string or string array");
        }
        return List.of();
    }

    private JsonObject loadJson(String path) throws StyleLoadingException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
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
        } catch (JsonSyntaxException error) {
            throw new StyleLoadingException("Invalid JSON format in " + path, error);
        } catch (StyleLoadingException error) {
            throw error;
        } catch (Exception error) {
            throw new StyleLoadingException("Error reading JSON file: " + path, error);
        }
    }

    private JsonObject findComponentStylesIgnoreCase(JsonObject stylesObject, String component) {
        for (Map.Entry<String, JsonElement> entry : stylesObject.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(component) && entry.getValue().isJsonObject()) {
                return entry.getValue().getAsJsonObject();
            }
        }
        return null;
    }

    public StyleAttributes getStyle(String componentType, String styleName) {
        if (componentType == null || componentType.isBlank()) {
            return StyleAttributes.defaults(DEFAULT_STYLE_NAME);
        }
        StyleSheet sheet = loadStyleIfAbsent(componentType);
        if (styleName != null) {
            String exact = findStyleName(sheet.styles().keySet(), styleName.trim());
            if (exact != null) {
                return sheet.styles().get(exact);
            }
        }
        return resolveStyle(componentType, splitStyleChain(styleName), Map.of());
    }

    public StyleAttributes resolveStyle(String componentType,
                                        Collection<String> styleChain,
                                        Map<String, String> overrides) {
        if (componentType == null || componentType.isBlank()) {
            return StyleAttributes.defaults(DEFAULT_STYLE_NAME);
        }

        StyleSheet sheet = loadStyleIfAbsent(componentType);
        List<String> resolvedChain = resolveChain(sheet, styleChain, componentType);
        Map<String, String> safeOverrides = overrides == null || overrides.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(overrides));
        StyleCacheKey cacheKey = new StyleCacheKey(
                normalizeComponent(componentType),
                List.copyOf(resolvedChain),
                safeOverrides
        );

        return composedStyleCache.computeIfAbsent(cacheKey, ignored -> {
            JsonObject merged = new JsonObject();
            for (String styleName : resolvedChain) {
                mergeInto(merged, sheet.resolvedJson().get(styleName));
            }
            safeOverrides.forEach((path, value) -> putOverride(merged, path, value));

            String resolvedName = String.join("+", resolvedChain);
            StyleAttributes parsed = gson.fromJson(merged, StyleAttributes.class);
            return parsed == null
                    ? StyleAttributes.defaults(resolvedName)
                    : parsed.normalized(resolvedName);
        });
    }

    private List<String> resolveChain(StyleSheet sheet,
                                      Collection<String> requestedChain,
                                      String componentType) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        if (requestedChain != null) {
            for (String requested : requestedChain) {
                if (requested == null || requested.isBlank()) {
                    continue;
                }
                String actual = findStyleName(sheet.styles().keySet(), requested.trim());
                if (actual != null) {
                    resolved.add(actual);
                } else {
                    warnMissingStyle(componentType, requested);
                }
            }
        }

        if (resolved.isEmpty()) {
            String fallback = findStyleName(sheet.styles().keySet(), DEFAULT_STYLE_NAME);
            if (fallback == null) {
                fallback = sheet.styles().keySet().stream().findFirst().orElse(DEFAULT_STYLE_NAME);
            }
            resolved.add(fallback);
        }
        return List.copyOf(resolved);
    }

    private void warnMissingStyle(String componentType, String styleName) {
        String warningKey = normalizeComponent(componentType) + ':' + styleName.toLowerCase(Locale.ROOT);
        if (missingStyleWarnings.add(warningKey)) {
            Engine.getLOGGER().warn(
                    "Style '{}' was not found for component '{}'; it was skipped during composition.",
                    styleName,
                    componentType
            );
        }
    }

    public boolean hasStyle(String componentType, String styleName) {
        if (componentType == null || styleName == null) {
            return false;
        }
        return findStyleName(loadStyleIfAbsent(componentType).styles().keySet(), styleName) != null;
    }

    public Set<String> getStyleNames(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(loadStyleIfAbsent(componentType).styles().keySet());
    }

    public void reload(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            return;
        }
        String key = normalizeComponent(componentType);
        String resourceName = componentResourceNames.getOrDefault(key, componentType);
        styleSheets.remove(key);
        elementStyles.remove(key);
        composedStyleCache.keySet().removeIf(cacheKey -> cacheKey.componentType().equals(key));
        missingStyleWarnings.removeIf(warning -> warning.startsWith(key + ':'));
        loadStyleIfAbsent(resourceName);
    }

    public void reloadAll() {
        List<String> components = new ArrayList<>(componentResourceNames.values());
        styleSheets.clear();
        elementStyles.clear();
        composedStyleCache.clear();
        missingStyleWarnings.clear();
        components.forEach(this::loadStyleIfAbsent);
    }

    public Map<String, Map<String, StyleAttributes>> getElementStyles() {
        return Collections.unmodifiableMap(elementStyles);
    }

    public static List<String> splitStyleChain(String rawStyleChain) {
        if (rawStyleChain == null || rawStyleChain.isBlank()) {
            return List.of();
        }
        List<String> styles = new ArrayList<>();
        for (String token : rawStyleChain.trim().split("[\\s,+>]+")) {
            if (!token.isBlank()) {
                styles.add(token.trim());
            }
        }
        return List.copyOf(styles);
    }

    private void putOverride(JsonObject root, String path, String rawValue) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] segments = path.trim().split("\\.");
        JsonObject target = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            JsonElement existing = target.get(segment);
            if (existing != null && existing.isJsonObject()) {
                target = existing.getAsJsonObject();
            } else {
                JsonObject nested = new JsonObject();
                target.add(segment, nested);
                target = nested;
            }
        }
        target.add(segments[segments.length - 1], parseOverrideValue(rawValue));
    }

    private JsonElement parseOverrideValue(String rawValue) {
        if (rawValue == null) {
            return JsonNull.INSTANCE;
        }
        String value = rawValue.trim();
        if (value.equalsIgnoreCase("null")) {
            return JsonNull.INSTANCE;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return new JsonPrimitive(Boolean.parseBoolean(value));
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                return JsonParser.parseString(value);
            } catch (JsonSyntaxException ignored) {
                return new JsonPrimitive(rawValue);
            }
        }
        try {
            if (value.matches("[-+]?\\d+")) {
                return new JsonPrimitive(Long.parseLong(value));
            }
            if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                return new JsonPrimitive(Double.parseDouble(value));
            }
        } catch (NumberFormatException ignored) {
            // Preserve the original string below.
        }
        return new JsonPrimitive(rawValue);
    }

    private static void mergeInto(JsonObject target, JsonObject source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            JsonElement incoming = entry.getValue();
            JsonElement existing = target.get(key);
            if (incoming.isJsonObject() && existing != null && existing.isJsonObject()) {
                mergeInto(existing.getAsJsonObject(), incoming.getAsJsonObject());
            } else {
                target.add(key, incoming.deepCopy());
            }
        }
    }

    private static Map<String, JsonObject> immutableJsonMap(Map<String, JsonObject> source) {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        source.forEach((name, json) -> copy.put(name, json.deepCopy()));
        return Collections.unmodifiableMap(copy);
    }

    private static String findStyleName(Collection<String> names, String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        for (String name : names) {
            if (name.equals(requested)) {
                return name;
            }
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(requested)) {
                return name;
            }
        }
        return null;
    }

    private static String normalizeComponent(String component) {
        return component == null ? "" : component.trim().toLowerCase(Locale.ROOT);
    }

    private record StyleSheet(
            Map<String, JsonObject> resolvedJson,
            Map<String, StyleAttributes> styles
    ) {
    }

    private record StyleCacheKey(
            String componentType,
            List<String> styleChain,
            Map<String, String> overrides
    ) {
    }
}
