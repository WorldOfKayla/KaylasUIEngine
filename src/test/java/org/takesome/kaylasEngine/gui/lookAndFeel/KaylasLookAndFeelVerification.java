package org.takesome.kaylasEngine.gui.lookAndFeel;

import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.checkbox.Checkbox;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.label.Label;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButton;
import org.takesome.kaylasEngine.gui.components.passfield.PassField;
import org.takesome.kaylasEngine.gui.components.slider.Slider;
import org.takesome.kaylasEngine.gui.components.spinner.Spinner;
import org.takesome.kaylasEngine.gui.components.textArea.TextArea;
import org.takesome.kaylasEngine.gui.components.textfield.TextField;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasButton;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasCheckbox;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasCombobox;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasLabel;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasMultiButton;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasPassField;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasSlider;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasSpinner;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasTextArea;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasTextField;
import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasTheme;
import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasThemes;

import javax.swing.UIDefaults;
import javax.swing.UIManager;
import java.lang.reflect.Modifier;

/** Executable regression verification for the Kaylas Look & Feel runtime. */
public final class KaylasLookAndFeelVerification {
    private KaylasLookAndFeelVerification() {
    }

    public static void main(String[] args) {
        verifyThemeContract();
        verifyInstallationAndDefaults();
        verifyEnhancedComponentInheritance();
        verifyEncapsulation();
        System.out.println("KINETICA Look & Feel verification passed.");
    }

    private static void verifyThemeContract() {
        KaylasTheme theme = KaylasThemes.kineticaDark();
        require("KINETICA Dark".equals(theme.name()), "unexpected built-in theme name");
        require(theme.arc() > 0, "theme arc must be positive");
        require(theme.focusWidth() > 0, "theme focus width must be positive");
        require(theme.controlHeight() > 0, "theme control height must be positive");
        require(theme.scrollBarWidth() > 0, "theme scrollbar width must be positive");
        require(theme.foreground().getAlpha() == 255, "theme foreground must be opaque");
    }

    private static void verifyInstallationAndDefaults() {
        KaylasTheme theme = KaylasThemes.kineticaDark();
        require(KaylasLookAndFeel.install(theme), "Kaylas Look & Feel installation failed");
        require(UIManager.getLookAndFeel() instanceof KaylasLookAndFeel,
                "UIManager did not retain Kaylas Look & Feel");
        require(KaylasLookAndFeel.currentTheme() == theme,
                "active theme reference was not retained");

        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        require(theme.background().equals(defaults.getColor("Panel.background")),
                "panel background did not come from the active theme");
        require(theme.foreground().equals(defaults.getColor("Label.foreground")),
                "label foreground did not come from the active theme");
        require(theme.accent().equals(defaults.getColor("Button.default.background")),
                "default button accent did not come from the active theme");
        require(theme.selectionBackground().equals(defaults.getColor("TextField.selectionBackground")),
                "text selection color did not come from the active theme");
        require(Integer.valueOf(theme.arc()).equals(defaults.get("Component.arc")),
                "component arc metric was not installed");
        require(Integer.valueOf(theme.focusWidth()).equals(defaults.get("Component.focusWidth")),
                "focus width metric was not installed");
        require(Integer.valueOf(theme.scrollBarWidth()).equals(defaults.get("ScrollBar.width")),
                "scrollbar width metric was not installed");
        Object buttonHeight = defaults.get("Button.minimumHeight");
        Object textFieldHeight = defaults.get("TextField.minimumHeight");
        require(Integer.valueOf(theme.controlHeight()).equals(buttonHeight),
                "button control height metric was not installed: " + buttonHeight);
        require(Integer.valueOf(theme.controlHeight()).equals(textFieldHeight),
                "text-field control height metric was not installed: " + textFieldHeight);
    }

    private static void verifyEnhancedComponentInheritance() {
        require(Button.class.isAssignableFrom(KaylasButton.class),
                "KaylasButton must preserve the Button API");
        require(Checkbox.class.isAssignableFrom(KaylasCheckbox.class),
                "KaylasCheckbox must preserve the Checkbox API");
        require(TextField.class.isAssignableFrom(KaylasTextField.class),
                "KaylasTextField must preserve the TextField API");
        require(TextArea.class.isAssignableFrom(KaylasTextArea.class),
                "KaylasTextArea must preserve the TextArea API");
        require(PassField.class.isAssignableFrom(KaylasPassField.class),
                "KaylasPassField must preserve the PassField API");
        require(Slider.class.isAssignableFrom(KaylasSlider.class),
                "KaylasSlider must preserve the Slider API");
        require(Spinner.class.isAssignableFrom(KaylasSpinner.class),
                "KaylasSpinner must preserve the Spinner API");
        require(Label.class.isAssignableFrom(KaylasLabel.class),
                "KaylasLabel must preserve the Label API");
        require(MultiButton.class.isAssignableFrom(KaylasMultiButton.class),
                "KaylasMultiButton must preserve the MultiButton API");
        require(Combobox.class.isAssignableFrom(KaylasCombobox.class),
                "KaylasCombobox must preserve the Combobox API");
    }

    private static void verifyEncapsulation() {
        requireHiddenFinal("org.takesome.kaylasEngine.gui.lookAndFeel.KaylasUIDefaults");
        requireHiddenFinal(
                "org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasComponentSupport"
        );
    }

    private static void requireHiddenFinal(String className) {
        try {
            Class<?> type = Class.forName(className);
            require(!Modifier.isPublic(type.getModifiers()),
                    "Look & Feel implementation leaked into public API: " + className);
            require(Modifier.isFinal(type.getModifiers()),
                    "Look & Feel implementation must be final: " + className);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Look & Feel implementation not found: " + className, error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
