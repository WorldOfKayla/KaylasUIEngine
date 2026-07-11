package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.constructor.ComponentConstructor;
import org.takesome.kaylasEngine.gui.config.ComponentConfigGroupRegistry;
import org.takesome.kaylasEngine.gui.config.ComponentConfigResolver;
import org.takesome.kaylasEngine.gui.scripting.LuaUiScriptEngine;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.utils.IconUtils;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Extensible, context-based factory for KaylasUI Swing components.
 *
 * <p>Every creation receives an immutable {@link ComponentCreationContext}; nested component
 * creation restores its parent context automatically. This removes the shared mutable style and
 * descriptor state that previously made asynchronous and composite creation non-deterministic.</p>
 */
public class ComponentFactory extends JComponent {
    private final Engine engine;
    private final LanguageProvider langProvider;
    private final IconUtils iconUtils;
    private final LuaUiScriptEngine luaUiScriptEngine;
    private final ComponentCatalog componentCatalog = new ComponentCatalog();
    private final ComponentConfigGroupRegistry configGroupRegistry = new ComponentConfigGroupRegistry();
    private final ComponentConfigResolver componentConfigResolver = new ComponentConfigResolver(configGroupRegistry);
    private final ComponentConstructor componentConstructor;
    private final Map<String, Function<ComponentAttributes, JComponent>> legacyRegistry = new ConcurrentHashMap<>();
    private final ComponentCreationState creationState = new ComponentCreationState();
    private final ComponentAttributeApplier attributeApplier;
    private final CompositeComponentBuilder compositeBuilder;
    private final BuiltInComponentCreators builtInCreators;

    private volatile ComponentFactoryListener componentFactoryListener;

    public ComponentFactory(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.langProvider = engine.getLANG();
        this.iconUtils = new IconUtils(engine);
        this.luaUiScriptEngine = new LuaUiScriptEngine(engine);
        this.componentConstructor = new ComponentConstructor(this, componentCatalog);
        this.attributeApplier = new ComponentAttributeApplier(engine, langProvider, luaUiScriptEngine);
        this.compositeBuilder = new CompositeComponentBuilder(this);
        this.builtInCreators = new BuiltInComponentCreators(this);
        registerBuiltIns();
    }

    private void registerBuiltIns() {
        builtInCreators.register();
    }

    /** Compatibility registration API. New integrations should prefer {@link #registerDefinition}. */
    public void registerComponent(String type, Function<ComponentAttributes, JComponent> creator) {
        registerComponent(type, ComponentKind.BASIC, creator);
    }

    public void registerComponent(String type,
                                  ComponentKind kind,
                                  Function<ComponentAttributes, JComponent> creator) {
        Objects.requireNonNull(creator, "creator");
        String canonicalType = requireType(type, "component type");
        ComponentDefinition<JComponent> definition = ComponentDefinition.<JComponent>builder(canonicalType)
                .kind(Objects.requireNonNull(kind, "kind"))
                .applyBaseStyle(false)
                .creator(context -> creator.apply(context.attributes()))
                .build();
        registerDefinition(definition);
        legacyRegistry.put(canonicalType, creator);
    }

    public void registerDefinition(AbstractComponentDefinition<? extends JComponent> definition) {
        Objects.requireNonNull(definition, "definition");
        AbstractComponentDefinition<? extends JComponent> previous = componentCatalog.register(definition);
        if (previous != null) {
            Engine.LOGGER.warn("Component definition '{}' was replaced.", definition.type());
        }
        Engine.LOGGER.info(
                "    - Registered {} component: {}",
                definition.kind().name().toLowerCase(Locale.ROOT),
                definition.type()
        );
    }

    public void registerAlias(String alias, String componentType) {
        componentCatalog.registerAlias(alias, componentType);
    }

    public boolean unregisterComponent(String componentType) {
        Optional<AbstractComponentDefinition<? extends JComponent>> definition =
                findComponentDefinition(componentType);
        if (definition.isPresent()) {
            legacyRegistry.remove(definition.get().type());
        }
        return componentCatalog.unregister(componentType);
    }

    /** Returns any basic or composite definition. */
    public Optional<AbstractComponentDefinition<? extends JComponent>> findComponentDefinition(
            String componentType
    ) {
        return componentCatalog.find(componentType);
    }

    /**
     * Legacy basic-definition lookup retained for source compatibility with 2.0 launchers.
     */
    public Optional<ComponentDefinition<? extends JComponent>> findDefinition(String componentType) {
        return findComponentDefinition(componentType)
                .filter(ComponentDefinition.class::isInstance)
                .map(definition -> (ComponentDefinition<? extends JComponent>) definition);
    }

    public JComponent createComponent(ComponentAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        ComponentAttributes resolvedAttributes = componentConfigResolver.resolve(attributes);
        resolvedAttributes.validateForCreation();

        AbstractComponentDefinition<? extends JComponent> definition =
                findComponentDefinition(resolvedAttributes.getComponentType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported component type: " + resolvedAttributes.getComponentType()
                                + ". Registered types: " + getRegisteredComponentTypes()
                ));

        List<String> styleChain = resolvedAttributes.getStyleChain();
        if (styleChain.isEmpty() && definition.defaultStyle() != null) {
            styleChain = List.of(definition.defaultStyle());
        }
        StyleAttributes resolvedStyle = engine.getStyleProvider().resolveStyle(
                definition.type(),
                styleChain,
                resolvedAttributes.getStyleOverrides()
        );
        ComponentCreationContext context = new ComponentCreationContext(
                this,
                definition,
                resolvedAttributes,
                resolvedStyle,
                resolvedAttributes.getBounds(),
                styleChain
        );

        creationState.enter(context);
        try {
            ComponentFactoryListener listener = componentFactoryListener;
            if (listener != null) {
                listener.onComponentCreation(resolvedAttributes);
            }
            JComponent component = definition.create(context);
            attributeApplier.apply(component, context);
            return component;
        } finally {
            creationState.exit();
        }
    }

    /**
     * Creates a Swing component on the EDT and completes the future with the result.
     * Descriptor parsing and other non-Swing work should remain on background executors.
     */
    public CompletableFuture<JComponent> createComponentAsync(ComponentAttributes attributes) {
        CompletableFuture<JComponent> future = new CompletableFuture<>();
        Runnable creation = () -> {
            try {
                future.complete(createComponent(attributes));
            } catch (Throwable error) {
                Engine.LOGGER.error(
                        "Error creating component: {}",
                        attributes == null ? "null" : attributes.getComponentType(),
                        error
                );
                future.completeExceptionally(error);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            creation.run();
        } else {
            SwingUtilities.invokeLater(creation);
        }
        return future;
    }

    public JComponent createTabs(ComponentAttributes attributes) {
        return compositeBuilder.createTabs(attributes);
    }

    public JComponent createCompositeComponent(ComponentAttributes attributes) {
        return compositeBuilder.createComposite(attributes);
    }

    public Optional<ComponentCreationContext> getCurrentCreationContext() {
        return creationState.currentContext();
    }

    public StyleAttributes getStyle() {
        return creationState.currentStyle();
    }

    public <T> T withStyle(StyleAttributes style, Supplier<T> action) {
        return creationState.withStyle(style, action);
    }

    public void withStyle(StyleAttributes style, Runnable action) {
        Objects.requireNonNull(action, "action");
        creationState.withStyle(style, () -> {
            action.run();
            return null;
        });
    }

    public Rectangle getBounds() {
        return creationState.currentBounds();
    }

    /** Sets the fallback used only when no component is currently being created. */
    @Deprecated(since = "2.0.0-AURELIA", forRemoval = false)
    public void setStyle(StyleAttributes style) {
        creationState.setFallbackStyle(style);
    }

    public ComponentAttributes getComponentAttribute() {
        return creationState.currentAttributes();
    }

    public Map<String, Function<ComponentAttributes, JComponent>> getComponentRegistry() {
        return Collections.unmodifiableMap(legacyRegistry);
    }

    public Map<String, ComponentDefinition<? extends JComponent>> getComponentDefinitions() {
        Map<String, ComponentDefinition<? extends JComponent>> definitions = new LinkedHashMap<>();
        componentCatalog.definitions().values().stream()
                .filter(ComponentDefinition.class::isInstance)
                .map(definition -> (ComponentDefinition<? extends JComponent>) definition)
                .forEach(definition -> definitions.put(definition.type(), definition));
        return Collections.unmodifiableMap(definitions);
    }

    public Map<String, AbstractComponentDefinition<? extends JComponent>> getAllComponentDefinitions() {
        return componentCatalog.definitions();
    }

    public List<String> getRegisteredComponentTypes() {
        return componentCatalog.types();
    }

    public List<String> getRegisteredComponentTypes(ComponentKind kind) {
        return componentCatalog.types(kind);
    }

    public ComponentConfigGroupRegistry getConfigGroupRegistry() {
        return configGroupRegistry;
    }

    public ComponentConfigResolver getComponentConfigResolver() {
        return componentConfigResolver;
    }

    public void activateConfigGroup(String group) {
        configGroupRegistry.activateGroup(group);
    }

    public void deactivateConfigGroup(String group) {
        configGroupRegistry.deactivateGroup(group);
    }

    public ComponentCatalog getComponentCatalog() {
        return componentCatalog;
    }

    public ComponentConstructor getComponentConstructor() {
        return componentConstructor;
    }

    public Map<String, Map<String, StyleAttributes>> getComponentStyles() {
        return engine.getStyleProvider().getElementStyles();
    }

    public Engine getEngine() {
        return engine;
    }

    public LanguageProvider getLangProvider() {
        return langProvider;
    }

    public IconUtils getIconUtils() {
        return iconUtils;
    }

    public LuaUiScriptEngine getLuaUiScriptEngine() {
        return luaUiScriptEngine;
    }

    public void setComponentFactoryListener(ComponentFactoryListener componentFactoryListener) {
        this.componentFactoryListener = componentFactoryListener;
    }

    private static String requireType(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }


}
