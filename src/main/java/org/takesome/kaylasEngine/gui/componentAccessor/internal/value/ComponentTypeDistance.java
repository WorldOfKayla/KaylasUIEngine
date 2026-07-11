package org.takesome.kaylasEngine.gui.componentAccessor.internal.value;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

/** Computes inheritance distance for semantic value-adapter resolution. */
public final class ComponentTypeDistance {
    private ComponentTypeDistance() {
    }

    public static int between(Class<?> actualType, Class<?> targetType) {
        if (actualType.equals(targetType)) {
            return 0;
        }
        Deque<TypeStep> queue = new ArrayDeque<>();
        Set<Class<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(new TypeStep(actualType, 0));
        visited.add(actualType);

        while (!queue.isEmpty()) {
            TypeStep current = queue.removeFirst();
            if (current.type().equals(targetType)) {
                return current.distance();
            }
            Class<?> superclass = current.type().getSuperclass();
            if (superclass != null && visited.add(superclass)) {
                queue.addLast(new TypeStep(superclass, current.distance() + 1));
            }
            for (Class<?> interfaceType : current.type().getInterfaces()) {
                if (visited.add(interfaceType)) {
                    queue.addLast(new TypeStep(interfaceType, current.distance() + 1));
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private record TypeStep(Class<?> type, int distance) {
    }
}
