package org.takesome.kaylasEngine.gui.componentAccessor;

/**
 * Defines how form collection handles components without a registered value adapter.
 */
public enum UnsupportedValuePolicy {

    /** Exclude unsupported components from the resulting form map. */
    SKIP,

    /** Insert an empty string for unsupported components, preserving legacy behavior. */
    EMPTY_STRING,

    /** Fail value collection with a {@link ComponentAccessException}. */
    FAIL
}
