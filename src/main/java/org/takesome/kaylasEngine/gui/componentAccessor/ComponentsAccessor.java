package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.constructor.ConstructedCompositeComponent;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Indexes, binds, queries, and extracts values from KaylasUI/Swing component graphs.
 *
 * <p>The accessor is a live view over a {@link ComponentAccessSource}. It can be refreshed after a
 * screen rebuild, traverses logical child panels with cycle protection, indexes named descendants of
 * constructor composites, injects fields annotated with {@link Component}, and delegates semantic
 * value handling to an extensible {@link ComponentValueRegistry}.</p>
 *
 * <h2>Compatibility constructor</h2>
 * <pre>{@code
 * ComponentsAccessor accessor = new ComponentsAccessor(
 *         guiBuilder,
 *         "settings",
 *         List.of(TextField.class, Checkbox.class)
 * );
 * }</pre>
 *
 * <h2>Scoped constructor lookup</h2>
 * <pre>{@code
 * Slider slider = accessor.requireLocal("volume", "slider", Slider.class);
 * }</pre>
 *
 * <h2>Strict native-value mode</h2>
 * <pre>{@code
 * ComponentAccessorOptions options = ComponentAccessorOptions.builder()
 *         .duplicatePolicy(DuplicateComponentPolicy.FAIL)
 *         .unsupportedValuePolicy(UnsupportedValuePolicy.FAIL)
 *         .valueMode(ComponentValueMode.NATIVE)
 *         .build();
 * }</pre>
 */
@SuppressWarnings("unused")
public class ComponentsAccessor {
    private static final String ATTRIBUTES_PROPERTY = "kaylas.ui.attributes";

    private final ComponentAccessSource source;
    private final String rootPanelId;
    private final List<Class<? extends JComponent>> componentTypes;
    private final ComponentAccessorOptions options;
    private final ComponentValueRegistry valueRegistry;
    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();

    private final Map<String, JComponent> componentMap = new LinkedHashMap<>();
    private final Map<String, List<JComponent>> panelComponentMap = new LinkedHashMap<>();
    private final Map<String, IndexedComponent> metadataMap = new LinkedHashMap<>();
    private long revision;

    /**
     * Creates an accessor with compatibility-oriented defaults and the standard value registry.
     *
     * @param guiBuilder GUI builder that owns the panel graph.
     * @param panelId root panel id.
     * @param componentTypes selected component classes used by panel views and form collection.
     */
    public ComponentsAccessor(GuiBuilder guiBuilder,
                              String panelId,
                              List<Class<?>> componentTypes) {
        this(
                ComponentAccessSource.from(guiBuilder),
                panelId,
                componentTypes,
                ComponentAccessorOptions.defaults(),
                ComponentValueRegistry.defaults()
        );
    }

    /**
     * Creates an accessor with custom traversal and value policies.
     *
     * @param guiBuilder GUI builder that owns the panel graph.
     * @param panelId root panel id.
     * @param componentTypes selected component classes.
     * @param options accessor options.
     */
    public ComponentsAccessor(GuiBuilder guiBuilder,
                              String panelId,
                              List<Class<?>> componentTypes,
                              ComponentAccessorOptions options) {
        this(
                ComponentAccessSource.from(guiBuilder),
                panelId,
                componentTypes,
                options,
                ComponentValueRegistry.defaults()
        );
    }

    /**
     * Creates an accessor with a custom value registry.
     *
     * @param guiBuilder GUI builder that owns the panel graph.
     * @param panelId root panel id.
     * @param componentTypes selected component classes.
     * @param options accessor options.
     * @param valueRegistry semantic value registry.
     */
    public ComponentsAccessor(GuiBuilder guiBuilder,
                              String panelId,
                              List<Class<?>> componentTypes,
                              ComponentAccessorOptions options,
                              ComponentValueRegistry valueRegistry) {
        this(
                ComponentAccessSource.from(guiBuilder),
                panelId,
                componentTypes,
                options,
                valueRegistry
        );
    }

    /**
     * Creates an accessor over an arbitrary component source.
     *
     * <p>An empty {@code componentTypes} list selects every {@link JComponent}. All named controls
     * are still placed in the global lookup index; the type list controls panel views and form-value
     * eligibility.</p>
     *
     * @param source component graph source.
     * @param panelId root panel id.
     * @param componentTypes selected component classes.
     * @param options accessor options.
     * @param valueRegistry semantic value registry.
     */
    public ComponentsAccessor(ComponentAccessSource source,
                              String panelId,
                              List<Class<?>> componentTypes,
                              ComponentAccessorOptions options,
                              ComponentValueRegistry valueRegistry) {
        this.source = Objects.requireNonNull(source, "source");
        if (panelId == null || panelId.isBlank()) {
            throw new IllegalArgumentException("panelId must not be blank");
        }
        this.rootPanelId = panelId.trim();
        this.componentTypes = normalizeComponentTypes(componentTypes);
        this.options = Objects.requireNonNullElseGet(options, ComponentAccessorOptions::defaults);
        this.valueRegistry = Objects.requireNonNullElseGet(
                valueRegistry,
                ComponentValueRegistry::defaults
        );
        refresh();
    }

    /**
     * Rebuilds the entire index from the current source state and rebinds annotated fields.
     *
     * @return immutable snapshot of the new index.
     * @throws ComponentAccessException when strict traversal, duplicate, or binding validation fails.
     */
    public final ComponentAccessSnapshot refresh() {
        Map<String, JComponent> nextComponents = new LinkedHashMap<>();
        Map<String, List<JComponent>> nextPanelComponents = new LinkedHashMap<>();
        Map<String, IndexedComponent> nextMetadata = new LinkedHashMap<>();
        Set<String> visitedPanels = new LinkedHashSet<>();
        Set<JComponent> visitedComponents = Collections.newSetFromMap(new IdentityHashMap<>());

        boolean rootExists = source.findPanel(rootPanelId).isPresent()
                || !source.components(rootPanelId).isEmpty()
                || !source.childPanels(rootPanelId).isEmpty();
        if (!rootExists && options.failOnMissingRootPanel()) {
            throw new ComponentAccessException("Root panel not found: " + rootPanelId);
        }

        collectPanel(
                rootPanelId,
                0,
                visitedPanels,
                visitedComponents,
                nextComponents,
                nextPanelComponents,
                nextMetadata
        );

        indexLock.writeLock().lock();
        try {
            componentMap.clear();
            componentMap.putAll(nextComponents);
            panelComponentMap.clear();
            nextPanelComponents.forEach((panel, components) ->
                    panelComponentMap.put(panel, List.copyOf(components))
            );
            metadataMap.clear();
            metadataMap.putAll(nextMetadata);
            revision++;
        } finally {
            indexLock.writeLock().unlock();
        }

        if (options.injectAnnotatedFields()) {
            bindAnnotatedFields();
        }
        return snapshot();
    }

    /**
     * Re-runs annotation binding against the current index without rebuilding traversal state.
     *
     * <p>This method is retained as the protected compatibility hook used by earlier subclasses.</p>
     */
    protected void initComponents() {
        bindAnnotatedFields();
    }

    /**
     * Returns the root panel.
     *
     * @return root panel, or {@code null} when absent.
     */
    public JPanel getPanel() {
        return source.findPanel(rootPanelId).orElse(null);
    }

    /**
     * Returns the root panel id used by this accessor.
     *
     * @return root panel id.
     */
    public String getRootPanelId() {
        return rootPanelId;
    }

    /** @return component graph source. */
    public ComponentAccessSource getSource() {
        return source;
    }

    /** @return immutable accessor options. */
    public ComponentAccessorOptions getOptions() {
        return options;
    }

    /** @return mutable per-accessor value registry. */
    public ComponentValueRegistry getValueRegistry() {
        return valueRegistry;
    }

    /**
     * Returns the current index revision.
     *
     * @return revision incremented after every successful refresh.
     */
    public long getRevision() {
        indexLock.readLock().lock();
        try {
            return revision;
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * Creates an immutable snapshot of the current index.
     *
     * @return component access snapshot.
     */
    public ComponentAccessSnapshot snapshot() {
        indexLock.readLock().lock();
        try {
            return new ComponentAccessSnapshot(
                    revision,
                    Instant.now(),
                    rootPanelId,
                    componentMap,
                    panelComponentMap,
                    metadataMap
            );
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * Returns all named indexed controls, including nested constructor nodes.
     *
     * @return immutable id-to-component snapshot.
     */
    public Map<String, JComponent> getComponentMap() {
        return snapshot().components();
    }

    /**
     * Returns only controls matching the configured component types.
     *
     * @return immutable selected component map.
     */
    public Map<String, JComponent> getSelectedComponentMap() {
        Map<String, JComponent> selected = new LinkedHashMap<>();
        metadataSnapshot().values().stream()
                .filter(IndexedComponent::selected)
                .forEach(metadata -> selected.put(metadata.id(), metadata.component()));
        return Collections.unmodifiableMap(selected);
    }

    /**
     * Returns direct selected controls grouped by logical panel id.
     *
     * @return immutable panel-component map.
     */
    public Map<String, List<JComponent>> getPanelComponentMap() {
        return snapshot().panelComponents();
    }

    /**
     * Returns direct selected controls registered for a panel.
     *
     * @param panelId panel id.
     * @return immutable component list.
     */
    public List<JComponent> getComponentsForPanel(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return List.of();
        }
        return snapshot().panelComponents().getOrDefault(panelId.trim(), List.of());
    }

    /**
     * Returns every indexed descendant associated with a logical panel.
     *
     * @param panelId panel id.
     * @return immutable metadata list ordered by traversal.
     */
    public List<IndexedComponent> getIndexedComponentsForPanel(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return List.of();
        }
        String normalized = panelId.trim();
        return metadataSnapshot().values().stream()
                .filter(metadata -> normalized.equals(metadata.panelId()))
                .toList();
    }

    /**
     * Returns detailed metadata for one component id.
     *
     * @param id component id.
     * @return metadata when indexed.
     */
    public Optional<IndexedComponent> findIndexedComponent(String id) {
        refreshBeforeLookup();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        indexLock.readLock().lock();
        try {
            return Optional.ofNullable(metadataMap.get(id.trim()));
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * Returns a component by id, including nested constructor nodes.
     *
     * @param id component id.
     * @return component or {@code null} when absent.
     */
    public JComponent getComponent(String id) {
        return findComponent(id).orElse(null);
    }

    /**
     * Finds a component by id.
     *
     * @param id component id.
     * @return component when present.
     */
    public Optional<JComponent> findComponent(String id) {
        refreshBeforeLookup();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        indexLock.readLock().lock();
        try {
            return Optional.ofNullable(componentMap.get(id.trim()));
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * Finds and type-checks a component.
     *
     * @param id component id.
     * @param expectedType expected Swing class.
     * @param <T> component type.
     * @return typed component when present and compatible.
     */
    public <T extends JComponent> Optional<T> findComponent(String id, Class<T> expectedType) {
        Objects.requireNonNull(expectedType, "expectedType");
        return findComponent(id)
                .filter(expectedType::isInstance)
                .map(expectedType::cast);
    }

    /**
     * Requires a component by id and expected type.
     *
     * @param id component id.
     * @param expectedType expected Swing class.
     * @param <T> component type.
     * @return typed component.
     * @throws ComponentAccessException when absent or incompatible.
     */
    public <T extends JComponent> T requireComponent(String id, Class<T> expectedType) {
        JComponent component = findComponent(id).orElseThrow(() ->
                new ComponentAccessException("Component not found: " + id)
        );
        if (!expectedType.isInstance(component)) {
            throw new ComponentAccessException(
                    "Component '" + id + "' has type " + component.getClass().getName()
                            + ", expected " + expectedType.getName()
            );
        }
        return expectedType.cast(component);
    }

    /**
     * Finds a local node inside a constructor composite scope.
     *
     * @param scopeId composite instance id.
     * @param localId local node id.
     * @param expectedType expected Swing class.
     * @param <T> component type.
     * @return typed local node when present.
     */
    public <T extends JComponent> Optional<T> findLocal(String scopeId,
                                                        String localId,
                                                        Class<T> expectedType) {
        return findComponent(qualify(scopeId, localId), expectedType);
    }

    /**
     * Requires a local node inside a constructor composite scope.
     *
     * @param scopeId composite instance id.
     * @param localId local node id.
     * @param expectedType expected Swing class.
     * @param <T> component type.
     * @return typed local node.
     */
    public <T extends JComponent> T requireLocal(String scopeId,
                                                 String localId,
                                                 Class<T> expectedType) {
        return requireComponent(qualify(scopeId, localId), expectedType);
    }

    /**
     * Finds all indexed components assignable to a class.
     *
     * @param expectedType component class.
     * @param <T> component type.
     * @return immutable typed list.
     */
    public <T extends JComponent> List<T> findByType(Class<T> expectedType) {
        Objects.requireNonNull(expectedType, "expectedType");
        refreshBeforeLookup();
        return componentSnapshot().values().stream()
                .filter(expectedType::isInstance)
                .map(expectedType::cast)
                .toList();
    }

    /**
     * Finds controls created from a catalog component type or alias.
     *
     * @param componentType canonical type or alias.
     * @return immutable component list.
     */
    public List<JComponent> findByCatalogType(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            return List.of();
        }
        refreshBeforeLookup();
        String requested = componentType.trim();
        String canonical = source.catalog()
                .flatMap(catalog -> catalog.find(requested))
                .map(definition -> definition.type())
                .orElse(requested);
        return metadataSnapshot().values().stream()
                .filter(metadata -> metadata.catalogType() != null)
                .filter(metadata -> metadata.catalogType().equalsIgnoreCase(canonical))
                .map(IndexedComponent::component)
                .toList();
    }

    /**
     * Reads a component's native semantic value.
     *
     * @param id component id.
     * @return native adapter value.
     */
    public Object readValue(String id) {
        return valueRegistry.read(requireComponent(id, JComponent.class));
    }

    /**
     * Reads a component value when both component and adapter are available.
     *
     * @param id component id.
     * @return optional native value; a native {@code null} is represented as empty.
     */
    public Optional<Object> findValue(String id) {
        Optional<JComponent> component = findComponent(id);
        if (component.isEmpty() || !valueRegistry.supports(component.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(valueRegistry.read(component.get()));
    }

    /**
     * Writes a semantic value through the resolved adapter.
     *
     * @param id component id.
     * @param value value to apply.
     */
    public void writeValue(String id, Object value) {
        valueRegistry.write(requireComponent(id, JComponent.class), value);
    }

    /**
     * Returns current form values for every eligible component in the indexed root graph.
     *
     * @return immutable form map using the configured {@link ComponentValueMode}.
     */
    public Map<String, Object> getFormCredentials() {
        Collection<IndexedComponent> eligible = metadataSnapshot().values().stream()
                .filter(IndexedComponent::formEligible)
                .toList();
        return collectValues(
                eligible,
                options.valueMode(),
                options.unsupportedValuePolicy()
        );
    }

    /**
     * Collects current form values for a panel and all logical child panels.
     *
     * @param panelId root panel id.
     * @return immutable form map using configured value policies.
     */
    public Map<String, Object> collectFormCredentialsForPanel(String panelId) {
        return collectFormCredentialsForPanel(
                panelId,
                options.valueMode(),
                options.unsupportedValuePolicy()
        );
    }

    /**
     * Collects native form values independently of the accessor's default representation mode.
     *
     * @param panelId root panel id.
     * @return immutable native-value map.
     */
    public Map<String, Object> collectNativeFormValuesForPanel(String panelId) {
        return collectFormCredentialsForPanel(
                panelId,
                ComponentValueMode.NATIVE,
                options.unsupportedValuePolicy()
        );
    }

    /**
     * Collects string form values independently of the accessor's default representation mode.
     *
     * @param panelId root panel id.
     * @return immutable string-value map typed as objects for API compatibility.
     */
    public Map<String, Object> collectStringFormValuesForPanel(String panelId) {
        return collectFormCredentialsForPanel(
                panelId,
                ComponentValueMode.STRING,
                options.unsupportedValuePolicy()
        );
    }

    /**
     * Returns selected component classes configured for panel views and forms.
     *
     * @return immutable class list; empty means every {@link JComponent}.
     */
    public List<Class<? extends JComponent>> getComponentTypes() {
        return componentTypes;
    }

    private void collectPanel(String panelId,
                              int depth,
                              Set<String> visitedPanels,
                              Set<JComponent> visitedComponents,
                              Map<String, JComponent> nextComponents,
                              Map<String, List<JComponent>> nextPanelComponents,
                              Map<String, IndexedComponent> nextMetadata) {
        guardDepth(depth, "panel", panelId);
        if (!visitedPanels.add(panelId)) {
            Engine.LOGGER.warn("ComponentAccessor skipped cyclic panel reference: {}", panelId);
            return;
        }

        List<JComponent> selectedRoots = new ArrayList<>();
        Set<JComponent> selectedRootIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JComponent root : source.components(panelId)) {
            if (root == null) {
                continue;
            }
            boolean selected = matchesSelectedType(root);
            if (selected && selectedRootIdentity.add(root)) {
                selectedRoots.add(root);
            }
            visitComponent(
                    root,
                    panelId,
                    null,
                    0,
                    false,
                    visitedComponents,
                    nextComponents,
                    nextMetadata
            );
        }
        nextPanelComponents.put(panelId, List.copyOf(selectedRoots));

        for (String childPanelId : source.childPanels(panelId)) {
            if (childPanelId == null || childPanelId.isBlank()) {
                continue;
            }
            collectPanel(
                    childPanelId.trim(),
                    depth + 1,
                    visitedPanels,
                    visitedComponents,
                    nextComponents,
                    nextPanelComponents,
                    nextMetadata
            );
        }
    }

    private void visitComponent(JComponent component,
                                String panelId,
                                String parentComponentId,
                                int depth,
                                boolean nested,
                                Set<JComponent> visitedComponents,
                                Map<String, JComponent> nextComponents,
                                Map<String, IndexedComponent> nextMetadata) {
        guardDepth(depth, "component", component.getName());
        if (!visitedComponents.add(component)) {
            return;
        }

        String id = normalize(component.getName());
        boolean selected = matchesSelectedType(component);
        boolean formEligible = selected && (!nested || options.includeNestedValuesInForms());
        String nextParentId = parentComponentId;

        if (id != null) {
            IndexedComponent metadata = new IndexedComponent(
                    id,
                    component,
                    panelId,
                    parentComponentId,
                    depth,
                    nested,
                    selected,
                    formEligible,
                    catalogType(component),
                    propertyString(component, ConstructedCompositeComponent.SCOPE_PROPERTY),
                    propertyString(component, ConstructedCompositeComponent.LOCAL_ID_PROPERTY)
            );
            registerComponent(id, component, metadata, nextComponents, nextMetadata);
            nextParentId = id;
        }

        if (!options.traverseNestedComponents()) {
            return;
        }
        for (java.awt.Component child : component.getComponents()) {
            if (child instanceof JComponent childComponent) {
                visitComponent(
                        childComponent,
                        panelId,
                        nextParentId,
                        depth + 1,
                        true,
                        visitedComponents,
                        nextComponents,
                        nextMetadata
                );
            }
        }
    }

    private void registerComponent(String id,
                                   JComponent component,
                                   IndexedComponent metadata,
                                   Map<String, JComponent> nextComponents,
                                   Map<String, IndexedComponent> nextMetadata) {
        JComponent existing = nextComponents.get(id);
        if (existing == null || existing == component) {
            nextComponents.put(id, component);
            nextMetadata.put(id, metadata);
            return;
        }

        switch (options.duplicatePolicy()) {
            case KEEP_FIRST -> Engine.LOGGER.warn(
                    "ComponentAccessor kept first duplicate id '{}': existing={}, ignored={}",
                    id,
                    existing.getClass().getName(),
                    component.getClass().getName()
            );
            case REPLACE -> {
                Engine.LOGGER.warn(
                        "ComponentAccessor replaced duplicate id '{}': old={}, new={}",
                        id,
                        existing.getClass().getName(),
                        component.getClass().getName()
                );
                nextComponents.put(id, component);
                nextMetadata.put(id, metadata);
            }
            case FAIL -> throw new ComponentAccessException(
                    "Duplicate component id '" + id + "': "
                            + existing.getClass().getName() + " and "
                            + component.getClass().getName()
            );
        }
    }

    private void bindAnnotatedFields() {
        Map<String, JComponent> components = componentSnapshot();
        for (Class<?> current = getClass();
             current != null && current != ComponentsAccessor.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Component binding = field.getAnnotation(Component.class);
                if (binding != null) {
                    bindField(field, binding, components);
                }
            }
        }
    }

    private void bindField(Field field,
                           Component binding,
                           Map<String, JComponent> components) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new ComponentAccessException(
                    "@Component cannot be applied to static field: " + field
            );
        }
        if (Modifier.isFinal(field.getModifiers())) {
            throw new ComponentAccessException(
                    "@Component cannot inject final field: " + field
            );
        }

        String componentId = bindingId(field, binding);
        JComponent component = components.get(componentId);
        boolean optionalField = Optional.class.equals(field.getType());

        if (component == null && binding.required()) {
            throw new ComponentAccessException(
                    "Required component '" + componentId + "' not found for field "
                            + field.getDeclaringClass().getName() + "." + field.getName()
            );
        }

        Object injectionValue;
        if (optionalField) {
            validateOptionalType(field, component, componentId);
            injectionValue = Optional.ofNullable(component);
        } else {
            if (component == null) {
                return;
            }
            if (!field.getType().isInstance(component)) {
                throw new ComponentAccessException(
                        "Component '" + componentId + "' has type "
                                + component.getClass().getName() + ", but field "
                                + field.getDeclaringClass().getName() + "." + field.getName()
                                + " expects " + field.getType().getName()
                );
            }
            injectionValue = component;
        }

        boolean accessible = field.canAccess(this);
        try {
            if (!accessible && !field.trySetAccessible()) {
                throw new ComponentAccessException("Cannot access component field: " + field);
            }
            field.set(this, injectionValue);
        } catch (IllegalAccessException error) {
            throw new ComponentAccessException(
                    "Unable to inject component '" + componentId + "' into field " + field,
                    error
            );
        } finally {
            if (!accessible) {
                try {
                    field.setAccessible(false);
                } catch (RuntimeException ignored) {
                    // Access was already used successfully; restoration is best effort.
                }
            }
        }
    }

    private void validateOptionalType(Field field,
                                      JComponent component,
                                      String componentId) {
        if (component == null) {
            return;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class<?> expectedType)) {
            return;
        }
        if (!expectedType.isInstance(component)) {
            throw new ComponentAccessException(
                    "Component '" + componentId + "' has type "
                            + component.getClass().getName() + ", but Optional field " + field
                            + " expects " + expectedType.getName()
            );
        }
    }

    private Map<String, Object> collectFormCredentialsForPanel(
            String panelId,
            ComponentValueMode valueMode,
            UnsupportedValuePolicy unsupportedPolicy
    ) {
        if (panelId == null || panelId.isBlank()) {
            return Map.of();
        }
        refreshBeforeLookup();
        Set<String> panelIds = new LinkedHashSet<>();
        collectPanelIds(panelId.trim(), 0, panelIds);
        Collection<IndexedComponent> components = metadataSnapshot().values().stream()
                .filter(IndexedComponent::formEligible)
                .filter(metadata -> panelIds.contains(metadata.panelId()))
                .toList();
        return collectValues(components, valueMode, unsupportedPolicy);
    }

    private Map<String, Object> collectValues(Collection<IndexedComponent> components,
                                              ComponentValueMode valueMode,
                                              UnsupportedValuePolicy unsupportedPolicy) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (IndexedComponent metadata : components) {
            FormValue formValue = formValue(metadata.component(), valueMode, unsupportedPolicy);
            if (formValue.include()) {
                values.put(metadata.id(), formValue.value());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private FormValue formValue(JComponent component,
                                ComponentValueMode valueMode,
                                UnsupportedValuePolicy unsupportedPolicy) {
        if (!valueRegistry.supports(component)) {
            return switch (unsupportedPolicy) {
                case SKIP -> FormValue.skip();
                case EMPTY_STRING -> FormValue.include("");
                case FAIL -> throw new ComponentAccessException(
                        "No form value adapter for component " + component.getClass().getName()
                                + " with id '" + component.getName() + "'"
                );
            };
        }

        Object nativeValue = valueRegistry.read(component);
        if (valueMode == ComponentValueMode.STRING) {
            return FormValue.include(nativeValue == null ? "" : String.valueOf(nativeValue));
        }
        return FormValue.include(nativeValue);
    }

    private void collectPanelIds(String panelId,
                                 int depth,
                                 Set<String> result) {
        guardDepth(depth, "panel", panelId);
        if (!result.add(panelId)) {
            return;
        }
        for (String child : source.childPanels(panelId)) {
            if (child != null && !child.isBlank()) {
                collectPanelIds(child.trim(), depth + 1, result);
            }
        }
    }

    private boolean matchesSelectedType(JComponent component) {
        return componentTypes.isEmpty()
                || componentTypes.stream().anyMatch(type -> type.isInstance(component));
    }

    private String catalogType(JComponent component) {
        Object attributes = component.getClientProperty(ATTRIBUTES_PROPERTY);
        if (attributes instanceof ComponentAttributes componentAttributes) {
            return normalize(componentAttributes.getComponentType());
        }
        return propertyString(component, ConstructedCompositeComponent.DEFINITION_PROPERTY);
    }

    private String bindingId(Field field, Component binding) {
        String scope = normalize(binding.scope());
        String localId = normalize(binding.localId());
        if (localId != null) {
            if (scope == null) {
                throw new ComponentAccessException(
                        "@Component localId requires a non-blank scope on field " + field
                );
            }
            return qualify(scope, localId);
        }

        String value = normalize(binding.value());
        String baseId = value == null ? field.getName() : value;
        if (scope == null || baseId.equals(scope) || baseId.startsWith(scope + ".")) {
            return baseId;
        }
        return qualify(scope, baseId);
    }

    private static String qualify(String scopeId, String localId) {
        String scope = normalize(scopeId);
        String local = normalize(localId);
        if (scope == null || local == null) {
            throw new IllegalArgumentException("scopeId and localId must not be blank");
        }
        if (local.equals(scope) || local.startsWith(scope + ".")) {
            return local;
        }
        return scope + "." + local;
    }

    private static String propertyString(JComponent component, String property) {
        Object value = component.getClientProperty(property);
        return value == null ? null : normalize(String.valueOf(value));
    }

    private void refreshBeforeLookup() {
        if (options.autoRefreshOnLookup()) {
            refresh();
        }
    }

    private Map<String, JComponent> componentSnapshot() {
        indexLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(componentMap));
        } finally {
            indexLock.readLock().unlock();
        }
    }

    private Map<String, IndexedComponent> metadataSnapshot() {
        indexLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(metadataMap));
        } finally {
            indexLock.readLock().unlock();
        }
    }

    private void guardDepth(int depth, String graphType, String identifier) {
        if (depth > options.maximumTraversalDepth()) {
            throw new ComponentAccessException(
                    "Maximum " + graphType + " traversal depth exceeded at '" + identifier + "'"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Class<? extends JComponent>> normalizeComponentTypes(
            List<Class<?>> componentTypes
    ) {
        if (componentTypes == null || componentTypes.isEmpty()) {
            return List.of();
        }
        List<Class<? extends JComponent>> result = new ArrayList<>();
        for (Class<?> componentType : componentTypes) {
            if (componentType == null || !JComponent.class.isAssignableFrom(componentType)) {
                throw new IllegalArgumentException(
                        "Component type must extend JComponent: " + componentType
                );
            }
            result.add((Class<? extends JComponent>) componentType);
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record FormValue(boolean include, Object value) {
        private static FormValue include(Object value) {
            return new FormValue(true, value);
        }

        private static FormValue skip() {
            return new FormValue(false, null);
        }
    }
}
