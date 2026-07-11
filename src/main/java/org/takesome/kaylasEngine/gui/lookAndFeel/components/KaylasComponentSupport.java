package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.lookAndFeel.KaylasLookAndFeel;
import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasTheme;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;

/** Shared state, accessibility and focus painting for Kaylas LAF components. */
final class KaylasComponentSupport {
    private KaylasComponentSupport() {
    }

    static void install(JComponent component, String role, boolean focusable) {
        component.putClientProperty(KaylasLookAndFeel.COMPONENT_PROPERTY, Boolean.TRUE);
        component.putClientProperty(KaylasLookAndFeel.ROLE_PROPERTY, role);
        component.putClientProperty("JComponent.roundRect", Boolean.TRUE);
        component.setFocusTraversalKeysEnabled(true);
        if (focusable) {
            component.setFocusable(true);
            component.setRequestFocusEnabled(true);
        }

        component.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                component.repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                component.repaint();
            }
        });
        PropertyChangeListener stateListener = event -> {
            String property = event.getPropertyName();
            if ("enabled".equals(property)
                    || "foreground".equals(property)
                    || "background".equals(property)) {
                component.repaint();
            }
        };
        component.addPropertyChangeListener(stateListener);
    }

    static void installTextComponent(JTextComponent component, String role) {
        install(component, role, true);
        KaylasTheme theme = theme();
        if (component.getCaretColor() == null) {
            component.setCaretColor(theme.focusRing());
        }
        if (component.getSelectionColor() == null) {
            component.setSelectionColor(theme.selectionBackground());
        }
        if (component.getSelectedTextColor() == null) {
            component.setSelectedTextColor(theme.selectionForeground());
        }
    }

    static void configureSpinnerEditor(JSpinner spinner) {
        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            return;
        }
        JFormattedTextField textField = editor.getTextField();
        KaylasTheme theme = theme();
        installTextComponent(textField, "spinnerEditor");
        textField.setBorder(new EmptyBorder(3, 7, 3, 7));
        textField.setOpaque(false);
        textField.setForeground(theme.foreground());
        textField.setDisabledTextColor(theme.disabledForeground());
        textField.setCaretColor(theme.focusRing());
        textField.setSelectionColor(theme.selectionBackground());
        textField.setSelectedTextColor(theme.selectionForeground());
    }

    static void paintFocusRing(JComponent component, Graphics graphics, int inset) {
        if (!component.isEnabled() || !hasFocusWithin(component)) {
            return;
        }
        KaylasTheme theme = theme();
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            graphics2D.setColor(withAlpha(theme.focusRing(), 225));
            graphics2D.setStroke(new BasicStroke(theme.focusWidth()));
            int halfStroke = Math.max(1, theme.focusWidth() / 2);
            int offset = Math.max(inset, halfStroke);
            int width = component.getWidth() - offset * 2 - 1;
            int height = component.getHeight() - offset * 2 - 1;
            if (width > 0 && height > 0) {
                int arc = Math.max(2, theme.arc() * 2);
                graphics2D.drawRoundRect(offset, offset, width, height, arc, arc);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    static boolean hasFocusWithin(JComponent component) {
        Component owner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .getFocusOwner();
        return owner == component
                || (owner != null && SwingUtilities.isDescendingFrom(owner, component));
    }

    static KaylasTheme theme() {
        return KaylasLookAndFeel.currentTheme();
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
