package org.takesome.kaylasEngine.gui.components.textArea;

import javax.swing.JTextArea;
import java.awt.Color;

/** Regression verification for declarative text-area colors in disabled Swing state. */
public final class TextAreaColorVerification {
    private TextAreaColorVerification() {
    }

    public static void verify() {
        JTextArea textArea = new JTextArea();
        Color expected = new Color(0x7e, 0x7d, 0x7c);

        TextArea.applyTextColor(textArea, expected);
        textArea.setEnabled(false);

        require(expected.equals(textArea.getForeground()),
                "textArea foreground did not retain the resolved color");
        require(expected.equals(textArea.getDisabledTextColor()),
                "disabled textArea did not retain the resolved color");
    }

    public static void main(String[] args) {
        verify();
        System.out.println("TextArea color verification passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
