package org.takesome.kaylasEngine.gui.componentAccessor.internal.binding;
import org.takesome.kaylasEngine.gui.componentAccessor.Component;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessException;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.support.ComponentIds;

import javax.swing.JComponent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Injects {@link Component}-annotated fields from a detached component index.
 *
 * <p>Reflection and binding validation live here so {@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor} can remain an index
 * facade instead of also owning field-introspection policy.</p>
 */
final class ReflectionComponentFieldBinder implements ComponentFieldBinding {

    @Override
    public void bind(Object target,
              Class<?> stopBefore,
              Map<String, JComponent> components) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(stopBefore, "stopBefore");
        Objects.requireNonNull(components, "components");

        for (Class<?> current = target.getClass();
             current != null && current != stopBefore;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Component binding = field.getAnnotation(Component.class);
                if (binding != null) {
                    bindField(target, field, binding, components);
                }
            }
        }
    }

    private void bindField(Object target,
                           Field field,
                           Component binding,
                           Map<String, JComponent> components) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new ComponentAccessException(
                    "@Component cannot be applied to static field: " + field
            );
        }
        if (Modifier.isFinal(field.getModifiers())) {
            throw new ComponentAccessException(
                    "@Component cannot inject final field: " + field
            );
        }

        String componentId = bindingId(field, binding);
        JComponent component = components.get(componentId);
        boolean optionalField = Optional.class.equals(field.getType());

        if (component == null && binding.required()) {
            throw new ComponentAccessException(
                    "Required component '" + componentId + "' not found for field "
                            + field.getDeclaringClass().getName() + "." + field.getName()
            );
        }

        Object injectionValue;
        if (optionalField) {
            validateOptionalType(field, component, componentId);
            injectionValue = Optional.ofNullable(component);
        } else {
            if (component == null) {
                return;
            }
            if (!field.getType().isInstance(component)) {
                throw new ComponentAccessException(
                        "Component '" + componentId + "' has type "
                                + component.getClass().getName() + ", but field "
                                + field.getDeclaringClass().getName() + "." + field.getName()
                                + " expects " + field.getType().getName()
                );
            }
            injectionValue = component;
        }

        boolean accessible = field.canAccess(target);
        try {
            if (!accessible && !field.trySetAccessible()) {
                throw new ComponentAccessException("Cannot access component field: " + field);
            }
            field.set(target, injectionValue);
        } catch (IllegalAccessException error) {
            throw new ComponentAccessException(
                    "Unable to inject component '" + componentId + "' into field " + field,
                    error
            );
        } finally {
            if (!accessible) {
                try {
                    field.setAccessible(false);
                } catch (RuntimeException ignored) {
                    // Access was already used successfully; restoration is best effort.
                }
            }
        }
    }

    private void validateOptionalType(Field field,
                                      JComponent component,
                                      String componentId) {
        if (component == null) {
            return;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class<?> expectedType)) {
            return;
        }
        if (!expectedType.isInstance(component)) {
            throw new ComponentAccessException(
                    "Component '" + componentId + "' has type "
                            + component.getClass().getName() + ", but Optional field " + field
                            + " expects " + expectedType.getName()
            );
        }
    }

    private String bindingId(Field field, Component binding) {
        String scope = ComponentIds.normalize(binding.scope());
        String localId = ComponentIds.normalize(binding.localId());
        if (localId != null) {
            if (scope == null) {
                throw new ComponentAccessException(
                        "@Component localId requires a non-blank scope on field " + field
                );
            }
            return ComponentIds.qualify(scope, localId);
        }

        String value = ComponentIds.normalize(binding.value());
        String baseId = value == null ? field.getName() : value;
        if (scope == null || baseId.equals(scope) || baseId.startsWith(scope + ".")) {
            return baseId;
        }
        return ComponentIds.qualify(scope, baseId);
    }
}
