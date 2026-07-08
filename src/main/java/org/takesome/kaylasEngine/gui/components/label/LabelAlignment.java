package org.takesome.kaylasEngine.gui.components.label;

import javax.swing.SwingConstants;
public enum LabelAlignment {
    LEFT(SwingConstants.LEFT),
    CENTER(SwingConstants.CENTER),
    RIGHT(SwingConstants.RIGHT);

    private final int alignment;

    LabelAlignment(int alignment) {
        this.alignment = alignment;
    }

    public int getType() {
        return alignment;
    }

    public static LabelAlignment fromString(String text) {
        return switch (text.toUpperCase()) {
            case "LEFT" -> LEFT;
            case "CENTER" -> CENTER;
            case "RIGHT" -> RIGHT;
            default -> throw new IllegalArgumentException("Invalid alignment value: " + text);
        };
    }
}
