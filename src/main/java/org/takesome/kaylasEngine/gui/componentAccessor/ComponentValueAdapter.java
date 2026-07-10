package org.takesome.kaylasEngine.gui.componentAccessor;

import javax.swing.JComponent;

/**
 * Reads and optionally writes a semantic value for one Swing component type.
 *
 * @param <T> supported component class.
 */
public interface ComponentValueAdapter<T extends JComponent> {

    /**
     * Returns the component class accepted by this adapter.
     *
     * @return supported component type.
     */
    Class<T> componentType();

    /**
     * Reads the component's semantic value.
     *
     * @param component component instance.
     * @return native value; may be {@code null} when the control has no value.
     */
    Object read(T component);

    /**
     * Indicates whether {@link #write(JComponent, Object)} is supported.
     *
     * @return {@code true} when the adapter can apply values.
     */
    default boolean writable() {
        return false;
    }

    /**
     * Applies a semantic value to the component.
     *
     * @param component component instance.
     * @param value value to apply.
     * @throws UnsupportedOperationException when the adapter is read-only.
     */
    default void write(T component, Object value) {
        throw new UnsupportedOperationException(
                "Adapter for " + componentType().getName() + " is read-only"
        );
    }

    /**
     * Allows application adapters to override built-in adapters for the same assignable type.
     *
     * @return ordering priority; larger values are preferred.
     */
    default int priority() {
        return 0;
    }
}
