package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.ComponentCatalog;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compatibility wrapper for a live {@link GuiBuilder}-backed component source.
 *
 * @deprecated Prefer {@link ComponentAccessSource#from(GuiBuilder)}. The concrete source adapter is
 * now an internal implementation detail.
 */
@Deprecated(since = "2.2.0-AURELIA", forRemoval = false)
public final class GuiBuilderComponentAccessSource implements ComponentAccessSource {
    private final GuiBuilder guiBuilder;
    private final ComponentAccessSource delegate;

    /**
     * Creates the compatibility wrapper.
     *
     * @param guiBuilder GUI builder exposed through the source abstraction.
     */
    public GuiBuilderComponentAccessSource(GuiBuilder guiBuilder) {
        this.guiBuilder = Objects.requireNonNull(guiBuilder, "guiBuilder");
        this.delegate = ComponentAccessSource.from(guiBuilder);
    }

    /**
     * Returns the wrapped builder for legacy callers.
     *
     * @return wrapped GUI builder.
     */
    public GuiBuilder guiBuilder() {
        return guiBuilder;
    }

    @Override
    public Optional<JPanel> findPanel(String panelId) {
        return delegate.findPanel(panelId);
    }

    @Override
    public List<JComponent> components(String panelId) {
        return delegate.components(panelId);
    }

    @Override
    public List<String> childPanels(String panelId) {
        return delegate.childPanels(panelId);
    }

    @Override
    public Optional<ComponentCatalog> catalog() {
        return delegate.catalog();
    }
}
