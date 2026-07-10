package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;

/** Immutable per-component creation state. */
public record ComponentCreationContext(
        ComponentFactory factory,
        AbstractComponentDefinition<? extends javax.swing.JComponent> definition,
        ComponentAttributes attributes,
        StyleAttributes style,
        Rectangle bounds,
        List<String> styleChain
) {
    public ComponentCreationContext {
        factory = Objects.requireNonNull(factory, "factory");
        definition = Objects.requireNonNull(definition, "definition");
        attributes = Objects.requireNonNull(attributes, "attributes");
        style = Objects.requireNonNull(style, "style");
        bounds = bounds == null ? new Rectangle() : new Rectangle(bounds);
        styleChain = styleChain == null ? List.of() : List.copyOf(styleChain);
    }

    public Engine engine() {
        return factory.getEngine();
    }

    public String componentType() {
        return definition.type();
    }

    public String componentId() {
        return attributes.getComponentId();
    }
}
