package org.takesome.kaylasEngine.gui.componentAccessor.internal.index;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessException;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.support.ComponentIds;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.constructor.ConstructedCompositeComponent;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Traverses a logical panel/component graph and builds a detached component index.
 *
 * <p>The indexer contains traversal, duplicate-id and metadata concerns. It deliberately does not
 * mutate {@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor}; the accessor commits a completed result atomically.</p>
 */
final class DefaultComponentGraphIndexer implements ComponentGraphIndexing {
    private static final String ATTRIBUTES_PROPERTY = "kaylas.ui.attributes";

    private final ComponentAccessSource source;
    private final ComponentAccessorOptions options;
    private final List<Class<? extends JComponent>> componentTypes;

    DefaultComponentGraphIndexer(ComponentAccessSource source,
                          ComponentAccessorOptions options,
                          List<Class<? extends JComponent>> componentTypes) {
        this.source = Objects.requireNonNull(source, "source");
        this.options = Objects.requireNonNull(options, "options");
        this.componentTypes = List.copyOf(Objects.requireNonNull(componentTypes, "componentTypes"));
    }

    @Override
    public Index build(String rootPanelId) {
        if (rootPanelId == null || rootPanelId.isBlank()) {
            throw new IllegalArgumentException("rootPanelId must not be blank");
        }
        String normalizedRoot = rootPanelId.trim();
        Map<String, JComponent> components = new LinkedHashMap<>();
        Map<String, List<JComponent>> panelComponents = new LinkedHashMap<>();
        Map<String, IndexedComponent> metadata = new LinkedHashMap<>();
        Set<String> visitedPanels = new LinkedHashSet<>();
        Set<JComponent> visitedComponents = Collections.newSetFromMap(new IdentityHashMap<>());

        boolean rootExists = source.findPanel(normalizedRoot).isPresent()
                || !source.components(normalizedRoot).isEmpty()
                || !source.childPanels(normalizedRoot).isEmpty();
        if (!rootExists && options.failOnMissingRootPanel()) {
            throw new ComponentAccessException("Root panel not found: " + normalizedRoot);
        }

        collectPanel(
                normalizedRoot,
                0,
                visitedPanels,
                visitedComponents,
                components,
                panelComponents,
                metadata
        );
        return new Index(components, panelComponents, metadata);
    }

    private void collectPanel(String panelId,
                              int depth,
                              Set<String> visitedPanels,
                              Set<JComponent> visitedComponents,
                              Map<String, JComponent> components,
                              Map<String, List<JComponent>> panelComponents,
                              Map<String, IndexedComponent> metadata) {
        guardDepth(depth, "panel", panelId);
        if (!visitedPanels.add(panelId)) {
            Engine.LOGGER.warn("ComponentAccessor skipped cyclic panel reference: {}", panelId);
            return;
        }

        List<JComponent> selectedRoots = new ArrayList<>();
        Set<JComponent> selectedRootIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JComponent root : source.components(panelId)) {
            if (root == null) {
                continue;
            }
            if (matchesSelectedType(root) && selectedRootIdentity.add(root)) {
                selectedRoots.add(root);
            }
            visitComponent(
                    root,
                    panelId,
                    null,
                    0,
                    false,
                    visitedComponents,
                    components,
                    metadata
            );
        }
        panelComponents.put(panelId, List.copyOf(selectedRoots));

        for (String childPanelId : source.childPanels(panelId)) {
            if (childPanelId == null || childPanelId.isBlank()) {
                continue;
            }
            collectPanel(
                    childPanelId.trim(),
                    depth + 1,
                    visitedPanels,
                    visitedComponents,
                    components,
                    panelComponents,
                    metadata
            );
        }
    }

    private void visitComponent(JComponent component,
                                String panelId,
                                String parentComponentId,
                                int depth,
                                boolean nested,
                                Set<JComponent> visitedComponents,
                                Map<String, JComponent> components,
                                Map<String, IndexedComponent> metadata) {
        guardDepth(depth, "component", component.getName());
        if (!visitedComponents.add(component)) {
            return;
        }

        String id = ComponentIds.normalize(component.getName());
        boolean selected = matchesSelectedType(component);
        boolean formEligible = selected && (!nested || options.includeNestedValuesInForms());
        String nextParentId = parentComponentId;

        if (id != null) {
            IndexedComponent indexed = new IndexedComponent(
                    id,
                    component,
                    panelId,
                    parentComponentId,
                    depth,
                    nested,
                    selected,
                    formEligible,
                    catalogType(component),
                    propertyString(component, ConstructedCompositeComponent.SCOPE_PROPERTY),
                    propertyString(component, ConstructedCompositeComponent.LOCAL_ID_PROPERTY)
            );
            registerComponent(id, component, indexed, components, metadata);
            nextParentId = id;
        }

        if (!options.traverseNestedComponents()) {
            return;
        }
        for (java.awt.Component child : component.getComponents()) {
            if (child instanceof JComponent childComponent) {
                visitComponent(
                        childComponent,
                        panelId,
                        nextParentId,
                        depth + 1,
                        true,
                        visitedComponents,
                        components,
                        metadata
                );
            }
        }
    }

    private void registerComponent(String id,
                                   JComponent component,
                                   IndexedComponent metadata,
                                   Map<String, JComponent> components,
                                   Map<String, IndexedComponent> metadataById) {
        JComponent existing = components.get(id);
        if (existing == null || existing == component) {
            components.put(id, component);
            metadataById.put(id, metadata);
            return;
        }

        switch (options.duplicatePolicy()) {
            case KEEP_FIRST -> Engine.LOGGER.warn(
                    "ComponentAccessor kept first duplicate id '{}': existing={}, ignored={}",
                    id,
                    existing.getClass().getName(),
                    component.getClass().getName()
            );
            case REPLACE -> {
                Engine.LOGGER.warn(
                        "ComponentAccessor replaced duplicate id '{}': old={}, new={}",
                        id,
                        existing.getClass().getName(),
                        component.getClass().getName()
                );
                components.put(id, component);
                metadataById.put(id, metadata);
            }
            case FAIL -> throw new ComponentAccessException(
                    "Duplicate component id '" + id + "': "
                            + existing.getClass().getName() + " and "
                            + component.getClass().getName()
            );
        }
    }

    private boolean matchesSelectedType(JComponent component) {
        return componentTypes.isEmpty()
                || componentTypes.stream().anyMatch(type -> type.isInstance(component));
    }

    private String catalogType(JComponent component) {
        Object attributes = component.getClientProperty(ATTRIBUTES_PROPERTY);
        if (attributes instanceof ComponentAttributes componentAttributes) {
            return ComponentIds.normalize(componentAttributes.getComponentType());
        }
        return propertyString(component, ConstructedCompositeComponent.DEFINITION_PROPERTY);
    }

    private void guardDepth(int depth, String graphType, String identifier) {
        if (depth > options.maximumTraversalDepth()) {
            throw new ComponentAccessException(
                    "Maximum " + graphType + " traversal depth exceeded at '" + identifier + "'"
            );
        }
    }

    private static String propertyString(JComponent component, String property) {
        Object value = component.getClientProperty(property);
        return value == null ? null : ComponentIds.normalize(String.valueOf(value));
    }

}
