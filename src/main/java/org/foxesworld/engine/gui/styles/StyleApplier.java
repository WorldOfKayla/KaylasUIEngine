package org.foxesworld.engine.gui.styles;

import org.foxesworld.engine.Engine;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static org.foxesworld.engine.utils.FontUtils.hexToColor;

/**
 * Registry-driven style applier for Swing components.
 */
public class StyleApplier {
    private final String[] components;
    private final Map<Class<? extends JComponent>, BiConsumer<JComponent, StyleAttributes>> styleHandlers = new ConcurrentHashMap<>();

    public StyleApplier(Engine engine) {
        this.components = engine.getEngineData().getStyles();
        registerStyleHandler(JComponent.class, this::applyBaseStyle);
    }

    @SuppressWarnings("unchecked")
    public <T extends JComponent> void registerStyleHandler(Class<T> componentClass, BiConsumer<T, StyleAttributes> handler) {
        Objects.requireNonNull(componentClass, "componentClass");
        Objects.requireNonNull(handler, "handler");
        styleHandlers.put(componentClass, (BiConsumer<JComponent, StyleAttributes>) handler);
    }

    public void applyStyle(JComponent component, StyleAttributes styleAttributes) {
        if (component == null || styleAttributes == null) {
            return;
        }
        resolveHandler(component).accept(component, styleAttributes);
    }

    private BiConsumer<JComponent, StyleAttributes> resolveHandler(JComponent component) {
        BiConsumer<JComponent, StyleAttributes> exact = styleHandlers.get(component.getClass());
        if (exact != null) {
            return exact;
        }
        return styleHandlers.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(component.getClass()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(this::applyBaseStyle);
    }

    private void applyBaseStyle(JComponent component, StyleAttributes style) {
        component.setOpaque(style.isOpaque());
        component.setForeground(hexToColor(style.getColor()));

        Color background = hexToColor(style.getBackground());
        if (!style.hasTransparentBackground()) {
            component.setBackground(background);
        }
        if (style.getBorderColor() != null && style.getBorderRadius() == 0) {
            component.setBorder(BorderFactory.createLineBorder(hexToColor(style.getBorderColor())));
        }
    }

    public String[] getComponents() {
        return components == null ? new String[0] : components.clone();
    }
}
