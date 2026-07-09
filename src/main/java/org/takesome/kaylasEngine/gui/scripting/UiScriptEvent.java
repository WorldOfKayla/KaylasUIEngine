package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaValue;

/**
 * Java-side view of a Lua UI event.
 *
 * <p>This event is emitted from Lua through {@code ui.emit(...)} or {@code component:emit(...)}
 * and delivered to Java listeners registered on {@link UiScriptContext}.</p>
 */
public record UiScriptEvent(
        String name,
        UiComponentApi source,
        Object rawEvent,
        LuaValue payload
) {
    public String sourceId() {
        return source == null ? null : source.id();
    }

    public String sourceType() {
        return source == null ? null : source.type();
    }

    public boolean hasPayload() {
        return payload != null && !payload.isnil();
    }
}
