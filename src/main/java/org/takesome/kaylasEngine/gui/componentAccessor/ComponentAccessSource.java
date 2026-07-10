package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.ComponentCatalog;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Optional;

/**
 * Supplies panel and component graphs to {@link ComponentsAccessor}.
 *
 * <p>The abstraction allows the accessor to operate on {@link GuiBuilder}, tests, dynamically
 * generated screens, or alternate UI registries without coupling traversal to one storage model.</p>
 */
public interface ComponentAccessSource {

    /**
     * Finds a panel by id.
     *
     * @param panelId panel id.
     * @return panel when present.
     */
    Optional<JPanel> findPanel(String panelId);

    /**
     * Returns root components registered directly for a panel.
     *
     * @param panelId panel id.
     * @return immutable or defensive component list.
     */
    List<JComponent> components(String panelId);

    /**
     * Returns logical child-panel ids.
     *
     * @param panelId parent panel id.
     * @return immutable or defensive child-id list.
     */
    List<String> childPanels(String panelId);

    /**
     * Returns the component catalog associated with this source when available.
     *
     * @return catalog, or empty for sources without definition metadata.
     */
    default Optional<ComponentCatalog> catalog() {
        return Optional.empty();
    }

    /**
     * Creates a live source adapter over a {@link GuiBuilder}.
     *
     * @param guiBuilder GUI builder.
     * @return source adapter.
     */
    static ComponentAccessSource from(GuiBuilder guiBuilder) {
        return new GuiBuilderComponentAccessSource(guiBuilder);
    }
}
