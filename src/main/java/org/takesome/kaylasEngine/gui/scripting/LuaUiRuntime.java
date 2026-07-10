package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable Lua UI execution runtime.
 *
 * <p>A full LuaJ {@link Globals} graph and parsed script chunk used to be created for every mouse,
 * focus, key and value-change event. This runtime keeps one restricted library VM per UI engine and
 * caches immutable compiled prototypes by resource path. Each invocation receives a very small
 * isolated environment, preserving the original closure semantics for {@code ui.on(...)} handlers.</p>
 */
final class LuaUiRuntime {
    private final UiScriptContext context;
    private final Globals libraryGlobals;
    private final Map<String, Prototype> compiledScripts = new HashMap<>();

    private long executionCount;
    private long compilationCount;
    private long observedCacheGeneration;

    LuaUiRuntime(UiScriptContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.libraryGlobals = LuaRuntimeFactory.createLightweightGlobals();
        this.observedCacheGeneration = context.scriptCacheGeneration();
    }

    synchronized void execute(String scriptPath, LuaTable component, LuaTable event) {
        Objects.requireNonNull(scriptPath, "scriptPath");
        refreshCacheGeneration();
        Prototype prototype = compiledScripts.computeIfAbsent(scriptPath, this::compile);

        LuaTable environment = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", libraryGlobals);
        environment.setmetatable(metatable);
        environment.set("_G", environment);
        environment.set("component", component == null ? LuaValue.NIL : component);
        environment.set("event", event == null ? LuaValue.NIL : event);
        environment.set("ui", context.uiTable());
        environment.set("engine", context.engineTable());

        new LuaClosure(prototype, environment).call();
        executionCount++;
    }

    synchronized void clearCompiledScripts() {
        compiledScripts.clear();
        observedCacheGeneration = context.scriptCacheGeneration();
    }

    synchronized int compiledScriptCount() {
        return compiledScripts.size();
    }

    synchronized long executionCount() {
        return executionCount;
    }

    synchronized long compilationCount() {
        return compilationCount;
    }

    private Prototype compile(String scriptPath) {
        compilationCount++;
        LuaValue loaded = libraryGlobals.load(context.scriptSource(scriptPath), scriptPath);
        if (loaded instanceof LuaClosure closure) {
            return closure.p;
        }
        throw new IllegalStateException("Lua compiler returned a non-closure chunk for " + scriptPath);
    }

    private void refreshCacheGeneration() {
        long generation = context.scriptCacheGeneration();
        if (generation != observedCacheGeneration) {
            compiledScripts.clear();
            observedCacheGeneration = generation;
        }
    }
}
