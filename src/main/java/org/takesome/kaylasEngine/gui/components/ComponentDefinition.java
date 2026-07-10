package org.takesome.kaylasEngine.gui.components;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Extensible component type definition used by {@link ComponentFactory}.
 *
 * <p>Definitions are immutable and can be derived. A derived definition reuses the parent's creator,
 * default style and configurator chain, then appends its own configuration. This provides component
 * inheritance without forcing every custom component into a deep Java class hierarchy.</p>
 *
 * @param <T> Swing component produced by the definition.
 */
public final class ComponentDefinition<T extends JComponent> {
    private final String type;
    private final String defaultStyle;
    private final boolean applyBaseStyle;
    private final Set<String> aliases;
    private final Function<ComponentCreationContext, T> creator;
    private final List<BiConsumer<T, ComponentCreationContext>> configurators;

    private ComponentDefinition(Builder<T> builder) {
        this.type = requireIdentifier(builder.type, "type");
        this.defaultStyle = normalizeStyle(builder.defaultStyle);
        this.applyBaseStyle = builder.applyBaseStyle;
        this.aliases = Set.copyOf(builder.aliases);
        this.creator = Objects.requireNonNull(builder.creator, "creator");
        this.configurators = List.copyOf(builder.configurators);
    }

    public static <T extends JComponent> Builder<T> builder(String type) {
        return new Builder<>(type);
    }

    public Builder<T> derive(String derivedType) {
        Builder<T> builder = new Builder<>(derivedType);
        builder.defaultStyle = defaultStyle;
        builder.applyBaseStyle = applyBaseStyle;
        builder.creator = creator;
        builder.configurators.addAll(configurators);
        return builder;
    }

    public String type() {
        return type;
    }

    public String defaultStyle() {
        return defaultStyle;
    }

    public boolean applyBaseStyle() {
        return applyBaseStyle;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public int configuratorCount() {
        return configurators.size();
    }

    public T create(ComponentCreationContext context) {
        T component = Objects.requireNonNull(
                creator.apply(context),
                "Component creator returned null for type: " + type
        );
        for (BiConsumer<T, ComponentCreationContext> configurator : configurators) {
            configurator.accept(component, context);
        }
        return component;
    }

    private static String requireIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Component " + label + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeStyle(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    public static final class Builder<T extends JComponent> {
        private final String type;
        private String defaultStyle = "default";
        private boolean applyBaseStyle = true;
        private final Set<String> aliases = new LinkedHashSet<>();
        private Function<ComponentCreationContext, T> creator;
        private final List<BiConsumer<T, ComponentCreationContext>> configurators = new ArrayList<>();

        private Builder(String type) {
            this.type = type;
        }

        public Builder<T> defaultStyle(String defaultStyle) {
            this.defaultStyle = defaultStyle;
            return this;
        }

        /**
         * Enables or disables the factory's generic Swing foreground/background/font pass.
         * Specialized built-in components disable it because their dedicated styler owns visuals.
         */
        public Builder<T> applyBaseStyle(boolean applyBaseStyle) {
            this.applyBaseStyle = applyBaseStyle;
            return this;
        }

        public Builder<T> alias(String alias) {
            aliases.add(requireIdentifier(alias, "alias"));
            return this;
        }

        public Builder<T> aliases(String... aliases) {
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && !alias.isBlank()) {
                        alias(alias);
                    }
                }
            }
            return this;
        }

        public Builder<T> creator(Function<ComponentCreationContext, T> creator) {
            this.creator = Objects.requireNonNull(creator, "creator");
            return this;
        }

        public Builder<T> configure(BiConsumer<T, ComponentCreationContext> configurator) {
            configurators.add(Objects.requireNonNull(configurator, "configurator"));
            return this;
        }

        public Builder<T> configureFirst(BiConsumer<T, ComponentCreationContext> configurator) {
            configurators.add(0, Objects.requireNonNull(configurator, "configurator"));
            return this;
        }

        public ComponentDefinition<T> build() {
            return new ComponentDefinition<>(this);
        }
    }
}
