package org.foxesworld.engine.gui.components;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.foxesworld.engine.Engine;
import org.foxesworld.engine.gui.components.button.Button;
import org.foxesworld.engine.gui.components.button.ButtonStyle;
import org.foxesworld.engine.gui.components.checkbox.Checkbox;
import org.foxesworld.engine.gui.components.checkbox.CheckboxStyle;
import org.foxesworld.engine.gui.components.compositeSlider.CompositeSlider;
import org.foxesworld.engine.gui.components.dropBox.DropBox;
import org.foxesworld.engine.gui.components.dropBox.DropBoxStyle;
import org.foxesworld.engine.gui.components.fileSelector.FileSelector;
import org.foxesworld.engine.gui.components.fileSelector.SelectionMode;
import org.foxesworld.engine.gui.components.label.Label;
import org.foxesworld.engine.gui.components.label.LabelStyle;
import org.foxesworld.engine.gui.components.multiButton.MultiButton;
import org.foxesworld.engine.gui.components.multiButton.MultiButtonStyle;
import org.foxesworld.engine.gui.components.passfield.PassField;
import org.foxesworld.engine.gui.components.passfield.PassFieldStyle;
import org.foxesworld.engine.gui.components.progressBar.HearthstoneProgressBar;
import org.foxesworld.engine.gui.components.progressBar.ProgressBarStyle;
import org.foxesworld.engine.gui.components.slider.Slider;
import org.foxesworld.engine.gui.components.spinner.Spinner;
import org.foxesworld.engine.gui.components.sprite.SpriteAnimation;
import org.foxesworld.engine.gui.components.textArea.AreaStyle;
import org.foxesworld.engine.gui.components.textArea.TextArea;
import org.foxesworld.engine.gui.components.textfield.TextField;
import org.foxesworld.engine.gui.components.textfield.TextFieldStyle;
import org.foxesworld.engine.gui.components.utils.tooltip.CustomTooltip;
import org.foxesworld.engine.gui.components.utils.tooltip.TooltipAttributes;
import org.foxesworld.engine.gui.styles.StyleAttributes;
import org.foxesworld.engine.locale.LanguageProvider;
import org.foxesworld.engine.utils.IconUtils;
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

import static org.foxesworld.engine.utils.FontUtils.hexToColor;

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
        registerComponent("dropBox", this::createDropBox);
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
                Engine.LOGGER.error("Error creating component: {}", attributes.getComponentType(), e);
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
        Engine.LOGGER.warn("Using experimental CompositeComponent {} !", componentAttributes.getComponentId());
        CompositeComponent compositeComponent = new CompositeComponent();

        compositeComponent.setVisible(componentAttributes.isVisible());
        compositeComponent.setOpaque(componentAttributes.isOpaque());

        List<ComponentAttributes> children = componentAttributes.getChildComponents();
        Engine.LOGGER.info("    - Number of child components: {}", children.size());

        for (ComponentAttributes comp : children) {
            if(comp.getInitialValue() == null) {
                comp.setInitialValue(componentAttributes.getInitialValue());
            }
            Engine.LOGGER.info("Creating child component: {}", comp.getComponentType());

            JComponent child = this.createComponent(comp);

            if (child == null) {
                Engine.LOGGER.error("Error: createComponent returned null!");
            } else {
                Engine.LOGGER.info("Adding child component: {}", child.getClass().getSimpleName());
                compositeComponent.addSubComponent(child);
            }
        }

        Engine.LOGGER.info("CompositeComponent creation completed.");
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
        component.setName(attributes.getComponentId());
        component.setBounds(attributes.getBounds());
        component.setOpaque(style != null && style.isOpaque());

        if (attributes.getToolTip() != null) {
            initializeTooltip(component, attributes);
        }

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
        String toolTipStyle = attributes.getTooltipStyle() != null ? attributes.getTooltipStyle() : "default";
        TooltipAttributes tooltipAttributes = loadTooltipAttributes(toolTipStyle);

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
        this.style = null;
        String componentType = attributes.getComponentType();
        String componentStyle = attributes.getComponentStyle();
        if (componentType == null || componentStyle == null) {
            return;
        }

        Map<String, StyleAttributes> stylesByType = componentStyles.computeIfAbsent(componentType,
                key -> engine.getStyleProvider().getElementStyles().get(key));
        if (stylesByType == null) {
            Engine.LOGGER.warn("No styles registered for component type: {}", componentType);
            return;
        }
        this.style = stylesByType.get(componentStyle);
        if (this.style == null) {
            Engine.LOGGER.warn("No style '{}' registered for component type: {}", componentStyle, componentType);
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
        label.setForeground(hexToColor(componentAttributes.getColor()));
        if (style != null) {
            label.setFont(engine.getFONTUTILS().getFont(style.getFont(), componentAttributes.getFontSize()));
        }
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
        ProgressBarStyle progressBarStyle = new ProgressBarStyle(this);
        JProgressBar progressBar = new JProgressBar();
        progressBarStyle.apply(progressBar);
        progressBar.setBounds(bounds);
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
        textArea.setForeground(hexToColor(componentAttributes.getColor()));
        textArea.setEditable(componentAttributes.isEnabled());
        if (style != null) {
            textArea.setFont(engine.getFONTUTILS().getFont(style.getFont(), componentAttributes.getFontSize()));
        }
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
        Spinner spinner = new Spinner(Integer.parseInt((String) componentAttributes.getInitialValue()), componentAttributes.getMinValue(), componentAttributes.getMaxValue(), componentAttributes.getMajorSpacing());
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
    private JComponent createDropBox(ComponentAttributes componentAttributes) {
        DropBoxStyle dropBoxStyle = new DropBoxStyle(this);
        DropBox dropBox = new DropBox(this, componentAttributes.getBounds().y);
        dropBoxStyle.apply(dropBox);
        return dropBox;
    }

    private JComponent createSlider(ComponentAttributes componentAttributes) {
        Slider slider = new Slider(this);
        slider.setValue(Integer.parseInt((String) componentAttributes.getInitialValue()));
        return slider;
    }
    private JComponent createCompositeSlider(ComponentAttributes componentAttributes) {
        CompositeSlider compositeSlider = new CompositeSlider(this);
        compositeSlider.setValue(Integer.parseInt((String) componentAttributes.getInitialValue()));
        return compositeSlider;
    }

    private JComponent createFileSelector(ComponentAttributes componentAttributes) {
        FileSelector fileSelector = new FileSelector(this, SelectionMode.valueOf(componentAttributes.getSelectionMode()));
        fileSelector.setValue((String) componentAttributes.getInitialValue());
        return fileSelector;
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

    /**
     * Sets a listener that will receive component factory events (creation start/completion etc.).
     *
     * @param componentFactoryListener listener instance.
     */
    public void setComponentFactoryListener(ComponentFactoryListener componentFactoryListener) {
        this.componentFactoryListener = componentFactoryListener;
    }
}