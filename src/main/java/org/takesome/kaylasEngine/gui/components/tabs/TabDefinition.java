package org.takesome.kaylasEngine.gui.components.tabs;

import javax.swing.Icon;

/** Immutable metadata for one tab. */
public record TabDefinition(
        String id,
        String title,
        Icon icon,
        boolean enabled,
        boolean visible
) {
    public TabDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tab id must not be blank");
        }
        id = id.trim();
        title = title == null ? id : title;
    }
}
