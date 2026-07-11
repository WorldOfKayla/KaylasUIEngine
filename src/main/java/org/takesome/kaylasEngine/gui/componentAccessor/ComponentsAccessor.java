package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.binding.ComponentFieldBinding;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.index.ComponentGraphIndexing;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.state.ComponentIndexState;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.support.ComponentIds;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.value.ComponentFormValueCollection;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final ComponentAccessSource source;
    private final String rootPanelId;
    private final List<Class<? extends JComponent>> componentTypes;
    private final ComponentAccessorOptions options;
    private final ComponentValueRegistry valueRegistry;
    private final ComponentGraphIndexing graphIndexer;
    private final ComponentFormValueCollection formValueCollector;
    private final ComponentFieldBinding fieldBinder = ComponentFieldBinding.create();
    private final ComponentIndexState indexState;

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
        this.indexState = ComponentIndexState.create(this.rootPanelId);
        this.componentTypes = normalizeComponentTypes(componentTypes);
        this.options = Objects.requireNonNullElseGet(options, ComponentAccessorOptions::defaults);
        this.valueRegistry = Objects.requireNonNullElseGet(
                valueRegistry,
                ComponentValueRegistry::defaults
        );
        this.graphIndexer = ComponentGraphIndexing.create(source, this.options, this.componentTypes);
        this.formValueCollector = ComponentFormValueCollection.create(
                source,
                this.options,
                this.valueRegistry
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
        ComponentGraphIndexing.Index nextIndex = graphIndexer.build(rootPanelId);

        indexState.replace(nextIndex);

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
        return indexState.revision();
    }

    /**
     * Creates an immutable snapshot of the current index.
     *
     * @return component access snapshot.
     */
    public ComponentAccessSnapshot snapshot() {
        return indexState.snapshot();
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
        return indexState.findMetadata(id);
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
        return indexState.findComponent(id);
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
        return findComponent(ComponentIds.qualify(scopeId, localId), expectedType);
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
        return requireComponent(ComponentIds.qualify(scopeId, localId), expectedType);
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
        return formValueCollector.collectAll(
                metadataSnapshot().values(),
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

    private void bindAnnotatedFields() {
        fieldBinder.bind(this, ComponentsAccessor.class, componentSnapshot());
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
        return formValueCollector.collectPanel(
                panelId,
                metadataSnapshot().values(),
                valueMode,
                unsupportedPolicy
        );
    }

    private void refreshBeforeLookup() {
        if (options.autoRefreshOnLookup()) {
            refresh();
        }
    }

    private Map<String, JComponent> componentSnapshot() {
        return indexState.components();
    }

    private Map<String, IndexedComponent> metadataSnapshot() {
        return indexState.metadata();
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

}
