package org.takesome.kaylasEngine.gui.components.tabs;

/** Listener for tab selection transitions. */
public interface TabChangeListener {
    default void tabChanging(TabChangeEvent event) {
    }

    default void tabChanged(TabChangeEvent event) {
    }
}
