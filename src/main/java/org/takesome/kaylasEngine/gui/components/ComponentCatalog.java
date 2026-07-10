package org.takesome.kaylasEngine.gui.components;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe database of every basic and composite component type known to the engine.
 */
public final class ComponentCatalog {
    private final Map<String, AbstractComponentDefinition<? extends JComponent>> definitions =
            new ConcurrentHashMap<>();
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    public AbstractComponentDefinition<? extends JComponent> register(
            AbstractComponentDefinition<? extends JComponent> definition
    ) {
        Objects.requireNonNull(definition, "definition");
        String canonicalKey = normalize(definition.type());
        AbstractComponentDefinition<? extends JComponent> previous =
                definitions.put(canonicalKey, definition);
        if (previous != null) {
            aliases.entrySet().removeIf(entry -> entry.getValue().equals(canonicalKey));
        }
        for (String alias : definition.aliases()) {
            registerAlias(alias, definition.type());
        }
        return previous;
    }

    public void registerAlias(String alias, String componentType) {
        String aliasKey = normalize(alias);
        AbstractComponentDefinition<? extends JComponent> target = find(componentType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot register alias '" + alias + "' for unknown component type '"
                                + componentType + "'"
                ));
        String targetKey = normalize(target.type());

        AbstractComponentDefinition<? extends JComponent> canonicalCollision = definitions.get(aliasKey);
        if (canonicalCollision != null && !aliasKey.equals(targetKey)) {
            throw new IllegalArgumentException(
                    "Alias '" + alias + "' collides with canonical component type '"
                            + canonicalCollision.type() + "'"
            );
        }

        String existingTarget = aliases.get(aliasKey);
        if (existingTarget != null && !existingTarget.equals(targetKey)) {
            throw new IllegalArgumentException(
                    "Alias '" + alias + "' is already assigned to component type '"
                            + definitions.get(existingTarget).type() + "'"
            );
        }
        aliases.put(aliasKey, targetKey);
    }

    public Optional<AbstractComponentDefinition<? extends JComponent>> find(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(componentType);
        return Optional.ofNullable(definitions.get(aliases.getOrDefault(key, key)));
    }

    public boolean contains(String componentType) {
        return find(componentType).isPresent();
    }

    public boolean unregister(String componentType) {
        Optional<AbstractComponentDefinition<? extends JComponent>> definition = find(componentType);
        if (definition.isEmpty()) {
            return false;
        }
        String canonicalKey = normalize(definition.get().type());
        aliases.entrySet().removeIf(entry -> entry.getValue().equals(canonicalKey));
        return definitions.remove(canonicalKey) != null;
    }

    public Map<String, AbstractComponentDefinition<? extends JComponent>> definitions() {
        Map<String, AbstractComponentDefinition<? extends JComponent>> result = new LinkedHashMap<>();
        definitions.values().stream()
                .sorted((left, right) -> left.type().compareToIgnoreCase(right.type()))
                .forEach(definition -> result.put(definition.type(), definition));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, AbstractComponentDefinition<? extends JComponent>> definitions(ComponentKind kind) {
        Objects.requireNonNull(kind, "kind");
        Map<String, AbstractComponentDefinition<? extends JComponent>> result = new LinkedHashMap<>();
        definitions.values().stream()
                .filter(definition -> definition.kind() == kind)
                .sorted((left, right) -> left.type().compareToIgnoreCase(right.type()))
                .forEach(definition -> result.put(definition.type(), definition));
        return Collections.unmodifiableMap(result);
    }

    public List<String> types() {
        List<String> types = new ArrayList<>();
        definitions.values().forEach(definition -> types.add(definition.type()));
        types.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(types);
    }

    public List<String> types(ComponentKind kind) {
        return definitions(kind).keySet().stream().toList();
    }

    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    public Set<String> canonicalKeys() {
        return Set.copyOf(definitions.keySet());
    }

    public int size() {
        return definitions.size();
    }

    public int size(ComponentKind kind) {
        return definitions(kind).size();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Component identifier must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
