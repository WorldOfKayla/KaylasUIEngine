package org.takesome.kaylasEngine.gui.componentAccessor.internal.source;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;
import org.takesome.kaylasEngine.gui.components.ComponentCatalog;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Optional;

/** Package-private live source backed by the current {@link GuiBuilder} maps. */
final class GuiBuilderAccessSource implements ComponentAccessSource {
    private final GuiBuilder guiBuilder;

    GuiBuilderAccessSource(GuiBuilder guiBuilder) {
        this.guiBuilder = guiBuilder;
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
