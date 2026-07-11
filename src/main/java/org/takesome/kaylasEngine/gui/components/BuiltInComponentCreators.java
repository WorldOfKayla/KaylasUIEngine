package org.takesome.kaylasEngine.gui.components;

import org.foxesworld.cfgProvider.ConfigTypeConverter;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.button.ButtonStyle;
import org.takesome.kaylasEngine.gui.components.checkbox.Checkbox;
import org.takesome.kaylasEngine.gui.components.checkbox.CheckboxStyle;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.combobox.ComboboxStyle;
import org.takesome.kaylasEngine.gui.components.compositeSlider.CompositeSlider;
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
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.utils.IconUtils;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.Objects;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/** Registers and constructs the engine-owned primitive component set. */
final class BuiltInComponentCreators {
    private final ComponentFactory factory;
    private final Engine engine;
    private final LanguageProvider language;
    private final IconUtils iconUtils;

    BuiltInComponentCreators(ComponentFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.engine = factory.getEngine();
        this.language = factory.getLangProvider();
        this.iconUtils = factory.getIconUtils();
    }

    void register() {
        factory.registerComponent("label", this::createLabel);
        factory.registerComponent("progressBar", this::createProgressBar);
        factory.registerComponent("button", this::createButton);
        factory.registerComponent("textArea", this::createTextArea);
        factory.registerComponent("checkBox", this::createCheckbox);
        factory.registerComponent("textField", this::createTextField);
        factory.registerComponent("spriteImage", this::createSpriteImage);
        factory.registerComponent("passField", this::createPassField);
        factory.registerComponent("spinner", this::createSpinner);
        factory.registerComponent("multiButton", this::createMultiButton);
        factory.registerComponent("combobox", this::createCombobox);
        factory.registerComponent("dropBox", this::createCombobox);
        factory.registerComponent("slider", this::createSlider);
        factory.registerComponent("compositeSlider", ComponentKind.COMPOSITE, this::createCompositeSlider);
        factory.registerComponent("fileSelector", ComponentKind.COMPOSITE, this::createFileSelector);
        factory.registerComponent("compositeComponent", ComponentKind.COMPOSITE, factory::createCompositeComponent);
        factory.registerComponent("tabs", ComponentKind.COMPOSITE, factory::createTabs);

        factory.registerAlias("checkbox", "checkBox");
        factory.registerAlias("check-box", "checkBox");
        factory.registerAlias("textfield", "textField");
        factory.registerAlias("text-field", "textField");
        factory.registerAlias("textarea", "textArea");
        factory.registerAlias("text-area", "textArea");
        factory.registerAlias("progress", "progressBar");
        factory.registerAlias("progress-bar", "progressBar");
        factory.registerAlias("sprite", "spriteImage");
        factory.registerAlias("dropdown", "dropBox");
        factory.registerAlias("select", "combobox");
        factory.registerAlias("composite", "compositeComponent");
        factory.registerAlias("tabbedPane", "tabs");
        factory.registerAlias("tabbed-pane", "tabs");
    }

    private JComponent createLabel(ComponentAttributes attributes) {
        Label label = new Label(factory);
        new LabelStyle(factory).apply(label);
        label.setIcon(iconUtils.getIcon(attributes));
        label.setText(localizedTextWithInitial(attributes));
        label.setForeground(hexToColor(valueOr(attributes.getColor(), factory.getStyle().getColor())));
        label.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), factory.getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), factory.getStyle().getFontStyle())
        ));
        return label;
    }

    private JComponent createProgressBar(ComponentAttributes attributes) {
        ProgressBar progressBar = new ProgressBar();
        new ProgressBarStyle(factory, attributes).apply(progressBar);
        return progressBar;
    }

    private JComponent createButton(ComponentAttributes attributes) {
        String text = localizedText(attributes.getLocaleKey());
        Button button = attributes.getImageIcon() == null
                ? new Button(factory, text)
                : new Button(factory, iconUtils.getIcon(attributes), text);
        new ButtonStyle(factory).apply(button);
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
            Engine.LOGGER.warn(
                    "Invalid key stroke '{}' for component '{}'.",
                    attributes.getKeyCode(),
                    attributes.getComponentId()
            );
            return;
        }
        String actionKey = valueOr(
                attributes.getComponentId(),
                "button.shortcut." + System.identityHashCode(button)
        );
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
        TextArea textArea = new TextArea(factory);
        textArea.setLineWrap(attributes.isLineWrap());
        new AreaStyle(factory).apply(textArea);
        textArea.setText(localizedTextWithInitial(attributes));
        textArea.setForeground(hexToColor(valueOr(attributes.getColor(), factory.getStyle().getColor())));
        textArea.setEditable(attributes.isEditable());
        textArea.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), factory.getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), factory.getStyle().getFontStyle())
        ));
        return textArea;
    }

    private JComponent createCheckbox(ComponentAttributes attributes) {
        Checkbox checkbox = new Checkbox(factory, localizedText(attributes.getLocaleKey()));
        new CheckboxStyle(factory).apply(checkbox);
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
            Engine.LOGGER.warn(
                    "Invalid key stroke '{}' for component '{}'.",
                    attributes.getKeyCode(),
                    attributes.getComponentId()
            );
            return;
        }
        String actionKey = valueOr(
                attributes.getComponentId(),
                "checkbox.shortcut." + System.identityHashCode(checkbox)
        );
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
        TextField textField = new TextField(factory);
        new TextFieldStyle(factory).apply(textField);
        if (attributes.getInitialValue() != null) {
            textField.setText(String.valueOf(attributes.getInitialValue()));
        }
        textField.setEditable(attributes.isEditable());
        textField.setActionCommand(attributes.getComponentId());
        textField.addActionListener(engine);
        return textField;
    }

    private JComponent createSpriteImage(ComponentAttributes attributes) {
        return new SpriteAnimation(factory);
    }

    private JComponent createPassField(ComponentAttributes attributes) {
        PassField passField = new PassField(factory, localizedText(attributes.getLocaleKey()));
        new PassFieldStyle(factory).apply(passField);
        passField.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), factory.getStyle().getFont()),
                effectiveFontSize(attributes),
                valueOr(attributes.getFontStyle(), factory.getStyle().getFontStyle())
        ));
        passField.setEditable(attributes.isEditable());
        passField.setActionCommand(attributes.getComponentId());
        return passField;
    }

    private JComponent createSpinner(ComponentAttributes attributes) {
        int minimum = attributes.getMinValue();
        int maximum = attributes.getMaxValue() > minimum ? attributes.getMaxValue() : minimum + 100;
        int initial = Math.max(
                minimum,
                Math.min(maximum, intValue(attributes.getInitialValue(), minimum))
        );
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
        MultiButton multiButton = new MultiButton(factory);
        new MultiButtonStyle(factory, attributes).apply(multiButton);
        multiButton.setActionCommand(attributes.getComponentId());
        multiButton.addActionListener(engine);
        return multiButton;
    }

    private JComponent createCombobox(ComponentAttributes attributes) {
        Combobox combobox = new Combobox(factory, attributes.getBounds().y);
        new ComboboxStyle(factory).apply(combobox);
        return combobox;
    }

    private JComponent createSlider(ComponentAttributes attributes) {
        Slider slider = new Slider(factory);
        int minimum = attributes.getMinValue();
        int maximum = attributes.getMaxValue() > minimum ? attributes.getMaxValue() : minimum + 100;
        int initial = Math.max(
                minimum,
                Math.min(maximum, intValue(attributes.getInitialValue(), minimum))
        );
        int range = Math.max(1, maximum - minimum);

        slider.setMinimum(minimum);
        slider.setMaximum(maximum);
        slider.setValue(initial);
        slider.setPaintTicks(true);
        slider.setPaintLabels(!attributes.isHideWordMarkers());
        slider.setMajorTickSpacing(attributes.getMajorSpacing() > 0
                ? attributes.getMajorSpacing()
                : Math.max(1, range / 5));
        slider.setMinorTickSpacing(attributes.getMinorSpacing() > 0
                ? attributes.getMinorSpacing()
                : Math.max(1, range / 10));
        slider.setUI(new TexturedSliderUI(factory, slider, factory.getStyle()));
        return slider;
    }

    private JComponent createCompositeSlider(ComponentAttributes attributes) {
        CompositeSlider slider = new CompositeSlider(factory);
        if (attributes.getInitialValue() != null) {
            slider.setValue(intValue(attributes.getInitialValue(), attributes.getMinValue()));
        }
        return slider;
    }

    private JComponent createFileSelector(ComponentAttributes attributes) {
        FileSelector selector = new FileSelector(factory, selectionMode(attributes.getSelectionMode()));
        if (attributes.getInitialValue() != null) {
            selector.setValue(String.valueOf(attributes.getInitialValue()));
        }
        return selector;
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
        return language.getString(localeKey);
    }

    private int effectiveFontSize(ComponentAttributes attributes) {
        return attributes.getFontSize() > 0
                ? attributes.getFontSize()
                : factory.getStyle().getFontSize();
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
        if (mode != null && !mode.isBlank()
                && resolved == SelectionMode.FILES_ONLY
                && !SelectionMode.isFileAlias(mode)) {
            Engine.LOGGER.warn("Invalid file selection mode '{}'; using FILES_ONLY.", mode);
        }
        return resolved;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
