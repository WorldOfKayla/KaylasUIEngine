package org.takesome.kaylasEngine.gui.scripting;

@FunctionalInterface
public interface UiScriptEventListener {
    void onEvent(UiScriptEvent event);
}
