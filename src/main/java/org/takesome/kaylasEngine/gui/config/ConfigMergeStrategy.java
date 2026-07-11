package org.takesome.kaylasEngine.gui.config;

import java.util.Locale;

/** Merge policy for collection-valued component configuration fragments. */
public enum ConfigMergeStrategy {
    REPLACE,
    APPEND,
    PREPEND,
    UNIQUE_APPEND;

    public static ConfigMergeStrategy from(Object value) {
        if (value == null) {
            return REPLACE;
        }
        String normalized = String.valueOf(value)
                .trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return ConfigMergeStrategy.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return REPLACE;
        }
    }
}
