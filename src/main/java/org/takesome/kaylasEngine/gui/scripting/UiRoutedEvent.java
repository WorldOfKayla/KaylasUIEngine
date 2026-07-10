package org.takesome.kaylasEngine.gui.scripting;

/** Metadata attached to a signal forwarded between components. */
public record UiRoutedEvent(
        String routeId,
        String scopeId,
        String sourceComponentId,
        String sourceEvent,
        String targetComponentId,
        String targetEvent
) {
}
