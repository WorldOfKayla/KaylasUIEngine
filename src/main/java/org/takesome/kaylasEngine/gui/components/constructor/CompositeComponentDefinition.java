package org.takesome.kaylasEngine.gui.components.constructor;

import org.takesome.kaylasEngine.gui.components.AbstractComponentDefinition;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentCreationContext;
import org.takesome.kaylasEngine.gui.components.ComponentKind;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reusable component graph assembled entirely from other catalog component types.
 */
public final class CompositeComponentDefinition
        extends AbstractComponentDefinition<ConstructedCompositeComponent> {
    private final CompositeComponent.LayoutMode layoutMode;
    private final List<ComponentNode> nodes;
    private final List<ComponentConnection> connections;
    private final AtomicLong instanceSequence = new AtomicLong();

    private CompositeComponentDefinition(Builder builder) {
        super(
                builder.type,
                ComponentKind.COMPOSITE,
                builder.defaultStyle,
                false,
                builder.aliases
        );
        this.layoutMode = builder.layoutMode;
        this.nodes = List.copyOf(builder.nodes.values());
        this.connections = List.copyOf(builder.connections);
        validateConnections(builder.nodes.keySet(), connections);
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public CompositeComponent.LayoutMode layoutMode() {
        return layoutMode;
    }

    public List<ComponentNode> nodes() {
        return nodes;
    }

    public List<ComponentConnection> connections() {
        return connections;
    }

    @Override
    public ConstructedCompositeComponent create(ComponentCreationContext context) {
        String instanceId = resolveInstanceId(context.attributes());
        if (context.attributes().getComponentId() == null
                || context.attributes().getComponentId().isBlank()) {
            context.attributes().setComponentId(instanceId);
        }
        ConstructedCompositeComponent composite = new ConstructedCompositeComponent(
                this,
                instanceId,
                layoutMode
        );
        composite.setLayoutConfig(context.attributes().getLayoutConfig());
        composite.setValue(context.attributes().getInitialValue());

        Rectangle rootBounds = context.bounds();
        if (rootBounds.width > 0 && rootBounds.height > 0) {
            Dimension size = new Dimension(rootBounds.width, rootBounds.height);
            composite.setPreferredSize(size);
            composite.setMinimumSize(size);
            composite.setSize(size);
        }

        for (ComponentNode node : nodes) {
            ComponentAttributes childAttributes = node.instantiate();
            childAttributes.setComponentId(composite.qualify(node.localId()));
            childAttributes.putProperty(ConstructedCompositeComponent.SCOPE_PROPERTY, instanceId);
            childAttributes.putProperty(ConstructedCompositeComponent.LOCAL_ID_PROPERTY, node.localId());
            childAttributes.putProperty(ConstructedCompositeComponent.DEFINITION_PROPERTY, type());

            JComponent child = context.factory().createComponent(childAttributes);
            composite.addNode(node.localId(), child);
        }

        for (ComponentConnection connection : connections) {
            AutoCloseable route = context.factory().getLuaUiScriptEngine().connectComponents(
                    composite.qualify(connection.sourceNode()),
                    connection.sourceEvent(),
                    composite.qualify(connection.targetNode()),
                    connection.targetEvent(),
                    instanceId
            );
            composite.trackSignalConnection(route);
        }
        composite.trackSignalConnection(
                () -> context.factory().getLuaUiScriptEngine().releaseScope(instanceId)
        );
        return composite;
    }

    private String resolveInstanceId(ComponentAttributes attributes) {
        String configured = attributes.getComponentId();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return type() + "-" + instanceSequence.incrementAndGet();
    }

    private static void validateConnections(Set<String> nodeIds,
                                            List<ComponentConnection> connections) {
        for (ComponentConnection connection : connections) {
            validateEndpoint(connection.sourceNode(), nodeIds, "source");
            validateEndpoint(connection.targetNode(), nodeIds, "target");
        }
    }

    private static void validateEndpoint(String nodeId, Set<String> nodes, String label) {
        if (!ComponentConnection.ROOT.equals(nodeId) && !nodes.contains(nodeId)) {
            throw new IllegalArgumentException(
                    "Composite connection " + label + " node '" + nodeId + "' is not defined"
            );
        }
    }

    public static final class Builder {
        private final String type;
        private String defaultStyle = "default";
        private CompositeComponent.LayoutMode layoutMode = CompositeComponent.LayoutMode.ABSOLUTE;
        private final Set<String> aliases = new LinkedHashSet<>();
        private final Map<String, ComponentNode> nodes = new LinkedHashMap<>();
        private final List<ComponentConnection> connections = new ArrayList<>();

        private Builder(String type) {
            this.type = requireIdentifier(type, "type");
        }

        public Builder defaultStyle(String defaultStyle) {
            this.defaultStyle = defaultStyle;
            return this;
        }

        public Builder layout(CompositeComponent.LayoutMode layoutMode) {
            this.layoutMode = Objects.requireNonNull(layoutMode, "layoutMode");
            return this;
        }

        public Builder alias(String alias) {
            aliases.add(requireIdentifier(alias, "alias"));
            return this;
        }

        public Builder aliases(String... aliases) {
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && !alias.isBlank()) {
                        alias(alias);
                    }
                }
            }
            return this;
        }

        public Builder child(String localId, ComponentAttributes prototype) {
            ComponentNode node = new ComponentNode(localId, prototype);
            if (nodes.putIfAbsent(node.localId(), node) != null) {
                throw new IllegalArgumentException(
                        "Composite node '" + node.localId() + "' is already defined"
                );
            }
            return this;
        }

        public Builder connect(String sourceNode,
                               String sourceEvent,
                               String targetNode,
                               String targetEvent) {
            connections.add(new ComponentConnection(
                    sourceNode,
                    sourceEvent,
                    targetNode,
                    targetEvent
            ));
            return this;
        }

        public CompositeComponentDefinition build() {
            return new CompositeComponentDefinition(this);
        }
    }
}
