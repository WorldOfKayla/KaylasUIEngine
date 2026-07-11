package org.takesome.kaylasEngine.gui.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves one immutable component descriptor against all matching configuration fragments. */
public final class ComponentConfigResolver {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final ComponentConfigGroupRegistry registry;

    public ComponentConfigResolver(ComponentConfigGroupRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ComponentAttributes resolve(ComponentAttributes source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> descriptor = GSON.fromJson(GSON.toJsonTree(source), MAP_TYPE);
        List<ComponentConfigFragment> fragments = registry.fragmentsFor(source);
        Map<String, Object> resolved = Map.of();

        for (ComponentConfigFragment fragment : fragments) {
            if (fragment.componentId().isBlank()) {
                resolved = DeepConfigMerger.merge(resolved, fragment.values());
            }
        }
        // The descriptor is the final authority over defaults supplied by global/type/group fragments.
        resolved = DeepConfigMerger.merge(resolved, descriptor);
        // Explicit instance extensions intentionally run last and can append or override structure.
        for (ComponentConfigFragment fragment : fragments) {
            if (!fragment.componentId().isBlank()) {
                resolved = DeepConfigMerger.merge(resolved, fragment.values());
            }
        }

        ComponentAttributes result = GSON.fromJson(GSON.toJsonTree(resolved), ComponentAttributes.class);
        if (result.getComponentType() == null || result.getComponentType().isBlank()) {
            result.setComponentType(source.getComponentType());
        }
        if ((result.getComponentId() == null || result.getComponentId().isBlank())
                && source.getComponentId() != null) {
            result.setComponentId(source.getComponentId());
        }
        return result;
    }

    public ComponentConfigGroupRegistry registry() {
        return registry;
    }
}
