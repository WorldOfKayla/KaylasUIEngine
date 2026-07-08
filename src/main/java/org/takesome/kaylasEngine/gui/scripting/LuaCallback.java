package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@FunctionalInterface
interface LuaCallback {
    LuaValue call(Varargs args);
}
