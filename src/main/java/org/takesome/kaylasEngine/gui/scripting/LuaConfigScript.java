package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.takesome.kaylasEngine.Engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic Lua config loader for application-owned UI scripts.
 *
 * The engine owns Lua execution and conversion mechanics. Applications own the script files and visual policy.
 */
public final class LuaConfigScript {
    private LuaConfigScript() {
    }

    public static Map<String, Object> load(UiScriptContext context, String scriptPath) {
        try {
            String source = context.scriptSource(scriptPath);
            Globals globals = JsePlatform.standardGlobals();
            globals.set("engine", context.engineTable());
            globals.set("ui", context.uiTable());

            LuaValue result = globals.load(source, scriptPath).call();
            if (!result.istable()) {
                Engine.getLOGGER().warn("Lua config script did not return a table: {}", scriptPath);
                return Map.of();
            }
            return toJavaMap(result.checktable());
        } catch (Exception error) {
            Engine.getLOGGER().warn("Unable to load Lua config script: {}", scriptPath, error);
            return Map.of();
        }
    }

    private static Map<String, Object> toJavaMap(LuaTable table) {
        Map<String, Object> map = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            var pair = table.next(key);
            key = pair.arg1();
            if (key.isnil()) {
                break;
            }
            LuaValue value = pair.arg(2);
            map.put(key.tojstring(), toJavaValue(value));
        }
        return map;
    }

    private static Object toJavaValue(LuaValue value) {
        if (value == null || value.isnil()) {
            return null;
        }
        if (value.istable()) {
            return toJavaMap(value.checktable());
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (number == Math.rint(number)) {
                return (int) number;
            }
            return number;
        }
        return value.tojstring();
    }
}
