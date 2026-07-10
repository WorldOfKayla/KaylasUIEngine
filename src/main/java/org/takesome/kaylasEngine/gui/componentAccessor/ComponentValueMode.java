package org.takesome.kaylasEngine.gui.componentAccessor;

/**
 * Controls how values returned by {@link ComponentsAccessor} are represented.
 */
public enum ComponentValueMode {

    /**
     * Convert supported values to strings. This preserves the behavior of the pre-2.1 accessor.
     */
    STRING,

    /**
     * Return adapter-native values such as {@link Boolean}, {@link Integer}, or {@link String}.
     */
    NATIVE
}
