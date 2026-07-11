package org.takesome.kaylasEngine.gui.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime registry for group-, type- and instance-scoped component configuration fragments.
 *
 * <p>Fragments are immutable. Active runtime groups can be changed after startup; newly created or
 * explicitly refreshed components resolve against the updated group set.</p>
 */
public final class ComponentConfigGroupRegistry {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final CopyOnWriteArrayList<ComponentConfigFragment> fragments = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final LinkedHashSet<String> activeGroups = new LinkedHashSet<>();
    private final AtomicLong sequence = new AtomicLong();

    public ComponentConfigFragment register(
            String group,
            String componentType,
            String componentId,
            int priority,
            String source,
            Map<String, ?> values
    ) {
        Map<String, Object> copied = values == null
                ? Map.of()
                : GSON.fromJson(GSON.toJsonTree(values), MAP_TYPE);
        ComponentConfigFragment fragment = new ComponentConfigFragment(
                group,
                componentType,
                componentId,
                priority,
                sequence.incrementAndGet(),
                source,
                copied
        );
        fragments.add(fragment);
        notifyChanged();
        return fragment;
    }

    public ComponentConfigFragment registerGlobal(Map<String, ?> values) {
        return register("", "", "", 0, "global", values);
    }

    public ComponentConfigFragment registerType(String componentType, Map<String, ?> values) {
        return register("", componentType, "", 100, "type:" + componentType, values);
    }

    public ComponentConfigFragment registerGroup(String group, Map<String, ?> values) {
        return register(group, "", "", 200, "group:" + group, values);
    }

    public ComponentConfigFragment registerGroupType(
            String group,
            String componentType,
            Map<String, ?> values
    ) {
        return register(group, componentType, "", 300,
                "group:" + group + "/type:" + componentType, values);
    }

    public ComponentConfigFragment extendComponent(String componentId, Map<String, ?> values) {
        return register("", "", componentId, 1000, "component:" + componentId, values);
    }

    public ComponentConfigFragment extendComponent(
            String group,
            String componentId,
            Map<String, ?> values
    ) {
        return register(group, "", componentId, 1100,
                "group:" + group + "/component:" + componentId, values);
    }

    public ComponentConfigFragment appendChildren(
            String group,
            String componentId,
            Collection<ComponentAttributes> children
    ) {
        List<Object> serializedChildren = children == null
                ? List.of()
                : children.stream().filter(Objects::nonNull)
                .map(child -> GSON.fromJson(GSON.toJsonTree(child), Object.class))
                .toList();
        return extendComponent(group, componentId, Map.of(
                "childComponents", Map.of(
                        DeepConfigMerger.MERGE_KEY, ConfigMergeStrategy.APPEND.name(),
                        DeepConfigMerger.VALUE_KEY, serializedChildren
                )
        ));
    }

    public boolean remove(ComponentConfigFragment fragment) {
        boolean removed = fragments.remove(fragment);
        if (removed) {
            notifyChanged();
        }
        return removed;
    }

    public void clear() {
        fragments.clear();
        synchronized (activeGroups) {
            activeGroups.clear();
        }
        notifyChanged();
    }

    public void activateGroup(String group) {
        String normalized = normalizeGroup(group);
        if (normalized.isBlank()) {
            return;
        }
        boolean changed;
        synchronized (activeGroups) {
            changed = activeGroups.add(normalized);
        }
        if (changed) {
            notifyChanged();
        }
    }

    public void deactivateGroup(String group) {
        String normalized = normalizeGroup(group);
        boolean changed;
        synchronized (activeGroups) {
            changed = activeGroups.removeIf(existing -> existing.equalsIgnoreCase(normalized));
        }
        if (changed) {
            notifyChanged();
        }
    }

    public void setActiveGroups(Collection<String> groups) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (groups != null) {
            groups.stream().map(ComponentConfigGroupRegistry::normalizeGroup)
                    .filter(value -> !value.isBlank())
                    .forEach(normalized::add);
        }
        boolean changed;
        synchronized (activeGroups) {
            changed = !activeGroups.equals(normalized);
            activeGroups.clear();
            activeGroups.addAll(normalized);
        }
        if (changed) {
            notifyChanged();
        }
    }

    public List<String> activeGroups() {
        synchronized (activeGroups) {
            return List.copyOf(activeGroups);
        }
    }

    public List<ComponentConfigFragment> fragmentsFor(ComponentAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        LinkedHashSet<String> groups = new LinkedHashSet<>(activeGroups());
        groups.addAll(attributes.getConfigGroups());

        String type = attributes.getComponentType();
        String id = attributes.getComponentId();
        List<ComponentConfigFragment> result = new ArrayList<>();
        for (ComponentConfigFragment fragment : fragments) {
            if (fragment.group().isBlank()) {
                if (fragment.matches("", type, id)) {
                    result.add(fragment);
                }
                continue;
            }
            for (String group : groups) {
                if (fragment.matches(group, type, id)) {
                    result.add(fragment);
                    break;
                }
            }
        }
        Map<String, Integer> groupOrder = new LinkedHashMap<>();
        int groupIndex = 0;
        for (String group : groups) {
            groupOrder.putIfAbsent(group.toLowerCase(java.util.Locale.ROOT), groupIndex++);
        }
        result.sort(Comparator
                .comparingInt(ComponentConfigFragment::priority)
                .thenComparingInt(fragment -> fragment.group().isBlank()
                        ? -1
                        : groupOrder.getOrDefault(
                                fragment.group().toLowerCase(java.util.Locale.ROOT),
                                Integer.MAX_VALUE
                        ))
                .thenComparingInt(ComponentConfigFragment::specificity)
                .thenComparingLong(ComponentConfigFragment::sequence));
        return List.copyOf(result);
    }

    public AutoCloseable onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    public int fragmentCount() {
        return fragments.size();
    }

    public Set<String> declaredGroups() {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        for (ComponentConfigFragment fragment : fragments) {
            if (!fragment.group().isBlank()) {
                groups.add(fragment.group());
            }
        }
        return Set.copyOf(groups);
    }

    private void notifyChanged() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // One observer cannot block configuration registry updates.
            }
        }
    }

    private static String normalizeGroup(String group) {
        return group == null ? "" : group.trim();
    }
}
