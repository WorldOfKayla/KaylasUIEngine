package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * Builds the restricted Lua runtime used by UI scripts.
 *
 * <p>The UI layer does not need filesystem, process, operating-system or Java-reflection libraries.
 * Avoiding those libraries makes VM creation cheaper, reduces retained runtime state and keeps the
 * scripting surface intentionally small.</p>
 */
final class LuaRuntimeFactory {
    private LuaRuntimeFactory() {
    }

    static Globals createLightweightGlobals() {
        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        LoadState.install(globals);
        LuaC.install(globals);
        return globals;
    }
}
