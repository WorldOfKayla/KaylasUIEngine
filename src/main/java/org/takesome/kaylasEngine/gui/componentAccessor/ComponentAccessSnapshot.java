package org.takesome.kaylasEngine.gui.componentAccessor;

import javax.swing.JComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable point-in-time view of a {@link ComponentsAccessor} index.
 *
 * @param revision monotonically increasing accessor revision.
 * @param createdAt snapshot creation time.
 * @param rootPanelId accessor root panel id.
 * @param components components indexed by id.
 * @param panelComponents direct accepted components grouped by logical panel id.
 * @param metadata detailed index metadata keyed by component id.
 */
public record ComponentAccessSnapshot(
        long revision,
        Instant createdAt,
        String rootPanelId,
        Map<String, JComponent> components,
        Map<String, List<JComponent>> panelComponents,
        Map<String, IndexedComponent> metadata
) {
    /**
     * Creates a defensive immutable snapshot.
     *
     * @param revision accessor revision.
     * @param createdAt snapshot creation time.
     * @param rootPanelId root panel id.
     * @param components indexed components.
     * @param panelComponents direct selected components grouped by panel.
     * @param metadata detailed component metadata.
     */
    public ComponentAccessSnapshot {
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        rootPanelId = rootPanelId == null ? "" : rootPanelId;
        components = immutableMap(components);
        metadata = immutableMap(metadata);
        panelComponents = immutablePanelMap(panelComponents);
    }

    /**
     * Returns the indexed component count.
     *
     * @return number of globally indexed ids.
     */
    public int size() {
        return components.size();
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, List<JComponent>> immutablePanelMap(
            Map<String, List<JComponent>> source
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<JComponent>> copy = new LinkedHashMap<>();
        source.forEach((panelId, components) -> copy.put(
                panelId,
                components == null ? List.of() : List.copyOf(new ArrayList<>(components))
        ));
        return Collections.unmodifiableMap(copy);
    }
}
