package org.takesome.kaylasEngine.gui.componentAccessor.internal.value;

import org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueRegistry;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;
import org.takesome.kaylasEngine.gui.components.compositeSlider.CompositeSlider;
import org.takesome.kaylasEngine.gui.components.fileSelector.FileSelector;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.text.JTextComponent;

/** Installs the engine's standard semantic value adapters into a registry. */
public final class DefaultComponentValueAdapters {
    private DefaultComponentValueAdapters() {
    }

    public static ComponentValueRegistry createRegistry() {
        ComponentValueRegistry registry = new ComponentValueRegistry();
        install(registry);
        return registry;
    }

    public static void install(ComponentValueRegistry registry) {
        registry.registerWritable(
                FileSelector.class,
                FileSelector::getValue,
                (component, value) -> component.setValue(value == null ? "" : String.valueOf(value)),
                100
        );
        registry.registerWritable(
                CompositeSlider.class,
                CompositeSlider::getValue,
                CompositeSlider::setValue,
                100
        );
        registry.registerWritable(
                ProgressBar.class,
                ProgressBar::getValue,
                ProgressBar::setValue,
                100
        );
        registry.registerWritable(
                CompositeComponent.class,
                CompositeComponent::getValue,
                CompositeComponent::setValue,
                10
        );
        registry.registerWritable(
                JPasswordField.class,
                component -> new String(component.getPassword()),
                (component, value) -> component.setText(value == null ? "" : String.valueOf(value)),
                50
        );
        registry.registerWritable(
                JTextComponent.class,
                JTextComponent::getText,
                (component, value) -> component.setText(value == null ? "" : String.valueOf(value)),
                0
        );
        registry.registerWritable(
                AbstractButton.class,
                AbstractButton::isSelected,
                (component, value) -> component.setSelected(booleanValue(value)),
                0
        );
        registry.registerWritable(
                JSlider.class,
                JSlider::getValue,
                (component, value) -> component.setValue(intValue(value, component.getValue())),
                0
        );
        registry.registerWritable(
                JSpinner.class,
                JSpinner::getValue,
                JSpinner::setValue,
                0
        );
        registry.registerWritable(
                JComboBox.class,
                JComboBox::getSelectedIndex,
                (component, value) -> component.setSelectedIndex(
                        intValue(value, component.getSelectedIndex())
                ),
                0
        );
        registry.registerWritable(
                JProgressBar.class,
                JProgressBar::getValue,
                (component, value) -> component.setValue(intValue(value, component.getValue())),
                0
        );
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // Use fallback.
            }
        }
        return fallback;
    }
}
