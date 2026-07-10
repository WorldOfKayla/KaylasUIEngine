package org.takesome.kaylasEngine.gui.components;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.foxesworld.cfgProvider.ConfigTypeConverter;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.button.ButtonStyle;
import org.takesome.kaylasEngine.gui.components.checkbox.Checkbox;
import org.takesome.kaylasEngine.gui.components.checkbox.CheckboxStyle;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.combobox.ComboboxStyle;
import org.takesome.kaylasEngine.gui.components.compositeSlider.CompositeSlider;
import org.takesome.kaylasEngine.gui.components.constructor.ComponentConstructor;
import org.takesome.kaylasEngine.gui.components.fileSelector.FileSelector;
import org.takesome.kaylasEngine.gui.components.fileSelector.SelectionMode;
import org.takesome.kaylasEngine.gui.components.label.Label;
import org.takesome.kaylasEngine.gui.components.label.LabelStyle;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButton;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButtonStyle;
import org.takesome.kaylasEngine.gui.components.passfield.PassField;
import org.takesome.kaylasEngine.gui.components.passfield.PassFieldStyle;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBarStyle;
import org.takesome.kaylasEngine.gui.components.slider.Slider;
import org.takesome.kaylasEngine.gui.components.slider.TexturedSliderUI;
import org.takesome.kaylasEngine.gui.components.spinner.Spinner;
import org.takesome.kaylasEngine.gui.components.sprite.SpriteAnimation;
import org.takesome.kaylasEngine.gui.components.textArea.AreaStyle;
import org.takesome.kaylasEngine.gui.components.textArea.TextArea;
import org.takesome.kaylasEngine.gui.components.textfield.TextField;
import org.takesome.kaylasEngine.gui.components.textfield.TextFieldStyle;
import org.takesome.kaylasEngine.gui.components.utils.tooltip.CustomTooltip;
import org.takesome.kaylasEngine.gui.components.utils.tooltip.TooltipAttributes;
import org.takesome.kaylasEngine.gui.scripting.LuaUiScriptEngine;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.utils.IconUtils;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/**
 * Extensible, context-based factory for KaylasUI Swing components.
 *
 * <p>Every creation receives an immutable {@link ComponentCreationContext}; nested component
 * creation restores its parent context automatically. This removes the shared mutable style and
 * descriptor state that previously made asynchronous and composite creation non-deterministic.</p>
 */
public class ComponentFactory extends JComponent {
    private static final String STYLE_PROPERTY = "kaylas.ui.style";
    private static final String STYLE_CHAIN_PROPERTY = "kaylas.ui.styleChain";
    private static final String ATTRIBUTES_PROPERTY = "kaylas.ui.attributes";

    private final Engine engine;
    private final LanguageProvider langProvider;
    private final IconUtils iconUtils;
    private final LuaUiScriptEngine luaUiScriptEngine;
    private final Map<String, TooltipAttributes> tooltipCache = new ConcurrentHashMap<>();
    private final ComponentCatalog componentCatalog = new ComponentCatalog();
    private final ComponentConstructor componentConstructor;
    private final Map<String, Function<ComponentAttributes, JComponent>> legacyRegistry = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<ComponentCreationContext>> creationStack =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<Deque<StyleAttributes>> scopedStyles =
            ThreadLocal.withInitial(ArrayDeque::new);

    private volatile StyleAttributes fallbackStyle = StyleAttributes.defaults("default");
    private volatile ComponentAttributes lastComponentAttribute;
    private volatile ComponentFactoryListener componentFactoryListener;

    public ComponentFactory(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.langProvider = engine.getLANG();
        this.iconUtils = new IconUtils(engine);
        this.luaUiScriptEngine = new LuaUiScriptEngine(engine);
        this.componentConstructor = new ComponentConstructor(this, componentCatalog);
        registerBuiltIns();
    }

    private void registerBuiltIns() {
        registerComponent("label", this::createLabel);
        registerComponent("progressBar", this::createProgressBar);
        registerComponent("button", this::createButton);
        registerComponent("textArea", this::createTextArea);
        registerComponent("checkBox", this::createCheckbox);
        registerComponent("textField", this::createTextField);
        registerComponent("spriteImage", this::createSpriteImage);
        registerComponent("passField", this::createPassField);
        registerComponent("spinner", this::createSpinner);
        registerComponent("multiButton", this::createMultiButton);
        registerComponent("combobox", this::createCombobox);
        registerComponent("dropBox", this::createCombobox);
        registerComponent("slider", this::createSlider);
        registerComponent("compositeSlider", ComponentKind.COMPOSITE, this::createCompositeSlider);
        registerComponent("fileSelector", ComponentKind.COMPOSITE, this::createFileSelector);
        registerComponent("compositeComponent", ComponentKind.COMPOSITE, this::createCompositeComponent);

        registerAlias("checkbox", "checkBox");
        registerAlias("check-box", "checkBox");
        registerAlias("textfield", "textField");
        registerAlias("text-field", "textField");
        registerAlias("textarea", "textArea");
        registerAlias("text-area", "textArea");
        registerAlias("progress", "progressBar");
        registerAlias("progress-bar", "progressBar");
        registerAlias("sprite", "spriteImage");
        registerAlias("dropdown", "dropBox");
        registerAlias("select", "combobox");
        registerAlias("composite", "compositeComponent");
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
        attributes.validateForCreation();

        AbstractComponentDefinition<? extends JComponent> definition =
                findComponentDefinition(attributes.getComponentType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported component type: " + attributes.getComponentType()
                                + ". Registered types: " + getRegisteredComponentTypes()
                ));

        List<String> styleChain = attributes.getStyleChain();
        if (styleChain.isEmpty() && definition.defaultStyle() != null) {
            styleChain = List.of(definition.defaultStyle());
        }
        StyleAttributes resolvedStyle = engine.getStyleProvider().resolveStyle(
                definition.type(),
                styleChain,
                attributes.getStyleOverrides()
        );
        ComponentCreationContext context = new ComponentCreationContext(
                this,
                definition,
                attributes,
                resolvedStyle,
                attributes.getBounds(),
                styleChain
        );

        Deque<ComponentCreationContext> stack = creationStack.get();
        if (definition.kind() == ComponentKind.COMPOSITE
                && stack.stream().anyMatch(parent ->
                parent.definition().type().equalsIgnoreCase(definition.type()))) {
            throw new IllegalStateException(
                    "Recursive composite construction detected for type '"
                            + definition.type() + "'"
            );
        }
        stack.push(context);
        lastComponentAttribute = attributes;
        try {
            ComponentFactoryListener listener = componentFactoryListener;
            if (listener != null) {
                listener.onComponentCreation(attributes);
            }
            JComponent component = definition.create(context);
            applyCommonAttributes(component, context);
            return component;
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                creationStack.remove();
            }
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

    public JComponent createCompositeComponent(ComponentAttributes attributes) {
        CompositeComponent composite = new CompositeComponent(resolveCompositeLayout(attributes));
        composite.setLayoutConfig(attributes.getLayoutConfig());
        composite.setValue(attributes.getInitialValue());

        List<ComponentAttributes> children = attributes.getChildComponents();
        if (children == null || children.isEmpty()) {
            Engine.LOGGER.debug("CompositeComponent '{}' has no child components.", attributes.getComponentId());
            return composite;
        }

        for (ComponentAttributes child : children) {
            if (child == null) {
                Engine.LOGGER.warn("CompositeComponent '{}' ignored a null child descriptor.", attributes.getComponentId());
                continue;
            }
            inheritCompositeDefaults(attributes, child);
            JComponent childComponent = createComponent(child);
            composite.addSubComponent(childComponent, composite.getLayoutConfigFor(child.getComponentType()));
        }
        return composite;
    }

    private void inheritCompositeDefaults(ComponentAttributes parent, ComponentAttributes child) {
        if (child.getInitialValue() == null && parent.getInitialValue() != null) {
            child.setInitialValue(parent.getInitialValue());
        }
        if (child.getStyleChain().isEmpty()) {
            String targetedStyle = parent.getStyles().get(child.getComponentType());
            if (targetedStyle == null) {
                targetedStyle = parent.getStyles().get(normalizeType(child.getComponentType()));
            }
            if (targetedStyle != null && !targetedStyle.isBlank()) {
                child.setComponentStyle(targetedStyle);
            }
        }
    }

    private void applyCommonAttributes(JComponent component, ComponentCreationContext context) {
        ComponentAttributes attributes = context.attributes();
        StyleAttributes style = context.style();

        component.setName(attributes.getComponentId());
        component.setBounds(context.bounds());
        component.setEnabled(attributes.isEnabled());
        component.setVisible(attributes.isVisible());
        component.setOpaque(attributes.hasOpaque() ? attributes.isOpaque() : style.isOpaque());
        if (context.definition().applyBaseStyle()) {
            applyResolvedBaseStyle(component, attributes, style);
        }
        if (attributes.hasFocusable()) {
            component.setFocusable(attributes.isFocusable());
        }
        if (attributes.hasDoubleBuffered()) {
            component.setDoubleBuffered(attributes.isDoubleBuffered());
        }
        applyCursor(component, attributes.getCursor());
        applyAccessibility(component, attributes);
        attributes.getProperties().forEach(component::putClientProperty);

        component.putClientProperty(STYLE_PROPERTY, style);
        component.putClientProperty(STYLE_CHAIN_PROPERTY, context.styleChain());
        component.putClientProperty(ATTRIBUTES_PROPERTY, attributes);

        if (attributes.getToolTip() != null && !attributes.getToolTip().isBlank()) {
            initializeTooltip(component, attributes);
        }
        luaUiScriptEngine.bind(component, attributes);
    }

    private void applyResolvedBaseStyle(JComponent component,
                                        ComponentAttributes attributes,
                                        StyleAttributes style) {
        component.setForeground(hexToColor(valueOr(attributes.getColor(), style.getColor())));

        String background = valueOr(attributes.getBackground(), style.getBackground());
        if (background != null
                && !background.isBlank()
                && !"transparent".equalsIgnoreCase(background)) {
            component.setBackground(hexToColor(background));
        }

        component.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), style.getFont()),
                attributes.getFontSize() > 0 ? attributes.getFontSize() : style.getFontSize(),
                valueOr(attributes.getFontStyle(), style.getFontStyle())
        ));
    }

    private void applyAccessibility(JComponent component, ComponentAttributes attributes) {
        if (attributes.getAccessibleName() != null && !attributes.getAccessibleName().isBlank()) {
            component.getAccessibleContext().setAccessibleName(attributes.getAccessibleName());
        }
        if (attributes.getAccessibleDescription() != null && !attributes.getAccessibleDescription().isBlank()) {
            component.getAccessibleContext().setAccessibleDescription(attributes.getAccessibleDescription());
        }
    }

    private void applyCursor(JComponent component, String cursorName) {
        if (cursorName == null || cursorName.isBlank()) {
            return;
        }
        int cursorType = switch (cursorName.trim().toLowerCase(Locale.ROOT)) {
            case "hand", "pointer" -> Cursor.HAND_CURSOR;
            case "text" -> Cursor.TEXT_CURSOR;
            case "wait", "busy" -> Cursor.WAIT_CURSOR;
            case "move" -> Cursor.MOVE_CURSOR;
            case "crosshair" -> Cursor.CROSSHAIR_CURSOR;
            case "resize-e", "e-resize" -> Cursor.E_RESIZE_CURSOR;
            case "resize-w", "w-resize" -> Cursor.W_RESIZE_CURSOR;
            case "resize-n", "n-resize" -> Cursor.N_RESIZE_CURSOR;
            case "resize-s", "s-resize" -> Cursor.S_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
        component.setCursor(Cursor.getPredefinedCursor(cursorType));
    }

    private void initializeTooltip(JComponent component, ComponentAttributes attributes) {
        String tooltipStyle = valueOr(attributes.getTooltipStyle(), "default");
        TooltipAttributes tooltipAttributes = tooltipCache.computeIfAbsent(tooltipStyle, this::loadTooltipAttributes);
        if (tooltipAttributes == null) {
            return;
        }
        CustomTooltip tooltip = new CustomTooltip(
                hexToColor(tooltipAttributes.getBgColor()),
                hexToColor(tooltipAttributes.getTextColor()),
                tooltipAttributes.getBorderRadius(),
                engine.getFONTUTILS().getFont(tooltipAttributes.getFont(), tooltipAttributes.getFontSize())
        );
        tooltip.attachToComponent(component, langProvider.getString(attributes.getToolTip()), 2000);
    }

    private TooltipAttributes loadTooltipAttributes(String styleName) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/styles/tooltip.json");
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(stream))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has(styleName)) {
                Engine.LOGGER.warn("Tooltip style '{}' not found.", styleName);
                return null;
            }
            return new Gson().fromJson(root.get(styleName), TooltipAttributes.class);
        } catch (Exception error) {
            Engine.LOGGER.error("Failed to load tooltip style '{}'.", styleName, error);
            return null;
        }
    }

    private JComponent createLabel(ComponentAttributes attributes) {
        Label label = new Label(this);
        new LabelStyle(this).apply(label);
        label.setIcon(iconUtils.getIcon(attributes));
        label.setText(localizedTextWithInitial(attributes));
        label.setForeground(hexToColor(valueOr(attributes.getColor(), getStyle().getColor())));
        label.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), getStyle().getFontStyle())
        ));
        return label;
    }

    private JComponent createProgressBar(ComponentAttributes attributes) {
        ProgressBar progressBar = new ProgressBar();
        new ProgressBarStyle(this, attributes).apply(progressBar);
        return progressBar;
    }

    private JComponent createButton(ComponentAttributes attributes) {
        String text = localizedText(attributes.getLocaleKey());
        Button button = attributes.getImageIcon() == null
                ? new Button(this, text)
                : new Button(this, iconUtils.getIcon(attributes), text);
        new ButtonStyle(this).apply(button);
        button.setActionCommand(attributes.getComponentId());
        button.addActionListener(engine);
        installButtonShortcut(button, attributes);
        return button;
    }

    private void installButtonShortcut(Button button, ComponentAttributes attributes) {
        if (attributes.getKeyCode() == null || attributes.getKeyCode().isBlank()) {
            return;
        }
        KeyStroke keyStroke = KeyStroke.getKeyStroke(attributes.getKeyCode());
        if (keyStroke == null) {
            Engine.LOGGER.warn("Invalid key stroke '{}' for component '{}'.", attributes.getKeyCode(), attributes.getComponentId());
            return;
        }
        String actionKey = valueOr(attributes.getComponentId(), "button.shortcut." + System.identityHashCode(button));
        button.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                button.ButtonClick();
                button.doClick();
                button.setPressed(false);
            }
        });
        button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey);
    }

    private JComponent createTextArea(ComponentAttributes attributes) {
        TextArea textArea = new TextArea(this);
        textArea.setLineWrap(attributes.isLineWrap());
        new AreaStyle(this).apply(textArea);
        textArea.setText(localizedTextWithInitial(attributes));
        textArea.setForeground(hexToColor(valueOr(attributes.getColor(), getStyle().getColor())));
        textArea.setEditable(attributes.isEditable());
        textArea.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), getStyle().getFontStyle())
        ));
        return textArea;
    }

    private JComponent createCheckbox(ComponentAttributes attributes) {
        Checkbox checkbox = new Checkbox(this, localizedText(attributes.getLocaleKey()));
        new CheckboxStyle(this).apply(checkbox);
        checkbox.setSelected(Boolean.parseBoolean(String.valueOf(attributes.getInitialValue())));
        installCheckboxShortcut(checkbox, attributes);
        return checkbox;
    }

    private void installCheckboxShortcut(Checkbox checkbox, ComponentAttributes attributes) {
        if (attributes.getKeyCode() == null || attributes.getKeyCode().isBlank()) {
            return;
        }
        KeyStroke keyStroke = KeyStroke.getKeyStroke(attributes.getKeyCode());
        if (keyStroke == null) {
            Engine.LOGGER.warn("Invalid key stroke '{}' for component '{}'.", attributes.getKeyCode(), attributes.getComponentId());
            return;
        }
        String actionKey = valueOr(attributes.getComponentId(), "checkbox.shortcut." + System.identityHashCode(checkbox));
        checkbox.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                checkbox.toggleCheckbox();
                checkbox.doClick();
            }
        });
        checkbox.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey);
    }

    private JComponent createTextField(ComponentAttributes attributes) {
        TextField textField = new TextField(this);
        new TextFieldStyle(this).apply(textField);
        if (attributes.getInitialValue() != null) {
            textField.setText(String.valueOf(attributes.getInitialValue()));
        }
        textField.setEditable(attributes.isEditable());
        textField.setActionCommand(attributes.getComponentId());
        textField.addActionListener(engine);
        return textField;
    }

    private JComponent createSpriteImage(ComponentAttributes attributes) {
        return new SpriteAnimation(this);
    }

    private JComponent createPassField(ComponentAttributes attributes) {
        PassField passField = new PassField(this, localizedText(attributes.getLocaleKey()));
        new PassFieldStyle(this).apply(passField);
        passField.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), getStyle().getFontStyle())
        ));
        passField.setEditable(attributes.isEditable());
        passField.setActionCommand(attributes.getComponentId());
        return passField;
    }

    private JComponent createSpinner(ComponentAttributes attributes) {
        int minimum = attributes.getMinValue();
        int maximum = attributes.getMaxValue() > minimum ? attributes.getMaxValue() : minimum + 100;
        int initial = Math.max(minimum, Math.min(maximum, intValue(attributes.getInitialValue(), minimum)));
        int step = attributes.getStepSize() > 0
                ? attributes.getStepSize()
                : Math.max(1, attributes.getMajorSpacing());
        Spinner spinner = new Spinner(initial, minimum, maximum, step);
        if (spinner.getSpinnerListener() != null) {
            spinner.init();
        }
        return spinner;
    }

    private JComponent createMultiButton(ComponentAttributes attributes) {
        MultiButton multiButton = new MultiButton(this);
        new MultiButtonStyle(this, attributes).apply(multiButton);
        multiButton.setActionCommand(attributes.getComponentId());
        multiButton.addActionListener(engine);
        return multiButton;
    }

    private JComponent createCombobox(ComponentAttributes attributes) {
        Combobox combobox = new Combobox(this, attributes.getBounds().y);
        new ComboboxStyle(this).apply(combobox);
        return combobox;
    }

    private JComponent createSlider(ComponentAttributes attributes) {
        Slider slider = new Slider(this);
        int minimum = attributes.getMinValue();
        int maximum = attributes.getMaxValue() > minimum ? attributes.getMaxValue() : minimum + 100;
        int initial = Math.max(minimum, Math.min(maximum, intValue(attributes.getInitialValue(), minimum)));
        int range = Math.max(1, maximum - minimum);

        slider.setMinimum(minimum);
        slider.setMaximum(maximum);
        slider.setValue(initial);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setMajorTickSpacing(attributes.getMajorSpacing() > 0
                ? attributes.getMajorSpacing()
                : Math.max(1, range / 5));
        slider.setMinorTickSpacing(attributes.getMinorSpacing() > 0
                ? attributes.getMinorSpacing()
                : Math.max(1, range / 10));
        slider.setUI(new TexturedSliderUI(this, slider, getStyle()));
        return slider;
    }

    private JComponent createCompositeSlider(ComponentAttributes attributes) {
        CompositeSlider slider = new CompositeSlider(this);
        if (attributes.getInitialValue() != null) {
            slider.setValue(intValue(attributes.getInitialValue(), attributes.getMinValue()));
        }
        return slider;
    }

    private JComponent createFileSelector(ComponentAttributes attributes) {
        FileSelector selector = new FileSelector(this, selectionMode(attributes.getSelectionMode()));
        if (attributes.getInitialValue() != null) {
            selector.setValue(String.valueOf(attributes.getInitialValue()));
        }
        return selector;
    }

    private CompositeComponent.LayoutMode resolveCompositeLayout(ComponentAttributes attributes) {
        String mode = valueOr(attributes.getAlignment(), "absolute").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "vertical", "y", "box-y" -> CompositeComponent.LayoutMode.VERTICAL;
            case "horizontal", "x", "box-x" -> CompositeComponent.LayoutMode.HORIZONTAL;
            case "flow" -> CompositeComponent.LayoutMode.FLOW;
            default -> CompositeComponent.LayoutMode.ABSOLUTE;
        };
    }

    private String localizedTextWithInitial(ComponentAttributes attributes) {
        String localized = localizedText(attributes.getLocaleKey());
        Object initialValue = attributes.getInitialValue();
        return initialValue == null || String.valueOf(initialValue).isBlank()
                ? localized
                : localized + " " + initialValue;
    }

    private String localizedText(String localeKey) {
        if (localeKey == null || localeKey.isBlank()) {
            return "";
        }
        return langProvider.getString(localeKey);
    }

    private int effectiveFontSize(ComponentAttributes attributes) {
        return attributes.getFontSize() > 0 ? attributes.getFontSize() : getStyle().getFontSize();
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (Integer) ConfigTypeConverter.convertToDeclaredType(value, int.class);
        } catch (RuntimeException error) {
            Engine.LOGGER.warn("Invalid numeric component value '{}'; using {}.", value, fallback);
            return fallback;
        }
    }

    private SelectionMode selectionMode(String mode) {
        SelectionMode resolved = SelectionMode.from(mode);
        if (mode != null && !mode.isBlank() && resolved == SelectionMode.FILES_ONLY
                && !SelectionMode.isFileAlias(mode)) {
            Engine.LOGGER.warn("Invalid file selection mode '{}'; using FILES_ONLY.", mode);
        }
        return resolved;
    }

    public Optional<ComponentCreationContext> getCurrentCreationContext() {
        Deque<ComponentCreationContext> stack = creationStack.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    public StyleAttributes getStyle() {
        Deque<StyleAttributes> overrides = scopedStyles.get();
        if (!overrides.isEmpty()) {
            return overrides.peek();
        }
        return getCurrentCreationContext().map(ComponentCreationContext::style).orElse(fallbackStyle);
    }

    public <T> T withStyle(StyleAttributes style, Supplier<T> action) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(action, "action");
        Deque<StyleAttributes> overrides = scopedStyles.get();
        overrides.push(style);
        try {
            return action.get();
        } finally {
            overrides.pop();
            if (overrides.isEmpty()) {
                scopedStyles.remove();
            }
        }
    }

    public void withStyle(StyleAttributes style, Runnable action) {
        withStyle(style, () -> {
            action.run();
            return null;
        });
    }

    public Rectangle getBounds() {
        return getCurrentCreationContext()
                .map(context -> new Rectangle(context.bounds()))
                .orElseGet(Rectangle::new);
    }

    /** Sets the fallback used only when no component is currently being created. */
    @Deprecated(since = "2.0.0-AURELIA", forRemoval = false)
    public void setStyle(StyleAttributes style) {
        this.fallbackStyle = Objects.requireNonNull(style, "style");
    }

    public ComponentAttributes getComponentAttribute() {
        return getCurrentCreationContext()
                .map(ComponentCreationContext::attributes)
                .orElse(lastComponentAttribute);
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

    private static String normalizeType(String type) {
        return requireType(type, "component type").toLowerCase(Locale.ROOT);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
