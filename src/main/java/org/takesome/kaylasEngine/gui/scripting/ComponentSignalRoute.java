package org.takesome.kaylasEngine.gui.scripting;

/** Immutable directed route between two registered UI components. */
public record ComponentSignalRoute(
        String id,
        String scopeId,
        String sourceId,
        String sourceEvent,
        String targetId,
        String targetEvent
) {
}
