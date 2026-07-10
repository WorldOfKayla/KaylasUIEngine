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
 * Definition for a Java-created basic component or a prebuilt composite implementation.
 *
 * <p>Definitions are immutable and can be derived. A derived definition reuses the parent's creator,
 * metadata and configurator chain, then appends its own configuration.</p>
 *
 * @param <T> Swing component produced by the definition.
 */
public final class ComponentDefinition<T extends JComponent> extends AbstractComponentDefinition<T> {
    private final Function<ComponentCreationContext, T> creator;
    private final List<BiConsumer<T, ComponentCreationContext>> configurators;

    private ComponentDefinition(Builder<T> builder) {
        super(
                builder.type,
                builder.kind,
                builder.defaultStyle,
                builder.applyBaseStyle,
                builder.aliases
        );
        this.creator = Objects.requireNonNull(builder.creator, "creator");
        this.configurators = List.copyOf(builder.configurators);
    }

    public static <T extends JComponent> Builder<T> builder(String type) {
        return new Builder<>(type);
    }

    public Builder<T> derive(String derivedType) {
        Builder<T> builder = new Builder<>(derivedType);
        builder.kind = kind();
        builder.defaultStyle = defaultStyle();
        builder.applyBaseStyle = applyBaseStyle();
        builder.creator = creator;
        builder.configurators.addAll(configurators);
        return builder;
    }

    public int configuratorCount() {
        return configurators.size();
    }

    @Override
    public T create(ComponentCreationContext context) {
        T component = Objects.requireNonNull(
                creator.apply(context),
                "Component creator returned null for type: " + type()
        );
        for (BiConsumer<T, ComponentCreationContext> configurator : configurators) {
            configurator.accept(component, context);
        }
        return component;
    }

    public static final class Builder<T extends JComponent> {
        private final String type;
        private ComponentKind kind = ComponentKind.BASIC;
        private String defaultStyle = "default";
        private boolean applyBaseStyle = true;
        private final Set<String> aliases = new LinkedHashSet<>();
        private Function<ComponentCreationContext, T> creator;
        private final List<BiConsumer<T, ComponentCreationContext>> configurators = new ArrayList<>();

        private Builder(String type) {
            this.type = type;
        }

        public Builder<T> kind(ComponentKind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
            return this;
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
