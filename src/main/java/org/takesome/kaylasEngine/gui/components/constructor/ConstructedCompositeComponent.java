package org.takesome.kaylasEngine.gui.components.constructor;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime instance produced from a {@link CompositeComponentDefinition}. */
public final class ConstructedCompositeComponent extends CompositeComponent {
    public static final String SCOPE_PROPERTY = "kaylas.ui.composite.scope";
    public static final String LOCAL_ID_PROPERTY = "kaylas.ui.composite.localId";
    public static final String DEFINITION_PROPERTY = "kaylas.ui.composite.definition";

    private final CompositeComponentDefinition definition;
    private final String instanceId;
    private final Map<String, JComponent> nodes = new LinkedHashMap<>();
    private final List<AutoCloseable> signalConnections = new ArrayList<>();

    ConstructedCompositeComponent(CompositeComponentDefinition definition,
                                  String instanceId,
                                  LayoutMode layoutMode) {
        super(layoutMode);
        this.definition = Objects.requireNonNull(definition, "definition");
        this.instanceId = requireId(instanceId);
        setName(this.instanceId);
        putClientProperty(SCOPE_PROPERTY, this.instanceId);
        putClientProperty(LOCAL_ID_PROPERTY, ComponentConnection.ROOT);
        putClientProperty(DEFINITION_PROPERTY, definition.type());
    }

    public CompositeComponentDefinition definition() {
        return definition;
    }

    public String instanceId() {
        return instanceId;
    }

    public String qualify(String localId) {
        if (localId == null || localId.isBlank() || ComponentConnection.ROOT.equals(localId)) {
            return instanceId;
        }
        String normalized = localId.trim();
        if (normalized.equals(instanceId) || normalized.startsWith(instanceId + ".")) {
            return normalized;
        }
        return instanceId + "." + normalized;
    }

    public JComponent getNode(String localId) {
        if (localId == null || localId.isBlank() || ComponentConnection.ROOT.equals(localId)) {
            return this;
        }
        return nodes.get(localId.trim());
    }

    public Map<String, JComponent> nodes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    void addNode(String localId, JComponent component) {
        String normalized = requireId(localId);
        JComponent previous = nodes.put(normalized, Objects.requireNonNull(component, "component"));
        if (previous != null && previous != component) {
            removeSubComponent(previous);
        }
        component.putClientProperty(SCOPE_PROPERTY, instanceId);
        component.putClientProperty(LOCAL_ID_PROPERTY, normalized);
        component.putClientProperty(DEFINITION_PROPERTY, definition.type());
        addSubComponent(component);
    }

    void trackSignalConnection(AutoCloseable connection) {
        if (connection != null) {
            signalConnections.add(connection);
        }
    }

    @Override
    public void removeNotify() {
        closeSignalConnections();
        super.removeNotify();
    }

    public void closeSignalConnections() {
        for (AutoCloseable connection : List.copyOf(signalConnections)) {
            try {
                connection.close();
            } catch (Exception error) {
                Engine.LOGGER.warn(
                        "Unable to close signal connection for composite '{}'.",
                        instanceId,
                        error
                );
            }
        }
        signalConnections.clear();
    }

    private static String requireId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Composite instance id must not be blank");
        }
        return value.trim();
    }
}
