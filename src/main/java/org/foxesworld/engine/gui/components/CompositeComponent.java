package org.foxesworld.engine.gui.components;

import org.foxesworld.engine.Engine;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stable composite Swing component used as a container for other engine components.
 *
 * <p>The default constructor keeps the historical vertical layout used by subclasses such as
 * CompositeSlider and FileSelector. Generic JSON-driven composite components should use
 * {@link LayoutMode#ABSOLUTE} so child bounds from JSON are respected.</p>
 */
public class CompositeComponent extends JComponent {
    public enum LayoutMode {
        VERTICAL,
        HORIZONTAL,
        ABSOLUTE,
        FLOW
    }

    protected ComponentFactory componentFactory;

    private final List<JComponent> subComponents = new CopyOnWriteArrayList<>();
    private final Map<String, JComponent> subComponentsByName = new ConcurrentHashMap<>();
    private final List<String> componentTypes = new CopyOnWriteArrayList<>();
    private final Map<String, String> componentStyles = new ConcurrentHashMap<>();
    private volatile Object value = "";
    private volatile ComponentAttributes.LayoutConfig layoutConfig;
    private final LayoutMode layoutMode;

    public CompositeComponent() {
        this(LayoutMode.VERTICAL);
    }

    public CompositeComponent(LayoutMode layoutMode) {
        this.layoutMode = layoutMode == null ? LayoutMode.VERTICAL : layoutMode;
        setDoubleBuffered(true);
        setOpaque(false);
        configureLayoutManager();
    }

    private void configureLayoutManager() {
        switch (layoutMode) {
            case ABSOLUTE -> setLayout(null);
            case HORIZONTAL -> setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            case FLOW -> setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
            case VERTICAL -> setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
    }

    protected void initComponents(Map<String, String> components) {
        if (components == null || components.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> compInst : components.entrySet()) {
            if (compInst.getKey() == null || compInst.getKey().isBlank()) {
                continue;
            }
            componentTypes.add(compInst.getKey());
            if (compInst.getValue() != null) {
                componentStyles.put(compInst.getKey(), compInst.getValue());
            }
        }
    }

    public void addSubComponent(JComponent component) {
        addSubComponent(component, null);
    }

    public void addSubComponent(JComponent component, ComponentAttributes.ComponentConfig config) {
        if (component == null) {
            Engine.LOGGER.warn("CompositeComponent ignored null child component");
            return;
        }
        if (config != null) {
            applyLayoutConfig(component, config);
        } else if (layoutMode == LayoutMode.ABSOLUTE) {
            ensureAbsoluteBounds(component);
        }

        subComponents.add(component);
        if (component.getName() != null && !component.getName().isBlank()) {
            subComponentsByName.put(component.getName(), component);
        }
        add(component);
        updateView();
    }

    private void ensureAbsoluteBounds(JComponent component) {
        Rectangle currentBounds = component.getBounds();
        if (currentBounds.width > 0 && currentBounds.height > 0) {
            return;
        }
        Dimension preferredSize = component.getPreferredSize();
        int width = Math.max(1, preferredSize != null ? preferredSize.width : currentBounds.width);
        int height = Math.max(1, preferredSize != null ? preferredSize.height : currentBounds.height);
        component.setBounds(currentBounds.x, currentBounds.y, width, height);
    }

    public boolean removeSubComponent(JComponent component) {
        if (component == null) {
            return false;
        }
        boolean removed = subComponents.remove(component);
        if (component.getName() != null) {
            subComponentsByName.remove(component.getName());
        }
        remove(component);
        updateView();
        return removed;
    }

    public List<JComponent> getSubComponents() {
        return Collections.unmodifiableList(new ArrayList<>(subComponents));
    }

    public JComponent getSubComponent(String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return null;
        }
        return subComponentsByName.get(componentId);
    }

    public int getSubComponentCount() {
        return subComponents.size();
    }

    public void clearSubComponents() {
        subComponents.clear();
        subComponentsByName.clear();
        removeAll();
        updateView();
    }

    private void updateView() {
        revalidate();
        repaint();
    }

    public JPanel createPanel(LayoutManager layout, boolean opaque) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(opaque);
        return panel;
    }

    public void setLayoutConfig(ComponentAttributes.LayoutConfig config) {
        this.layoutConfig = config;
    }

    public ComponentAttributes.LayoutConfig getLayoutConfig() {
        return this.layoutConfig;
    }

    public ComponentAttributes.ComponentConfig getLayoutConfigFor(String componentType) {
        if (layoutConfig == null || componentType == null) {
            return null;
        }
        return layoutConfig.getForType(componentType);
    }

    protected void applyLayoutConfig(JComponent component, ComponentAttributes.ComponentConfig config) {
        if (component == null || config == null) {
            return;
        }
        int width = Math.max(0, config.getWidth());
        int height = Math.max(0, config.getHeight());
        component.setBounds(config.getX(), config.getY(), width, height);
        if (width > 0 && height > 0) {
            Dimension size = new Dimension(width, height);
            component.setPreferredSize(size);
            component.setMinimumSize(size);
        }
    }

    protected List<String> getComponentTypes() {
        return Collections.unmodifiableList(new ArrayList<>(componentTypes));
    }

    protected Map<String, String> getComponentStyles() {
        return Collections.unmodifiableMap(componentStyles);
    }

    public void setValue(String value) {
        setValue((Object) value);
    }

    public void setValue(Object value) {
        this.value = value == null ? "" : value;
    }

    public Object getValue() {
        return value == null ? "" : value;
    }

    public LayoutMode getLayoutMode() {
        return layoutMode;
    }

    @Override
    public Dimension getPreferredSize() {
        if (layoutMode != LayoutMode.ABSOLUTE) {
            return super.getPreferredSize();
        }
        Rectangle bounds = calculateChildrenBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return super.getPreferredSize();
        }
        return new Dimension(bounds.x + bounds.width, bounds.y + bounds.height);
    }

    private Rectangle calculateChildrenBounds() {
        Rectangle result = new Rectangle();
        for (JComponent component : subComponents) {
            result = result.union(component.getBounds());
        }
        return result;
    }
}
