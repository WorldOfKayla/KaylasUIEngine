package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.ComponentCatalog;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Live {@link ComponentAccessSource} backed by a {@link GuiBuilder}.
 *
 * <p>Each method reads the builder's current maps, so calling {@link ComponentsAccessor#refresh()}
 * after dynamic screen rebuilds observes the latest panel and component instances.</p>
 */
public final class GuiBuilderComponentAccessSource implements ComponentAccessSource {
    private final GuiBuilder guiBuilder;

    /**
     * Creates a source over a GUI builder.
     *
     * @param guiBuilder GUI builder to expose.
     */
    public GuiBuilderComponentAccessSource(GuiBuilder guiBuilder) {
        this.guiBuilder = Objects.requireNonNull(guiBuilder, "guiBuilder");
    }

    /** @return wrapped GUI builder. */
    public GuiBuilder guiBuilder() {
        return guiBuilder;
    }

    @Override
    public Optional<JPanel> findPanel(String panelId) {
        return Optional.ofNullable(guiBuilder.getPanelsMap().get(panelId));
    }

    @Override
    public List<JComponent> components(String panelId) {
        List<JComponent> components = guiBuilder.getComponentsMap().get(panelId);
        return components == null ? List.of() : List.copyOf(components);
    }

    @Override
    public List<String> childPanels(String panelId) {
        List<String> children = guiBuilder.getChildParentMap().get(panelId);
        return children == null ? List.of() : List.copyOf(children);
    }

    @Override
    public Optional<ComponentCatalog> catalog() {
        return Optional.of(guiBuilder.getComponentCatalog());
    }
}
