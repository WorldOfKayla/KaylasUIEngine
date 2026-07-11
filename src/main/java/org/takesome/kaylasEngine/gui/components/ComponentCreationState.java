package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.gui.styles.StyleAttributes;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-confined component-construction state used by {@link ComponentFactory}.
 *
 * <p>The state object owns nested creation contexts and temporary style overrides, keeping mutable
 * orchestration details out of the factory registry and public API.</p>
 */
final class ComponentCreationState {
    private final ThreadLocal<Deque<ComponentCreationContext>> creationStack =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<Deque<StyleAttributes>> scopedStyles =
            ThreadLocal.withInitial(ArrayDeque::new);

    private volatile StyleAttributes fallbackStyle = StyleAttributes.defaults("default");
    private volatile ComponentAttributes lastComponentAttributes;

    void enter(ComponentCreationContext context) {
        Objects.requireNonNull(context, "context");
        Deque<ComponentCreationContext> stack = creationStack.get();
        if (context.definition().kind() == ComponentKind.COMPOSITE
                && stack.stream().anyMatch(parent ->
                parent.definition().type().equalsIgnoreCase(context.definition().type()))) {
            throw new IllegalStateException(
                    "Recursive composite construction detected for type '"
                            + context.definition().type() + "'"
            );
        }
        stack.push(context);
        lastComponentAttributes = context.attributes();
    }

    void exit() {
        Deque<ComponentCreationContext> stack = creationStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            creationStack.remove();
        }
    }

    Optional<ComponentCreationContext> currentContext() {
        Deque<ComponentCreationContext> stack = creationStack.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    StyleAttributes currentStyle() {
        Deque<StyleAttributes> overrides = scopedStyles.get();
        if (!overrides.isEmpty()) {
            return overrides.peek();
        }
        return currentContext().map(ComponentCreationContext::style).orElse(fallbackStyle);
    }

    <T> T withStyle(StyleAttributes style, Supplier<T> action) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(action, "action");
        Deque<StyleAttributes> overrides = scopedStyles.get();
        overrides.push(style);
        try {
            return action.get();
        } finally {
            overrides.pop();
            if (overrides.isEmpty()) {
                scopedStyles.remove();
            }
        }
    }

    Rectangle currentBounds() {
        return currentContext()
                .map(context -> new Rectangle(context.bounds()))
                .orElseGet(Rectangle::new);
    }

    void setFallbackStyle(StyleAttributes style) {
        fallbackStyle = Objects.requireNonNull(style, "style");
    }

    ComponentAttributes currentAttributes() {
        return currentContext()
                .map(ComponentCreationContext::attributes)
                .orElse(lastComponentAttributes);
    }
}
