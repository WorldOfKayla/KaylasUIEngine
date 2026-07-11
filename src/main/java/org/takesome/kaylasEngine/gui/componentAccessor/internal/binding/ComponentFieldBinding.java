package org.takesome.kaylasEngine.gui.componentAccessor.internal.binding;

import javax.swing.JComponent;
import java.util.Map;

/**
 * Internal boundary for annotation-driven component field injection.
 *
 * <p>The reflection implementation remains package-private; callers depend only on this narrow
 * operation contract.</p>
 */
public interface ComponentFieldBinding {
    static ComponentFieldBinding create() {
        return new ReflectionComponentFieldBinder();
    }

    void bind(Object target, Class<?> stopBefore, Map<String, JComponent> components);
}
