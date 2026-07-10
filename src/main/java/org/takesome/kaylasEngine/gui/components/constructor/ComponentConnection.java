package org.takesome.kaylasEngine.gui.components.constructor;

/**
 * Declarative signal route between two nodes of one composite instance.
 *
 * <p>Use {@code $root} to address the constructed composite root.</p>
 */
public record ComponentConnection(
        String sourceNode,
        String sourceEvent,
        String targetNode,
        String targetEvent
) {
    public static final String ROOT = "$root";

    public ComponentConnection {
        sourceNode = require(sourceNode, "source node");
        sourceEvent = require(sourceEvent, "source event");
        targetNode = require(targetNode, "target node");
        targetEvent = require(targetEvent, "target event");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Component connection " + label + " must not be blank");
        }
        return value.trim();
    }
}
