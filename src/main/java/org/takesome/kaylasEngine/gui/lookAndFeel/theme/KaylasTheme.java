package org.takesome.kaylasEngine.gui.lookAndFeel.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * Immutable palette and metric contract used by Kaylas Look and Feel and its engine components.
 */
public record KaylasTheme(
        String name,
        Color background,
        Color surface,
        Color elevatedSurface,
        Color border,
        Color accent,
        Color accentHover,
        Color accentPressed,
        Color foreground,
        Color mutedForeground,
        Color disabledForeground,
        Color selectionBackground,
        Color selectionForeground,
        Color focusRing,
        Color success,
        Color danger,
        int arc,
        int focusWidth,
        int controlHeight,
        int scrollBarWidth
) {
    /** Validates and normalizes a complete theme definition. */
    public KaylasTheme {
        name = requireText(name, "name");
        background = Objects.requireNonNull(background, "background");
        surface = Objects.requireNonNull(surface, "surface");
        elevatedSurface = Objects.requireNonNull(elevatedSurface, "elevatedSurface");
        border = Objects.requireNonNull(border, "border");
        accent = Objects.requireNonNull(accent, "accent");
        accentHover = Objects.requireNonNull(accentHover, "accentHover");
        accentPressed = Objects.requireNonNull(accentPressed, "accentPressed");
        foreground = Objects.requireNonNull(foreground, "foreground");
        mutedForeground = Objects.requireNonNull(mutedForeground, "mutedForeground");
        disabledForeground = Objects.requireNonNull(disabledForeground, "disabledForeground");
        selectionBackground = Objects.requireNonNull(selectionBackground, "selectionBackground");
        selectionForeground = Objects.requireNonNull(selectionForeground, "selectionForeground");
        focusRing = Objects.requireNonNull(focusRing, "focusRing");
        success = Objects.requireNonNull(success, "success");
        danger = Objects.requireNonNull(danger, "danger");
        arc = positive(arc, "arc");
        focusWidth = positive(focusWidth, "focusWidth");
        controlHeight = positive(controlHeight, "controlHeight");
        scrollBarWidth = positive(scrollBarWidth, "scrollBarWidth");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
