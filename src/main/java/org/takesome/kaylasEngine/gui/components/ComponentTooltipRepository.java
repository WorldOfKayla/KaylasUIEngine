package org.takesome.kaylasEngine.gui.components;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.utils.tooltip.TooltipAttributes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Loads and caches tooltip style descriptors independently of component construction. */
final class ComponentTooltipRepository {
    private static final String TOOLTIP_RESOURCE = "assets/styles/tooltip.json";

    private final ClassLoader classLoader;
    private final Map<String, Optional<TooltipAttributes>> cache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    ComponentTooltipRepository(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    Optional<TooltipAttributes> find(String styleName) {
        String normalized = styleName == null || styleName.isBlank() ? "default" : styleName.trim();
        return cache.computeIfAbsent(normalized, this::load);
    }

    private Optional<TooltipAttributes> load(String styleName) {
        try (InputStream stream = classLoader.getResourceAsStream(TOOLTIP_RESOURCE)) {
            if (stream == null) {
                Engine.LOGGER.warn("Tooltip style resource not found: {}", TOOLTIP_RESOURCE);
                return Optional.empty();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has(styleName)) {
                    Engine.LOGGER.warn("Tooltip style '{}' not found.", styleName);
                    return Optional.empty();
                }
                return Optional.ofNullable(gson.fromJson(root.get(styleName), TooltipAttributes.class));
            }
        } catch (Exception error) {
            Engine.LOGGER.error("Failed to load tooltip style '{}'.", styleName, error);
            return Optional.empty();
        }
    }
}
