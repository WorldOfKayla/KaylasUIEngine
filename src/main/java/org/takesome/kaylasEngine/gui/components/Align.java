package org.takesome.kaylasEngine.gui.components;

import java.util.Locale;

public enum Align {
    LEFT,
    CENTER,
    RIGHT;

    public static Align from(String value) {
        if (value == null || value.isBlank()) {
            return LEFT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("CENTRE".equals(normalized) || "MIDDLE".equals(normalized)) {
            normalized = "CENTER";
        }
        try {
            return Align.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return LEFT;
        }
    }
}
