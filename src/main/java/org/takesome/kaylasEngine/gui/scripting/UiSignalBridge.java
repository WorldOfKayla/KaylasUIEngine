package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaValue;

/** Bridge exposed to Lua for directed component-to-component signals. */
interface UiSignalBridge {
    String connect(String sourceId,
                   String sourceEvent,
                   String targetId,
                   String targetEvent,
                   String scopeId);

    boolean disconnect(String routeId);

    boolean send(String targetId, String eventName, LuaValue payload);
}
