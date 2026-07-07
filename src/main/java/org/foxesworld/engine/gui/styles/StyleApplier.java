package org.foxesworld.engine.gui.styles;

import org.foxesworld.engine.Engine;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Utility that applies visual styles to Swing components.
 *
 * <p>
 * Maintains a registry of handlers that know how to apply {@link StyleAttributes}
 * to specific component types. Handlers are registered per component class and
 * invoked when {@link #applyStyle(JComponent, StyleAttributes)} is called.
 * </p>
 */
public class StyleApplier {
    private String[] components;

    public StyleApplier(Engine engine){
        this.components = engine.getEngineData().getStyles();
    }

    private final Map<Class<? extends JComponent>, BiConsumer<JComponent, StyleAttributes>> styleHandlers = new HashMap<>();

    /**
     * Registers a style handler for the specified component type.
     *
     * @param componentClass Component class (for example {@code JButton.class}).
     * @param handler        Style handler (lambda or method reference) that receives the component
     *                       and the {@link StyleAttributes} to apply.
     * @param <T>            type of the Swing component.
     */
    public <T extends JComponent> void registerStyleHandler(Class<T> componentClass, BiConsumer<T, StyleAttributes> handler) {
        styleHandlers.put(componentClass, (BiConsumer<JComponent, StyleAttributes>) handler);
    }

    /**
     * Applies the given style to the provided component using the registered handler.
     *
     * @param component       component to style.
     * @param styleAttributes style attributes to apply.
     * @throws IllegalArgumentException      if either {@code component} or {@code styleAttributes} is null.
     * @throws UnsupportedOperationException if no handler is registered for the component's class.
     */
    public void applyStyle(JComponent component, StyleAttributes styleAttributes) {
        if (component == null || styleAttributes == null) {
            throw new IllegalArgumentException("Component and StyleAttributes cannot be null.");
        }

        BiConsumer<JComponent, StyleAttributes> handler = styleHandlers.get(component.getClass());
        if (handler != null) {
            handler.accept(component, styleAttributes);
        } else {
            throw new UnsupportedOperationException("No style handler registered for component type: " + component.getClass());
        }
    }
}
