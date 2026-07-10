package org.takesome.kaylasEngine.gui.componentAccessor;

import javax.swing.JComponent;
import java.util.Objects;

/**
 * Immutable metadata describing one named component discovered by {@link ComponentsAccessor}.
 *
 * @param id globally indexed component id.
 * @param component Swing component instance.
 * @param panelId logical panel that owns the root traversal.
 * @param parentComponentId nearest named parent component id, or {@code null}.
 * @param depth component-tree depth relative to the panel root component.
 * @param nested {@code true} when the component was discovered below another registered component.
 * @param selected {@code true} when the component matches the accessor's configured component types.
 * @param formEligible {@code true} when the component may contribute a form value.
 * @param catalogType component descriptor type when runtime metadata is available.
 * @param compositeScope constructor composite scope id when available.
 * @param localId local constructor node id when available.
 */
public record IndexedComponent(
        String id,
        JComponent component,
        String panelId,
        String parentComponentId,
        int depth,
        boolean nested,
        boolean selected,
        boolean formEligible,
        String catalogType,
        String compositeScope,
        String localId
) {
    /**
     * Validates and normalizes index metadata.
     *
     * @param id globally indexed id.
     * @param component Swing component instance.
     * @param panelId logical owner panel id.
     * @param parentComponentId nearest named parent id.
     * @param depth component-tree depth.
     * @param nested whether the component is nested.
     * @param selected whether the component matches selected types.
     * @param formEligible whether the component may contribute a form value.
     * @param catalogType descriptor/catalog type.
     * @param compositeScope constructor scope id.
     * @param localId constructor local node id.
     */
    public IndexedComponent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Indexed component id must not be blank");
        }
        id = id.trim();
        component = Objects.requireNonNull(component, "component");
        panelId = normalize(panelId);
        parentComponentId = normalize(parentComponentId);
        catalogType = normalize(catalogType);
        compositeScope = normalize(compositeScope);
        localId = normalize(localId);
        depth = Math.max(0, depth);
    }

    /**
     * Tests whether the indexed component belongs to a constructor scope.
     *
     * @return {@code true} when scope metadata is present.
     */
    public boolean scoped() {
        return compositeScope != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
