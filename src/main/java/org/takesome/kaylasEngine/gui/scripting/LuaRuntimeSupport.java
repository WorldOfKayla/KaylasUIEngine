package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.takesome.kaylasEngine.Engine;

import javax.swing.SwingUtilities;
import java.awt.Color;

final class LuaRuntimeSupport {
    private LuaRuntimeSupport() {
    }

    static LuaValue function(LuaCallback callback) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return callback.call(args);
            }
        };
    }

    static LuaValue arg(Varargs args, int logicalIndex) {
        int offset = hasSelf(args) ? 1 : 0;
        int actualIndex = logicalIndex + offset;
        return args.narg() >= actualIndex ? args.arg(actualIndex) : LuaValue.NIL;
    }

    static String stringArg(Varargs args, int logicalIndex, String fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.tojstring();
    }

    static int intArg(Varargs args, int logicalIndex, int fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.toint();
    }

    static boolean booleanArg(Varargs args, int logicalIndex, boolean fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.toboolean();
    }

    static Color colorArg(Varargs args, int logicalIndex, Color fallback) {
        String raw = stringArg(args, logicalIndex, null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Color.decode(raw.startsWith("#") ? raw : "#" + raw);
        } catch (NumberFormatException error) {
            Engine.LOGGER.warn("Invalid Lua UI color '{}'.", raw);
            return fallback;
        }
    }

    static LuaValue toLuaValue(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof LuaValue luaValue) {
            return luaValue;
        }
        if (value instanceof Boolean booleanValue) {
            return LuaValue.valueOf(booleanValue);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    static Object javaValue(LuaValue value) {
        if (value == null || value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            double doubleValue = value.todouble();
            if (doubleValue == Math.rint(doubleValue)) {
                return (int) doubleValue;
            }
            return doubleValue;
        }
        if (value.istable()) {
            return value;
        }
        return value.tojstring();
    }

    static LuaValue value(String value) {
        return value == null ? LuaValue.NIL : LuaValue.valueOf(value);
    }

    static String normalizeEventName(String eventName) {
        if (eventName == null) {
            return "";
        }
        String normalized = eventName.trim();
        if (normalized.equalsIgnoreCase("mouseClicked")) {
            return "click";
        }
        if (normalized.equalsIgnoreCase("mouseEntered")) {
            return "hover";
        }
        if (normalized.equalsIgnoreCase("mouseExited")) {
            return "hoverExit";
        }
        if (normalized.equalsIgnoreCase("focusGained")) {
            return "focus";
        }
        if (normalized.equalsIgnoreCase("focusLost")) {
            return "blur";
        }
        return normalized;
    }

    static String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    static boolean hasSelf(Varargs args) {
        return args.narg() > 0 && args.arg1().istable();
    }

    static LuaTable table() {
        return new LuaTable();
    }
}
