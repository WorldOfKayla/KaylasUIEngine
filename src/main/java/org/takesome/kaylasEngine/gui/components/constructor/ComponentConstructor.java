package org.takesome.kaylasEngine.gui.components.constructor;

import org.takesome.kaylasEngine.gui.components.AbstractComponentDefinition;
import org.takesome.kaylasEngine.gui.components.ComponentCatalog;
import org.takesome.kaylasEngine.gui.components.ComponentCreationContext;
import org.takesome.kaylasEngine.gui.components.ComponentDefinition;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.config.ComponentConfigGroupRegistry;
import org.takesome.kaylasEngine.gui.config.ComponentConfigResolver;

import javax.swing.JComponent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Launcher-facing constructor for reusable basic and composite component types. */
public final class ComponentConstructor {
    private final ComponentFactory factory;
    private final ComponentCatalog catalog;

    public ComponentConstructor(ComponentFactory factory, ComponentCatalog catalog) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public CompositeComponentDefinition.Builder composite(String type) {
        return CompositeComponentDefinition.builder(type);
    }

    public <T extends JComponent> ComponentDefinition.Builder<T> basic(String type) {
        return ComponentDefinition.builder(type);
    }

    public <T extends JComponent> ComponentDefinition<T> basic(
            String type,
            Function<ComponentCreationContext, T> creator
    ) {
        return ComponentDefinition.<T>builder(type)
                .creator(creator)
                .build();
    }

    public <D extends AbstractComponentDefinition<? extends JComponent>> D register(D definition) {
        Objects.requireNonNull(definition, "definition");
        if (definition instanceof CompositeComponentDefinition composite) {
            validateComposite(catalog, composite);
        }
        factory.registerDefinition(definition);
        return definition;
    }

    public CompositeComponentDefinition register(CompositeComponentDefinition.Builder builder) {
        return register(Objects.requireNonNull(builder, "builder").build());
    }

    public ComponentCatalog catalog() {
        return catalog;
    }

    public ComponentFactory factory() {
        return factory;
    }

    public ComponentConfigGroupRegistry configGroups() {
        return factory.getConfigGroupRegistry();
    }

    public ComponentConfigResolver configResolver() {
        return factory.getComponentConfigResolver();
    }

    /**
     * Validates child availability and rejects direct or indirect recursive component graphs.
     */
    public static void validateComposite(ComponentCatalog catalog,
                                         CompositeComponentDefinition composite) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(composite, "composite");

        for (ComponentNode node : composite.nodes()) {
            String childType = node.prototype().getComponentType();
            if (composite.type().equalsIgnoreCase(childType)) {
                throw new IllegalArgumentException(
                        "Composite '" + composite.type() + "' cannot contain itself directly"
                );
            }
            if (!catalog.contains(childType)) {
                throw new IllegalArgumentException(
                        "Composite '" + composite.type() + "' references unknown child type '"
                                + childType + "'"
                );
            }
            if (reachesType(catalog, childType, composite.type(), composite, new HashSet<>())) {
                throw new IllegalArgumentException(
                        "Composite '" + composite.type() + "' introduces a recursive dependency through '"
                                + childType + "'"
                );
            }
        }
    }

    private static boolean reachesType(ComponentCatalog catalog,
                                       String currentType,
                                       String targetType,
                                       CompositeComponentDefinition candidate,
                                       Set<String> visiting) {
        if (currentType.equalsIgnoreCase(targetType)) {
            return true;
        }

        String key = currentType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!visiting.add(key)) {
            return false;
        }
        try {
            AbstractComponentDefinition<? extends JComponent> definition =
                    currentType.equalsIgnoreCase(candidate.type())
                            ? candidate
                            : catalog.find(currentType).orElse(null);
            if (!(definition instanceof CompositeComponentDefinition composite)) {
                return false;
            }
            for (ComponentNode node : composite.nodes()) {
                if (reachesType(
                        catalog,
                        node.prototype().getComponentType(),
                        targetType,
                        candidate,
                        visiting
                )) {
                    return true;
                }
            }
            return false;
        } finally {
            visiting.remove(key);
        }
    }
}
