package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;
import org.takesome.kaylasEngine.gui.components.checkbox.Checkbox;
import org.takesome.kaylasEngine.gui.components.compositeSlider.CompositeSlider;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.fileSelector.FileSelector;
import org.takesome.kaylasEngine.gui.components.passfield.PassField;
import org.takesome.kaylasEngine.gui.components.slider.Slider;
import org.takesome.kaylasEngine.gui.components.textfield.TextField;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class ComponentsAccessor {

    /**
     * Helper for accessing and extracting values from GUI components built by {@link GuiBuilder}.
     *
     * <p>
     * Responsibilities:
     * <ul>
     *     <li>Collect components belonging to a given root panel (recursively).</li>
     *     <li>Provide lookups by component id (name).</li>
     *     <li>Inject components into fields annotated with a component-binding annotation (via reflection).</li>
     *     <li>Extract "form" values from common component types (text fields, checkboxes, sliders, etc.).</li>
     * </ul>
     * </p>
     *
     * <p>
     * This class assumes component names (IDs) are set (via {@code JComponent.setName}) and that
     * the {@code componentTypes} list contains the classes that should be considered when collecting
     * components. Field injection expects fields in subclasses to be annotated with a {@code @Component}
     * annotation (custom) whose {@code value()} is the component ID to inject (or empty to use the field name).
     * </p>
     *
     * <p>Use it like a lightweight, reflection-based form binder. Yes — reflection. Bring coffee.</p>
     */
    private final GuiBuilder guiBuilder;
    private final String panelId;
    private final List<Class<?>> componentTypes;
    private final Map<String, JComponent> componentMap = new HashMap<>();
    private final Map<String, List<JComponent>> panelComponentMap = new HashMap<>();
    private final Map<String, Object> formCredentials = new HashMap<>();

    /**
     * Creates a new ComponentsAccessor.
     *
     * @param guiBuilder     non-null {@link GuiBuilder} instance that holds panels/components maps.
     * @param panelId        non-null id of the root panel to collect components from.
     * @param componentTypes list of component classes to consider when collecting (for example {@code TextField.class}).
     * @throws NullPointerException if any argument is null.
     */
    public ComponentsAccessor(GuiBuilder guiBuilder, String panelId, List<Class<?>> componentTypes) {
        this.guiBuilder = Objects.requireNonNull(guiBuilder, "guiBuilder must not be null");
        this.panelId = Objects.requireNonNull(panelId, "panelId must not be null");
        this.componentTypes = Objects.requireNonNull(componentTypes, "componentTypes must not be null");
        collectComponents(panelId);
        this.initComponents();
    }


    /**
     * Map of handlers used to extract string values from known component types.
     *
     * <p>
     * Keys are concrete component classes; values are functions that return a string representation
     * of the component's current value. Unknown component classes default to an empty string.
     * </p>
     */
    private final Map<Class<?>, Function<JComponent, String>> valueExtractors = Map.of(
            TextField.class, c -> ((TextField) c).getText(),
            PassField.class, c -> new String(((PassField) c).getPassword()),
            Checkbox.class, c -> String.valueOf(((Checkbox) c).isSelected()),
            Slider.class, c -> String.valueOf(((Slider) c).getValue()),
            Combobox.class, c -> String.valueOf(((Combobox) c).getSelectedIndex()),
            FileSelector.class, c -> ((FileSelector)c).getValue(),
            CompositeSlider.class, c -> String.valueOf(((CompositeSlider) c).getValue()),
            CompositeComponent.class, c -> String.valueOf(((CompositeComponent) c).getValue())
    );

    /**
     * Scans declared fields of this class for the {@code @Component} annotation and injects
     * matching {@link JComponent} instances into annotated fields.
     *
     *
     * Injection rules:
     * <ul>
     *     <li>If annotation's {@code value()} is non-empty, it is used as the component id.</li>
     *     <li>Otherwise the Java field name is used as the component id.</li>
     * </ul>
     *
     *
     * @throws RuntimeException         if a field cannot be set due to access restrictions.
     * @throws IllegalArgumentException if a required component id cannot be found in the collected components.
     */
    protected void initComponents() {
        Class<?> clazz = this.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Component.class)) {
                Component annotation = field.getAnnotation(Component.class);
                String componentId = annotation.value().isEmpty()
                        ? field.getName()
                        : annotation.value();

                JComponent component = componentMap.get(componentId);
                if (component != null) {
                    try {
                        boolean wasAccessible = field.canAccess(this);
                        field.setAccessible(true);
                        field.set(this, component);
                        field.setAccessible(wasAccessible);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(
                                "Error injecting component '" + componentId + "' into field " + field.getName(), e
                        );
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Component with ID '" + componentId + "' not found for field " + field.getName()
                    );
                }
            }
        }
    }

    /**
     * Returns the root {@link JPanel} referenced by {@code panelId} from the {@link GuiBuilder}.
     *
     * @return the panel instance, or {@code null} if not found.
     */
    public JPanel getPanel(){
        return this.guiBuilder.getPanelsMap().get(panelId);
    }

    /**
     * Collects components starting from the specified panel id and walking child panels recursively.
     *
     * <p>
     * This method populates internal {@link #panelComponentMap} and invokes {@link #processComponent}
     * for each collected component that matches {@link #isComponentType(Class)}.
     * </p>
     *
     * @param panelId id of the panel to collect components from.
     */
    private void collectComponents(String panelId) {
        Optional.ofNullable(guiBuilder.getComponentsMap().get(panelId))
                .ifPresent(components -> {
                    List<JComponent> panelComponents = components.stream()
                            .filter(this::isComponentType)
                            .peek(this::processComponent)
                            .collect(Collectors.toList());
                    panelComponentMap.put(panelId, panelComponents);
                });

        Optional.ofNullable(guiBuilder.getChildParentMap().get(panelId))
                .ifPresent(childPanels -> childPanels.forEach(this::collectComponents));
    }

    /**
     * Processes a single component: stores it in {@link #componentMap} by name, stores its current
     * form value in {@link #formCredentials} and recursively processes inner panel components.
     *
     * @param component component to process; silently returns if {@code null}.
     */
    private void processComponent(JComponent component) {
        if (component == null) {
            return;
        }

        String name = component.getName();
        if (name != null && !name.isEmpty()) {
            componentMap.put(name, component);
            formCredentials.put(name, getValue(component));

            if (component instanceof org.takesome.kaylasEngine.gui.components.panel.Panel panel) {
                Arrays.stream(panel.getComponents())
                        .filter(JComponent.class::isInstance)
                        .map(JComponent.class::cast)
                        .forEach(this::processComponent);
            }
        }
    }

    /**
     * Tests whether the provided component is an instance of any of the classes listed in {@link #componentTypes}.
     *
     * @param component candidate component.
     * @return {@code true} if the component is of an accepted type.
     */
    private boolean isComponentType(JComponent component) {
        return componentTypes.stream().anyMatch(type -> type.isInstance(component));
    }

    /**
     * Extracts a string value from known component types using {@link #valueExtractors}.
     *
     * <p>
     * If the component's exact runtime class is not found in the map, an empty string is returned.
     * </p>
     *
     * @param component component to extract the value from.
     * @return string representation of the component's value, or empty string for unsupported types.
     */
    private String getValue(JComponent component) {
        Function<JComponent, String> extractor = valueExtractors.get(component.getClass());
        if (extractor != null) {
            return extractor.apply(component);
        }
        return valueExtractors.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(component))
                .findFirst()
                .map(entry -> entry.getValue().apply(component))
                .orElse("");
    }

    /**
     * Returns an unmodifiable map of collected components keyed by their names (IDs).
     *
     * @return map of component id -> {@link JComponent}.
     */
    public Map<String, JComponent> getComponentMap() {
        return Collections.unmodifiableMap(componentMap);
    }

    /**
     * Returns an unmodifiable map of panel id -> list of components for that panel.
     *
     * @return panel -> components map.
     */
    public Map<String, List<JComponent>> getPanelComponentMap() {
        return Collections.unmodifiableMap(panelComponentMap);
    }

    /**
     * Returns the current snapshot of extracted form credentials (component id -> value).
     *
     * @return map of component id -> value object.
     */
    public Map<String, Object> getFormCredentials() {
        return formCredentials;
    }

    /**
     * Returns a collected component by its id.
     *
     * @param id component id (name).
     * @return the matching {@link JComponent} or {@code null} if not present.
     */
    public JComponent getComponent(String id) {
        return componentMap.get(id);
    }

    /**
     * Returns components registered for the specified panel.
     *
     * @param panelId id of the panel.
     * @return list of components for the panel or an empty list if none exist.
     */
    public List<JComponent> getComponentsForPanel(String panelId) {
        return panelComponentMap.getOrDefault(panelId, Collections.emptyList());
    }

    /**
     * Collects and returns form credentials (component id -> string value) for the specified panel and its children.
     *
     * <p>
     * The method traverses the panel hierarchy starting from {@code panelId} and uses {@link #getValue(JComponent)}
     * to extract values from supported component types.
     * </p>
     *
     * @param panelId root panel id to collect credentials from.
     * @return map of component id -> extracted value (as Object).
     */
    public Map<String, Object> collectFormCredentialsForPanel(String panelId) {
        Map<String, Object> credentials = new HashMap<>();
        collectFormCredentials(panelId, credentials);
        return credentials;
    }

    /**
     * Internal recursive helper for {@link #collectFormCredentialsForPanel(String)}.
     *
     * @param panelId     current panel id.
     * @param credentials accumulator map to populate.
     */
    private void collectFormCredentials(String panelId, Map<String, Object> credentials) {
        Optional.ofNullable(panelComponentMap.get(panelId))
                .ifPresent(components -> components.forEach(component -> {
                    String name = component.getName();
                    if (name != null && !name.isEmpty()) {
                        credentials.put(name, getValue(component));
                    }
                }));

        Optional.ofNullable(guiBuilder.getChildParentMap().get(panelId))
                .ifPresent(childPanels -> childPanels.forEach(childPanelId -> collectFormCredentials(childPanelId, credentials)));
    }
}
