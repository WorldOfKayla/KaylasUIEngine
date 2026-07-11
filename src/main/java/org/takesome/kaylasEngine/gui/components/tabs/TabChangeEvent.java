package org.takesome.kaylasEngine.gui.components.tabs;

/** State transition emitted by the tabs component. */
public record TabChangeEvent(
        String previousTabId,
        String tabId,
        int index,
        String source
) {
}
