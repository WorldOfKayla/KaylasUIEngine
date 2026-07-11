package org.takesome.kaylasEngine.gui.componentAccessor.internal.index;

import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal boundary for building a detached index from a component graph.
 *
 * <p>The concrete traversal implementation is package-private. The nested result performs defensive
 * copies so a completed traversal can be committed atomically by the public accessor facade.</p>
 */
public interface ComponentGraphIndexing {
    static ComponentGraphIndexing create(ComponentAccessSource source,
                                         ComponentAccessorOptions options,
                                         List<Class<? extends JComponent>> componentTypes) {
        return new DefaultComponentGraphIndexer(source, options, componentTypes);
    }

    Index build(String rootPanelId);

    record Index(
            Map<String, JComponent> components,
            Map<String, List<JComponent>> panelComponents,
            Map<String, IndexedComponent> metadata
    ) {
        public Index {
            components = immutableMap(components);
            panelComponents = immutablePanelMap(panelComponents);
            metadata = immutableMap(metadata);
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
}
