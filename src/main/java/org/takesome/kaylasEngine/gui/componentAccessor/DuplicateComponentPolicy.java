package org.takesome.kaylasEngine.gui.componentAccessor;

/**
 * Defines how {@link ComponentsAccessor} handles multiple named components with the same id.
 */
public enum DuplicateComponentPolicy {

    /** Keep the first component and ignore later duplicates. */
    KEEP_FIRST,

    /** Replace the previously indexed component with the most recently discovered instance. */
    REPLACE,

    /** Fail refresh immediately with a {@link ComponentAccessException}. */
    FAIL
}
