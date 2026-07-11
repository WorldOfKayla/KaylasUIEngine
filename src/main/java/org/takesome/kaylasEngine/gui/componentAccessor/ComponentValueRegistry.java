package org.takesome.kaylasEngine.gui.componentAccessor;

import org.takesome.kaylasEngine.gui.componentAccessor.internal.value.ComponentTypeDistance;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.value.DefaultComponentValueAdapters;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Ordered, thread-safe registry of semantic value adapters for Swing components.
 *
 * <p>Resolution prefers an exact runtime-class adapter, then the nearest assignable adapter, then
 * the adapter with the highest {@link ComponentValueAdapter#priority() priority}. Application code
 * can copy the default registry and replace or extend adapters without modifying engine classes.</p>
 */
public final class ComponentValueRegistry {
    private final CopyOnWriteArrayList<ComponentValueAdapter<? extends JComponent>> adapters;

    /** Creates an empty registry. */
    public ComponentValueRegistry() {
        this.adapters = new CopyOnWriteArrayList<>();
    }

    private ComponentValueRegistry(List<ComponentValueAdapter<? extends JComponent>> adapters) {
        this.adapters = new CopyOnWriteArrayList<>(adapters);
    }

    /**
     * Creates a registry containing adapters for standard Swing controls and KaylasUI composites.
     *
     * @return independent mutable registry.
     */
    public static ComponentValueRegistry defaults() {
        return DefaultComponentValueAdapters.createRegistry();
    }

    /**
     * Returns an independent registry containing the same adapter objects.
     *
     * @return mutable copy.
     */
    public ComponentValueRegistry copy() {
        return new ComponentValueRegistry(adapters);
    }

    /**
     * Registers or replaces an adapter for its declared component type.
     *
     * @param adapter adapter to register.
     * @param <T> component type.
     * @return this registry.
     */
    public <T extends JComponent> ComponentValueRegistry register(ComponentValueAdapter<T> adapter) {
        Objects.requireNonNull(adapter, "adapter");
        adapters.removeIf(existing -> existing.componentType().equals(adapter.componentType()));
        adapters.add(adapter);
        return this;
    }

    /**
     * Registers a read-only functional adapter.
     *
     * @param componentType supported component class.
     * @param reader semantic value reader.
     * @param <T> component type.
     * @return this registry.
     */
    public <T extends JComponent> ComponentValueRegistry register(
            Class<T> componentType,
            Function<? super T, ?> reader
    ) {
        return register(componentType, reader, 0);
    }

    /**
     * Registers a read-only functional adapter with explicit priority.
     *
     * @param componentType supported component class.
     * @param reader semantic value reader.
     * @param priority resolution priority.
     * @param <T> component type.
     * @return this registry.
     */
    public <T extends JComponent> ComponentValueRegistry register(
            Class<T> componentType,
            Function<? super T, ?> reader,
            int priority
    ) {
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(reader, "reader");
        return register(new FunctionalAdapter<>(componentType, reader, null, priority));
    }

    /**
     * Registers a readable and writable functional adapter.
     *
     * @param componentType supported component class.
     * @param reader semantic value reader.
     * @param writer semantic value writer.
     * @param <T> component type.
     * @return this registry.
     */
    public <T extends JComponent> ComponentValueRegistry registerWritable(
            Class<T> componentType,
            Function<? super T, ?> reader,
            BiConsumer<? super T, Object> writer
    ) {
        return registerWritable(componentType, reader, writer, 0);
    }

    /**
     * Registers a readable and writable functional adapter with explicit priority.
     *
     * @param componentType supported component class.
     * @param reader semantic value reader.
     * @param writer semantic value writer.
     * @param priority resolution priority.
     * @param <T> component type.
     * @return this registry.
     */
    public <T extends JComponent> ComponentValueRegistry registerWritable(
            Class<T> componentType,
            Function<? super T, ?> reader,
            BiConsumer<? super T, Object> writer,
            int priority
    ) {
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(writer, "writer");
        return register(new FunctionalAdapter<>(componentType, reader, writer, priority));
    }

    /**
     * Removes the adapter declared for an exact component class.
     *
     * @param componentType adapter component class.
     * @return {@code true} when an adapter was removed.
     */
    public boolean unregister(Class<? extends JComponent> componentType) {
        Objects.requireNonNull(componentType, "componentType");
        return adapters.removeIf(adapter -> adapter.componentType().equals(componentType));
    }

    /**
     * Resolves the best adapter for a component.
     *
     * @param component component instance.
     * @return matching adapter, or empty when unsupported.
     */
    public Optional<ComponentValueAdapter<? extends JComponent>> findAdapter(JComponent component) {
        Objects.requireNonNull(component, "component");
        Class<?> actualType = component.getClass();

        return adapters.stream()
                .filter(adapter -> adapter.componentType().isInstance(component))
                .min(Comparator
                        .comparingInt((ComponentValueAdapter<? extends JComponent> adapter) ->
                                ComponentTypeDistance.between(actualType, adapter.componentType()))
                        .thenComparing(
                                ComponentValueAdapter::priority,
                                Comparator.reverseOrder()
                        ));
    }

    /**
     * Tests whether a component has a value adapter.
     *
     * @param component component instance.
     * @return {@code true} when a readable adapter is available.
     */
    public boolean supports(JComponent component) {
        return component != null && findAdapter(component).isPresent();
    }

    /**
     * Reads a native value.
     *
     * @param component component instance.
     * @return adapter-native value.
     * @throws ComponentAccessException when no adapter exists or reading fails.
     */
    public Object read(JComponent component) {
        ComponentValueAdapter<? extends JComponent> adapter = findAdapter(component)
                .orElseThrow(() -> new ComponentAccessException(
                        "No value adapter registered for " + component.getClass().getName()
                ));
        try {
            return readUnchecked(adapter, component);
        } catch (RuntimeException error) {
            throw new ComponentAccessException(
                    "Unable to read component value from " + component.getClass().getName(),
                    error
            );
        }
    }

    /**
     * Writes a semantic value.
     *
     * @param component component instance.
     * @param value value to apply.
     * @throws ComponentAccessException when no writable adapter exists or writing fails.
     */
    public void write(JComponent component, Object value) {
        ComponentValueAdapter<? extends JComponent> adapter = findAdapter(component)
                .orElseThrow(() -> new ComponentAccessException(
                        "No value adapter registered for " + component.getClass().getName()
                ));
        if (!adapter.writable()) {
            throw new ComponentAccessException(
                    "Value adapter for " + adapter.componentType().getName() + " is read-only"
            );
        }
        try {
            writeUnchecked(adapter, component, value);
        } catch (RuntimeException error) {
            throw new ComponentAccessException(
                    "Unable to write component value for " + component.getClass().getName(),
                    error
            );
        }
    }

    /**
     * Returns an immutable snapshot of registered adapters.
     *
     * @return adapter list in registration order.
     */
    public List<ComponentValueAdapter<? extends JComponent>> adapters() {
        return Collections.unmodifiableList(new ArrayList<>(adapters));
    }

    @SuppressWarnings("unchecked")
    private static <T extends JComponent> Object readUnchecked(
            ComponentValueAdapter<? extends JComponent> adapter,
            JComponent component
    ) {
        return ((ComponentValueAdapter<T>) adapter).read((T) component);
    }

    @SuppressWarnings("unchecked")
    private static <T extends JComponent> void writeUnchecked(
            ComponentValueAdapter<? extends JComponent> adapter,
            JComponent component,
            Object value
    ) {
        ((ComponentValueAdapter<T>) adapter).write((T) component, value);
    }

    private record FunctionalAdapter<T extends JComponent>(
            Class<T> componentType,
            Function<? super T, ?> reader,
            BiConsumer<? super T, Object> writer,
            int priority
    ) implements ComponentValueAdapter<T> {
        private FunctionalAdapter {
            Objects.requireNonNull(componentType, "componentType");
            Objects.requireNonNull(reader, "reader");
        }

        @Override
        public Object read(T component) {
            return reader.apply(component);
        }

        @Override
        public boolean writable() {
            return writer != null;
        }

        @Override
        public void write(T component, Object value) {
            if (writer == null) {
                ComponentValueAdapter.super.write(component, value);
                return;
            }
            writer.accept(component, value);
        }
    }
}
