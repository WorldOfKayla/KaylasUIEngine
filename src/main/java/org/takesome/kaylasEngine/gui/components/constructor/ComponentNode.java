package org.takesome.kaylasEngine.gui.components.constructor;

import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.util.Objects;

/** Immutable child-node prototype inside a reusable composite definition. */
public record ComponentNode(String localId, ComponentAttributes prototype) {
    public ComponentNode {
        if (localId == null || localId.isBlank()) {
            throw new IllegalArgumentException("Composite node id must not be blank");
        }
        localId = localId.trim();
        prototype = Objects.requireNonNull(prototype, "prototype").copy();
        prototype.validateForCreation();
    }

    /** Returns a defensive copy so catalog prototypes cannot be mutated externally. */
    @Override
    public ComponentAttributes prototype() {
        return prototype.copy();
    }

    /** Returns an independent mutable descriptor for one composite instance. */
    public ComponentAttributes instantiate() {
        return prototype.copy();
    }
}
