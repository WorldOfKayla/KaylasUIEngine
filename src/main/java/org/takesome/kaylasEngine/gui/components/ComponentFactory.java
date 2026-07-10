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
import org.takesome.kaylasEngine.gui.components.compositeSlider.CompositeSlider;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.combobox.ComboboxStyle;
import org.takesome.kaylasEngine.gui.components.fileSelector.FileSelector;
import org.takesome.kaylasEngine.gui.components.fileSelector.SelectionMode;
import org.takesome.kaylasEngine.gui.components.label.Label;
import org.takesome.kaylasEngine.gui.components.label.LabelStyle;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButton;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButtonStyle;
import org.takesome.kaylasEngine.gui.components.passfield.PassField;
import org.takesome.kaylasEngine.gui.components.passfield.PassFieldStyle;
import org.takesome.kaylasEngine.gui.components.progressBar.HearthstoneProgressBar;
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
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.gui.scripting.LuaUiScriptEngine;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.utils.IconUtils;
import java.awt.Rectangle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/**
 * Factory responsible for creating Swing {@link JComponent} instances used by the GUI system.
 *
 * <p>
 * The factory holds a registry of component creators and style definitions. Components are created
 * from {@link ComponentAttributes} describing type, style, bounds and behavior. The factory also
 * provides async creation helpers, tooltip initialization and composite component creation.
 * </p>
 */
public class ComponentFactory extends JComponent {

    private final Engine engine;
    private final LanguageProvider langProvider;
    private final IconUtils iconUtils;
    private final Map<String, Map<String, StyleAttributes>> componentStyles = new ConcurrentHashMap<>();
    private final Map<String, Function<ComponentAttributes, JComponent>> componentRegistry = new ConcurrentHashMap<>();
    private final LuaUiScriptEngine luaUiScriptEngine;
    private final Map<String, TooltipAttributes> tooltipCache = new ConcurrentHashMap<>();
    private StyleAttributes style;
    private ComponentAttributes componentAttribute;
    private ComponentFactoryListener componentFactoryListener;
    private Rectangle bounds;

    /**
     * Creates a new ComponentFactory bound to the provided {@link Engine}.
     *
     * <p>
     * The constructor registers a set of built-in component creators (label, button, textArea, etc.).
     * </p>
     *
     * @param engine engine instance used for resources, localization and event wiring; must not be {@code null}.
     */
    public ComponentFactory(Engine engine) {
        this.engine = engine;
        this.langProvider = engine.getLANG();
        this.iconUtils = new IconUtils(engine);
        this.luaUiScriptEngine = new LuaUiScriptEngine(engine);

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
        registerComponent("comboBox", this::createCombobox);
        registerComponent("dropBox", this::createCombobox);
        registerComponent("slider", this::createSlider);
        registerComponent("compositeSlider", this::createCompositeSlider);
        registerComponent("fileSelector", this::createFileSelector);
        registerComponent("compositeComponent", this::createCompositeComponent);
    }

    /**
     * Registers a component creator function for a given component type identifier.
     *
     * @param type    string identifier used in {@link ComponentAttributes#getComponentType()}.
     * @param creator function that receives {@link ComponentAttributes} and returns the created {@link JComponent}.
     */
    public void registerComponent(String type, Function<ComponentAttributes, JComponent> creator) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Component type must not be blank");
        }
        componentRegistry.put(type, Objects.requireNonNull(creator, "creator"));
        Engine.LOGGER.info("    - Registered component: {}", type);
    }

    /**
     * Creates a component asynchronously using a background task.
     *
     * <p>
     * This method wraps {@link #createComponent(ComponentAttributes)} in a {@link CompletableFuture}.
     * Any exceptions during creation are caught and logged; {@code null} is returned in such cases.
     * </p>
     *
     * @param attributes component attributes describing the component to create.
     * @return a CompletableFuture that completes with the created {@link JComponent} or {@code null} on error.
     */
    public CompletableFuture<JComponent> createComponentAsync(ComponentAttributes attributes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createComponent(attributes);
            } catch (Exception e) {
                Engine.LOGGER.error("Error creating component: {}", attributes != null ? attributes.getComponentType() : "null", e);
                return null;
            }
        }, engine.getExecutorServiceProvider().getExecutorService());
    }

    /**
     * Creates a composite component that contains multiple child components.
     *
     * <p>
     * This feature is experimental: children are created (potentially asynchronously internally)
     * and attached to a {@code CompositeComponent}. Child components inherit the parent's initial value
     * when not explicitly provided.
     * </p>
     *
     * @param componentAttributes attributes describing the composite and its children.
     * @return constructed composite {@link JComponent}.
     * @throws RuntimeException if child creation is interrupted or fails.
     */
    public JComponent createCompositeComponent(ComponentAttributes componentAttributes) {
        CompositeComponent compositeComponent = new CompositeComponent(resolveCompositeLayout(componentAttributes));
        compositeComponent.setLayoutConfig(componentAttributes.getLayoutConfig());
        compositeComponent.setValue(componentAttributes.getInitialValue());
        compositeComponent.setVisible(componentAttributes.isVisible());
        compositeComponent.setOpaque(componentAttributes.isOpaque());

        List<ComponentAttributes> children = componentAttributes.getChildComponents();
        if (children == null || children.isEmpty()) {
            Engine.LOGGER.info("CompositeComponent '{}' has no child components.", componentAttributes.getComponentId());
            return compositeComponent;
        }

        Engine.LOGGER.info("Creating CompositeComponent '{}' with {} child components.",
                componentAttributes.getComponentId(), children.size());

        for (ComponentAttributes childAttributes : children) {
            if (childAttributes == null) {
                Engine.LOGGER.warn("CompositeComponent '{}' ignored null child attributes.", componentAttributes.getComponentId());
                continue;
            }
            inheritCompositeDefaults(componentAttributes, childAttributes);
            JComponent child = this.createComponent(childAttributes);
            compositeComponent.addSubComponent(child, compositeComponent.getLayoutConfigFor(childAttributes.getComponentType()));
        }

        Engine.LOGGER.info("CompositeComponent '{}' created successfully.", componentAttributes.getComponentId());
        return compositeComponent;
    }



    /**
     * Creates a Swing component described by {@link ComponentAttributes}.
     *
     * <p>
     * The method resolves a registered creator by {@code componentType}, applies styles, sets bounds,
     * initializes tooltips (if present), and wires basic actions/listeners.
     * </p>
     *
     * @param attributes descriptor describing the desired component.
     * @return created {@link JComponent}.
     * @throws IllegalArgumentException if the {@code componentType} is not registered / supported.
     */
    public JComponent createComponent(ComponentAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        this.componentAttribute = attributes;
        this.bounds = attributes.getBounds();
        loadStyle(attributes);

        String componentType = attributes.getComponentType();
        Function<ComponentAttributes, JComponent> creator = componentRegistry.get(componentType);
        if (creator == null) {
            throw new IllegalArgumentException("Unsupported component type: " + componentType);
        }

        if (componentFactoryListener != null) {
            componentFactoryListener.onComponentCreation(attributes);
        }

        JComponent component = Objects.requireNonNull(creator.apply(attributes),
                "Component creator returned null for type: " + componentType);
        applyCommonAttributes(component, attributes);
        return component;
    }

    /**
     * Initializes and attaches a tooltip to the given component based on tooltip style attributes.
     *
     * <p>
     * This method reads tooltip style definitions from a bundled JSON resource and builds a {@link CustomTooltip}.
     * </p>
     *
     * @param component  component to attach the tooltip to.
     * @param attributes component attributes containing tooltip keys and style names.
     */
    private void initializeTooltip(JComponent component, ComponentAttributes attributes) {
        String toolTipStyle = valueOr(attributes.getTooltipStyle(), "default");
        TooltipAttributes tooltipAttributes = tooltipCache.computeIfAbsent(toolTipStyle, this::loadTooltipAttributes);

        if (tooltipAttributes != null) {
            CustomTooltip tooltip = new CustomTooltip(
                    hexToColor(tooltipAttributes.getBgColor()),
                    hexToColor(tooltipAttributes.getTextColor()),
                    tooltipAttributes.getBorderRadius(),
                    engine.getFONTUTILS().getFont(tooltipAttributes.getFont(), tooltipAttributes.getFontSize())
            );
            tooltip.attachToComponent(component, langProvider.getString(attributes.getToolTip()), 2000);
        }
    }

    /**
     * Loads the style for the current component from the engine's style provider.
     *
     * @param attributes attributes containing type and style name.
     */
    private void loadStyle(ComponentAttributes attributes) {
        String componentType = attributes.getComponentType();
        String componentStyle = attributes.getComponentStyle();
        this.style = engine.getStyleProvider().getStyle(componentType, componentStyle);
        if (componentType != null && !componentType.isBlank()) {
            componentStyles.computeIfAbsent(componentType, key -> engine.getStyleProvider().getElementStyles().get(key));
        }
    }

    /**
     * Loads tooltip style attributes from a bundled JSON resource.
     *
     * @param styleName style key to lookup in the tooltip definitions.
     * @return TooltipAttributes instance or {@code null} if loading fails.
     */
    private TooltipAttributes loadTooltipAttributes(String styleName) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("assets/styles/tooltip.json");
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(inputStream))) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            if (jsonObject == null || !jsonObject.has(styleName)) {
                Engine.LOGGER.warn("Tooltip style '{}' not found", styleName);
                return null;
            }
            return new Gson().fromJson(jsonObject.get(styleName), TooltipAttributes.class);
        } catch (Exception e) {
            Engine.LOGGER.error("Failed to load tooltip attributes for style: {}", styleName, e);
            return null;
        }
    }

    private JComponent createLabel(ComponentAttributes componentAttributes) {
        LabelStyle labelStyle = new LabelStyle(this);
        Label label = new Label(this);
        labelStyle.apply(label);
        label.setIcon(iconUtils.getIcon(componentAttributes));
        label.setText(localizedTextWithInitial(componentAttributes));
        label.setForeground(hexToColor(valueOr(componentAttributes.getColor(), style.getColor())));
        label.setFont(engine.getFONTUTILS().getFont(
                valueOr(componentAttributes.getFont(), style.getFont()),
                effectiveFontSize(componentAttributes),
                valueOr(componentAttributes.getFontStyle(), style.getFontStyle())
        ));
        return label;
    }

    private String localizedTextWithInitial(ComponentAttributes componentAttributes) {
        String localized = this.getEngine().getLANG().getString(componentAttributes.getLocaleKey());
        Object initialValue = componentAttributes.getInitialValue();
        if (initialValue == null || String.valueOf(initialValue).isBlank()) {
            return localized;
        }
        return localized + " " + initialValue;
    }

    private JComponent createProgressBar(ComponentAttributes componentAttributes) {
        ProgressBar progressBar = new ProgressBar();
        ProgressBarStyle progressBarStyle = new ProgressBarStyle(this, componentAttributes);
        progressBarStyle.apply(progressBar);
        return progressBar;
    }
    private JComponent createButton(ComponentAttributes componentAttributes) {
        ButtonStyle buttonStyle = new ButtonStyle(this);
        String buttonText = this.getEngine().getLANG().getString(componentAttributes.getLocaleKey());
        Button button = (componentAttributes.getImageIcon() != null) ? new Button(this, iconUtils.getIcon(componentAttributes), buttonText) : new Button(this, buttonText);
        buttonStyle.apply(button);
        button.setBounds(bounds);
        button.setActionCommand(componentAttributes.getComponentId());
        button.addActionListener(engine);
        if (componentAttributes.getKeyCode() != null) {
            button.setFocusable(true);
            button.requestFocus();
            KeyStroke keyStroke = KeyStroke.getKeyStroke(componentAttributes.getKeyCode());
            AbstractAction buttonAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    button.ButtonClick();
                    button.doClick();
                    button.setPressed(false);
                }
            };

            button.getActionMap().put(componentAttributes.getComponentId(), buttonAction);
            button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, componentAttributes.getComponentId());
            button.setEnabled(componentAttributes.isEnabled());
        }
        return button;
    }

    private JComponent createTextArea(ComponentAttributes componentAttributes) {
        AreaStyle areaStyle = new AreaStyle(this);
        TextArea textArea = new TextArea(this);
        textArea.setLineWrap(componentAttributes.isLineWrap());
        areaStyle.apply(textArea);
        textArea.setText(localizedTextWithInitial(componentAttributes));
        textArea.setForeground(hexToColor(valueOr(componentAttributes.getColor(), style.getColor())));
        textArea.setEditable(componentAttributes.isEnabled());
        textArea.setFont(engine.getFONTUTILS().getFont(style.getFont(), effectiveFontSize(componentAttributes)));
        return textArea;
    }

    private JComponent createCheckbox(ComponentAttributes componentAttributes) {
        CheckboxStyle checkboxStyle = new CheckboxStyle(this);
        Checkbox checkbox = new Checkbox(this, this.getEngine().getLANG().getString(componentAttributes.getLocaleKey()));
        checkboxStyle.apply(checkbox);
        checkbox.setSelected(Boolean.parseBoolean(String.valueOf(componentAttributes.getInitialValue())));
        if (componentAttributes.getKeyCode() != null) {
            checkbox.setFocusable(true);
            checkbox.requestFocus();
            KeyStroke keyStroke = KeyStroke.getKeyStroke(componentAttributes.getKeyCode());
            AbstractAction buttonAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    checkbox.toggleCheckbox();
                    checkbox.doClick();
                }
            };

            checkbox.getActionMap().put(componentAttributes.getComponentId(), buttonAction);
            checkbox.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, componentAttributes.getComponentId());
        }
        checkbox.setEnabled(componentAttributes.isEnabled());
        return checkbox;
    }

    private JComponent createTextField(ComponentAttributes componentAttributes) {
        TextFieldStyle textFieldStyle = new TextFieldStyle(this);
        TextField textField = new TextField(this);
        textFieldStyle.apply(textField);
        if (componentAttributes.getInitialValue() != null) {
            textField.setText(String.valueOf(componentAttributes.getInitialValue()));
        }
        textField.setActionCommand(componentAttributes.getComponentId());
        textField.addActionListener(engine);
        return textField;
    }

    private JComponent createSpriteImage() {
        return new SpriteAnimation(this);
    }

    private JComponent createSpriteImage(ComponentAttributes attributes) {
        return new SpriteAnimation(this);
    }

    private JComponent createPassField(ComponentAttributes componentAttributes) {
        PassFieldStyle passFieldStyle = new PassFieldStyle(this);
        PassField passField = new PassField(this, this.getEngine().getLANG().getString(componentAttributes.getLocaleKey()));
        passFieldStyle.apply(passField);
        passField.setFont(engine.getFONTUTILS().getFont(style.getFont(), style.getFontSize()));
        passField.setActionCommand(componentAttributes.getComponentId());
        return passField;
    }

    private JComponent createSpinner(ComponentAttributes componentAttributes) {
        Spinner spinner = new Spinner(intValue(componentAttributes.getInitialValue(), componentAttributes.getMinValue()), componentAttributes.getMinValue(), componentAttributes.getMaxValue(), componentAttributes.getMajorSpacing());
        if(spinner.getSpinnerListener() != null) {
            spinner.init();
        }
        return spinner;
    }
    private JComponent createMultiButton(ComponentAttributes componentAttributes) {
        MultiButtonStyle multiButtonStyle = new MultiButtonStyle(this, componentAttributes);
        MultiButton multiButton = new MultiButton(this);
        multiButtonStyle.apply(multiButton);
        multiButton.setActionCommand(componentAttributes.getComponentId());
        multiButton.addActionListener(engine);
        return multiButton;
    }

    private JComponent createTestPB(ComponentAttributes componentAttributes){
        return new HearthstoneProgressBar();
    }
    private JComponent createCombobox(ComponentAttributes componentAttributes) {
        ComboboxStyle comboboxStyle = new ComboboxStyle(this);
        Combobox combobox = new Combobox(this, componentAttributes.getBounds().y);
        comboboxStyle.apply(combobox);
        return combobox;
    }

    private JComponent createSlider(ComponentAttributes componentAttributes) {
        Slider slider = new Slider(this);
        int minValue = componentAttributes.getMinValue();
        int maxValue = componentAttributes.getMaxValue() > minValue ? componentAttributes.getMaxValue() : minValue + 100;
        int initialValue = Math.max(minValue, Math.min(maxValue, intValue(componentAttributes.getInitialValue(), minValue)));

        slider.setMinimum(minValue);
        slider.setMaximum(maxValue);
        slider.setValue(initialValue);
        slider.setOpaque(false);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setMajorTickSpacing(componentAttributes.getMajorSpacing() > 0
                ? componentAttributes.getMajorSpacing()
                : Math.max(1, (maxValue - minValue) / 5));
        slider.setMinorTickSpacing(componentAttributes.getMinorSpacing() > 0
                ? componentAttributes.getMinorSpacing()
                : Math.max(1, (maxValue - minValue) / 10));

        slider.setUI(new TexturedSliderUI(this, slider, style));
        return slider;
    }
    private JComponent createCompositeSlider(ComponentAttributes componentAttributes) {
        CompositeSlider compositeSlider = new CompositeSlider(this);
        if (componentAttributes.getInitialValue() != null) {
            compositeSlider.setValue(intValue(componentAttributes.getInitialValue(), componentAttributes.getMinValue()));
        }
        return compositeSlider;
    }

    private JComponent createFileSelector(ComponentAttributes componentAttributes) {
        FileSelector fileSelector = new FileSelector(this, selectionMode(componentAttributes.getSelectionMode()));
        fileSelector.setValue(String.valueOf(componentAttributes.getInitialValue()));
        return fileSelector;
    }

    private CompositeComponent.LayoutMode resolveCompositeLayout(ComponentAttributes attributes) {
        String mode = valueOr(attributes.getAlignment(), "absolute").toLowerCase();
        return switch (mode) {
            case "vertical", "y", "box-y" -> CompositeComponent.LayoutMode.VERTICAL;
            case "horizontal", "x", "box-x" -> CompositeComponent.LayoutMode.HORIZONTAL;
            case "flow" -> CompositeComponent.LayoutMode.FLOW;
            default -> CompositeComponent.LayoutMode.ABSOLUTE;
        };
    }

    private void inheritCompositeDefaults(ComponentAttributes parent, ComponentAttributes child) {
        if (child.getInitialValue() == null && parent.getInitialValue() != null) {
            child.setInitialValue(parent.getInitialValue());
        }
    }

    private void applyCommonAttributes(JComponent component, ComponentAttributes attributes) {
        component.setName(attributes.getComponentId());
        component.setBounds(attributes.getBounds());
        component.setOpaque(style != null && style.isOpaque());

        if (attributes.getToolTip() != null) {
            initializeTooltip(component, attributes);
        }
        luaUiScriptEngine.bind(component, attributes);
    }

    private int effectiveFontSize(ComponentAttributes attributes) {
        return attributes.getFontSize() > 0 ? attributes.getFontSize() : style.getFontSize();
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (Integer) ConfigTypeConverter.convertToDeclaredType(value, int.class);
        } catch (RuntimeException ex) {
            Engine.LOGGER.warn("Invalid numeric component value: {}", value);
            return fallback;
        }
    }

    private SelectionMode selectionMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return SelectionMode.FILES_ONLY;
        }
        try {
            return SelectionMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            Engine.LOGGER.warn("Invalid file selection mode '{}', falling back to FILES_ONLY", mode);
            return SelectionMode.FILES_ONLY;
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Returns the engine instance associated with this factory.
     *
     * @return engine instance.
     */
    public Engine getEngine() {
        return engine;
    }

    /**
     * Returns the language provider used for localization.
     *
     * @return language provider.
     */
    public LanguageProvider getLangProvider() {
        return langProvider;
    }

    /**
     * Returns the icon utility instance used to resolve icons for components.
     *
     * @return icon utility.
     */
    public IconUtils getIconUtils() {
        return iconUtils;
    }

    /**
     * Returns the map of component styles loaded from the engine style provider.
     *
     * @return component styles map (componentType -> (styleName -> StyleAttributes)).
     */
    public Map<String, Map<String, StyleAttributes>> getComponentStyles() {
        return componentStyles;
    }

    /**
     * Returns the registry of component creators.
     *
     * @return map of component type -> creator function.
     */
    public Map<String, Function<ComponentAttributes, JComponent>> getComponentRegistry() {
        return componentRegistry;
    }

    /**
     * Returns the currently applied style attributes used during component creation.
     *
     * @return current {@link StyleAttributes} or {@code null} if none applied.
     */
    public StyleAttributes getStyle() {
        return style;
    }

    /**
     * Manually sets the style attributes to be used for subsequent component creation.
     *
     * @param style style attributes.
     */
    public void setStyle(StyleAttributes style) {
        this.style = style;
    }

    /**
     * Returns the last {@link ComponentAttributes} used by {@link #createComponent(ComponentAttributes)}.
     *
     * @return last component attributes or {@code null}.
     */
    public ComponentAttributes getComponentAttribute() {
        return componentAttribute;
    }

    public LuaUiScriptEngine getLuaUiScriptEngine() {
        return luaUiScriptEngine;
    }

    /**
     * Sets a listener that will receive component factory events (creation start/completion etc.).
     *
     * @param componentFactoryListener listener instance.
     */
    public void setComponentFactoryListener(ComponentFactoryListener componentFactoryListener) {
        this.componentFactoryListener = componentFactoryListener;
    }
}
