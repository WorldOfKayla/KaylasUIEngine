package org.takesome.kaylasEngine.gui.componentAccessor;

/**
 * Signals an invalid component index, field binding, value conversion, or lookup operation.
 */
public class ComponentAccessException extends RuntimeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message diagnostic message.
     */
    public ComponentAccessException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a diagnostic message and root cause.
     *
     * @param message diagnostic message.
     * @param cause root cause.
     */
    public ComponentAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
