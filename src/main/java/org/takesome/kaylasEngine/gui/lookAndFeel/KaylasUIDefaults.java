package org.takesome.kaylasEngine.gui.lookAndFeel;

import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasTheme;

import javax.swing.UIDefaults;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;
import java.awt.Color;

/** Applies one Kaylas theme to FlatLaf and standard Swing fallback components. */
final class KaylasUIDefaults {
    private KaylasUIDefaults() {
    }

    static void apply(UIDefaults defaults, KaylasTheme theme) {
        ColorUIResource background = color(theme.background());
        ColorUIResource surface = color(theme.surface());
        ColorUIResource elevated = color(theme.elevatedSurface());
        ColorUIResource border = color(theme.border());
        ColorUIResource accent = color(theme.accent());
        ColorUIResource accentHover = color(theme.accentHover());
        ColorUIResource accentPressed = color(theme.accentPressed());
        ColorUIResource foreground = color(theme.foreground());
        ColorUIResource muted = color(theme.mutedForeground());
        ColorUIResource disabled = color(theme.disabledForeground());
        ColorUIResource selection = color(theme.selectionBackground());
        ColorUIResource selectionForeground = color(theme.selectionForeground());
        ColorUIResource focus = color(theme.focusRing());

        defaults.put("control", surface);
        defaults.put("info", elevated);
        defaults.put("text", foreground);
        defaults.put("nimbusBase", surface);
        defaults.put("nimbusFocus", focus);
        defaults.put("nimbusSelectionBackground", selection);

        defaults.put("Panel.background", background);
        defaults.put("Viewport.background", background);
        defaults.put("Label.foreground", foreground);
        defaults.put("Label.disabledForeground", disabled);
        defaults.put("Separator.foreground", border);

        defaults.put("Button.background", surface);
        defaults.put("Button.foreground", foreground);
        defaults.put("Button.disabledText", disabled);
        defaults.put("Button.hoverBackground", elevated);
        defaults.put("Button.pressedBackground", accentPressed);
        defaults.put("Button.default.background", accent);
        defaults.put("Button.default.hoverBackground", accentHover);
        defaults.put("Button.default.pressedBackground", accentPressed);
        defaults.put("Button.arc", theme.arc());
        defaults.put("Button.minimumWidth", 72);
        defaults.put("Button.minimumHeight", theme.controlHeight());

        defaults.put("CheckBox.foreground", foreground);
        defaults.put("CheckBox.disabledText", disabled);
        defaults.put("RadioButton.foreground", foreground);
        defaults.put("RadioButton.disabledText", disabled);

        applyTextDefaults(defaults, "TextField", theme, surface, foreground, disabled, selection, selectionForeground, focus);
        applyTextDefaults(defaults, "PasswordField", theme, surface, foreground, disabled, selection, selectionForeground, focus);
        applyTextDefaults(defaults, "FormattedTextField", theme, surface, foreground, disabled, selection, selectionForeground, focus);
        applyTextDefaults(defaults, "TextArea", theme, surface, foreground, disabled, selection, selectionForeground, focus);
        applyTextDefaults(defaults, "TextPane", theme, surface, foreground, disabled, selection, selectionForeground, focus);
        applyTextDefaults(defaults, "EditorPane", theme, surface, foreground, disabled, selection, selectionForeground, focus);

        defaults.put("ComboBox.background", surface);
        defaults.put("ComboBox.foreground", foreground);
        defaults.put("ComboBox.disabledBackground", background);
        defaults.put("ComboBox.disabledForeground", disabled);
        defaults.put("ComboBox.selectionBackground", selection);
        defaults.put("ComboBox.selectionForeground", selectionForeground);
        defaults.put("ComboBox.buttonBackground", elevated);
        defaults.put("ComboBox.buttonArrowColor", muted);
        defaults.put("ComboBox.buttonHoverArrowColor", accentHover);
        defaults.put("ComboBox.buttonPressedArrowColor", accentPressed);
        defaults.put("ComboBox.minimumHeight", theme.controlHeight());

        defaults.put("Spinner.background", surface);
        defaults.put("Spinner.foreground", foreground);
        defaults.put("Spinner.buttonBackground", elevated);
        defaults.put("Spinner.buttonArrowColor", muted);
        defaults.put("Spinner.buttonHoverArrowColor", accentHover);
        defaults.put("Spinner.buttonPressedArrowColor", accentPressed);
        defaults.put("Spinner.minimumHeight", theme.controlHeight());

        defaults.put("Slider.trackColor", border);
        defaults.put("Slider.thumbColor", accent);
        defaults.put("Slider.hoverThumbColor", accentHover);
        defaults.put("Slider.pressedThumbColor", accentPressed);
        defaults.put("Slider.focusedColor", focus);

        defaults.put("ScrollBar.track", background);
        defaults.put("ScrollBar.thumb", border);
        defaults.put("ScrollBar.hoverThumbColor", accentHover);
        defaults.put("ScrollBar.pressedThumbColor", accentPressed);
        defaults.put("ScrollBar.width", theme.scrollBarWidth());
        defaults.put("ScrollBar.thumbArc", theme.arc());
        defaults.put("ScrollBar.trackArc", theme.arc());
        defaults.put("ScrollBar.showButtons", false);

        defaults.put("ProgressBar.background", surface);
        defaults.put("ProgressBar.foreground", accent);
        defaults.put("ProgressBar.selectionBackground", foreground);
        defaults.put("ProgressBar.selectionForeground", foreground);
        defaults.put("ProgressBar.arc", theme.arc());

        defaults.put("TabbedPane.background", background);
        defaults.put("TabbedPane.foreground", foreground);
        defaults.put("TabbedPane.selectedBackground", elevated);
        defaults.put("TabbedPane.hoverColor", surface);
        defaults.put("TabbedPane.focusColor", focus);
        defaults.put("TabbedPane.underlineColor", accent);
        defaults.put("TabbedPane.inactiveUnderlineColor", border);
        defaults.put("TabbedPane.tabType", "card");

        defaults.put("PopupMenu.background", elevated);
        defaults.put("MenuItem.background", elevated);
        defaults.put("MenuItem.foreground", foreground);
        defaults.put("MenuItem.selectionBackground", accent);
        defaults.put("MenuItem.selectionForeground", selectionForeground);
        defaults.put("ToolTip.background", elevated);
        defaults.put("ToolTip.foreground", foreground);
        defaults.put("ToolTip.border", new EmptyBorder(6, 9, 6, 9));

        defaults.put("Component.arc", theme.arc());
        defaults.put("Component.focusWidth", theme.focusWidth());
        defaults.put("Component.innerFocusWidth", 0);
        defaults.put("Component.focusColor", focus);
        defaults.put("Component.borderColor", border);
        defaults.put("Component.disabledBorderColor", background);
        defaults.put("Component.minimumWidth", 48);
        defaults.put("Component.minimumHeight", theme.controlHeight());
    }

    private static void applyTextDefaults(UIDefaults defaults,
                                          String prefix,
                                          KaylasTheme theme,
                                          ColorUIResource background,
                                          ColorUIResource foreground,
                                          ColorUIResource disabled,
                                          ColorUIResource selection,
                                          ColorUIResource selectionForeground,
                                          ColorUIResource focus) {
        defaults.put(prefix + ".background", background);
        defaults.put(prefix + ".foreground", foreground);
        defaults.put(prefix + ".inactiveForeground", disabled);
        defaults.put(prefix + ".disabledBackground", color(theme.background()));
        defaults.put(prefix + ".disabledForeground", disabled);
        defaults.put(prefix + ".selectionBackground", selection);
        defaults.put(prefix + ".selectionForeground", selectionForeground);
        defaults.put(prefix + ".caretForeground", focus);
        defaults.put(prefix + ".margin", new InsetsUIResource(5, 8, 5, 8));
        defaults.put(prefix + ".minimumHeight", theme.controlHeight());
    }

    private static ColorUIResource color(Color color) {
        return new ColorUIResource(color);
    }
}
