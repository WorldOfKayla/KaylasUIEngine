package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.arg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.booleanArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.function;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.normalizeEventName;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.normalizeResourcePath;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.runOnEdt;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.stringArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.table;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.toLuaValue;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.value;

/**
 * Runtime context shared by Lua UI scripts.
 *
 * <p>This class owns component registry, script source cache and the custom Lua event bus exposed
 * through {@code ui.on(...)} and {@code ui.emit(...)}.</p>
 */
public final class UiScriptContext {
    private static final int MAX_EVENT_DEPTH = 8;

    private final Engine engine;
    private final Map<String, JComponent> componentsById = new ConcurrentHashMap<>();
    private final Map<String, UiComponentApi> componentApisById = new ConcurrentHashMap<>();
    private final Map<String, String> scriptSourceCache = new ConcurrentHashMap<>();
    private final Map<String, List<LuaValue>> eventSubscribers = new ConcurrentHashMap<>();
    private final Map<String, List<UiScriptEventListener>> javaEventSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, UiScriptEventListener>> namedJavaEventSubscribers = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> eventDepth = ThreadLocal.withInitial(() -> 0);

    public UiScriptContext(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public Engine engine() {
        return engine;
    }

    public UiComponentApi registerComponent(JComponent component, ComponentAttributes attributes) {
        UiComponentApi api = new UiComponentApi(this, component, attributes);
        String id = api.id();
        if (id != null && !id.isBlank()) {
            componentsById.put(id, component);
            componentApisById.put(id, api);
        }
        return api;
    }

    public JComponent findComponent(String componentId) {
        return componentsById.get(componentId);
    }

    public UiComponentApi findApi(String componentId) {
        return componentApisById.get(componentId);
    }

    public UiComponentApi apiFor(JComponent component, ComponentAttributes attributes) {
        if (component == null) {
            return null;
        }
        String componentId = componentId(component, attributes);
        if (componentId != null && componentApisById.containsKey(componentId)) {
            return componentApisById.get(componentId);
        }
        return new UiComponentApi(this, component, attributes);
    }

    public LuaTable componentTable(JComponent component, ComponentAttributes attributes) {
        UiComponentApi api = apiFor(component, attributes);
        return api == null ? table() : api.toLuaTable();
    }

    public Map<String, JComponent> componentsById() {
        return Collections.unmodifiableMap(componentsById);
    }

    public void clearScriptCache() {
        scriptSourceCache.clear();
    }

    public AutoCloseable on(String eventName, UiScriptEventListener listener) {
        String normalized = normalizeEventName(eventName);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Lua UI event name cannot be blank.");
        }
        Objects.requireNonNull(listener, "listener");
        List<UiScriptEventListener> listeners = javaEventSubscribers.computeIfAbsent(normalized, key -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public AutoCloseable on(String eventName, String listenerId, UiScriptEventListener listener) {
        String normalized = normalizeEventName(eventName);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Lua UI event name cannot be blank.");
        }
        if (listenerId == null || listenerId.isBlank()) {
            throw new IllegalArgumentException("Lua UI Java listener id cannot be blank.");
        }
        Objects.requireNonNull(listener, "listener");
        Map<String, UiScriptEventListener> listeners = namedJavaEventSubscribers.computeIfAbsent(normalized, key -> new ConcurrentHashMap<>());
        listeners.put(listenerId, listener);
        return () -> listeners.remove(listenerId, listener);
    }

    public String scriptSource(String scriptPath) {
        return scriptSourceCache.computeIfAbsent(scriptPath, this::loadScriptSource);
    }

    public boolean scriptExists(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return false;
        }
        String normalized = normalizeResourcePath(scriptPath);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = UiScriptContext.class.getClassLoader();
        }
        try (InputStream resource = classLoader.getResourceAsStream(normalized)) {
            if (resource != null) {
                return true;
            }
        } catch (IOException ignored) {
            return false;
        }
        Path path = Path.of(scriptPath);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir", ".")).resolve(path).normalize();
        }
        return Files.isRegularFile(path);
    }

    public void emit(String eventName, UiComponentApi source, LuaValue payload) {
        dispatch(eventName, source, null, payload == null ? LuaValue.NIL : payload);
    }

    public void dispatch(String eventName, UiComponentApi source, Object rawEvent, LuaValue payload) {
        String normalized = normalizeEventName(eventName);
        if (normalized.isBlank()) {
            return;
        }

        int depth = eventDepth.get();
        if (depth >= MAX_EVENT_DEPTH) {
            Engine.LOGGER.warn("Lua UI event '{}' ignored because max event depth was reached.", normalized);
            return;
        }

        LuaValue resolvedPayload = payload == null ? LuaValue.NIL : payload;
        List<UiScriptEventListener> javaSubscribers = javaSubscribersFor(normalized);
        List<LuaValue> luaSubscribers = subscribersFor(normalized);
        if (javaSubscribers.isEmpty() && luaSubscribers.isEmpty()) {
            return;
        }

        eventDepth.set(depth + 1);
        try {
            if (!javaSubscribers.isEmpty()) {
                UiScriptEvent javaEvent = new UiScriptEvent(normalized, source, rawEvent, resolvedPayload);
                for (UiScriptEventListener subscriber : javaSubscribers) {
                    try {
                        subscriber.onEvent(javaEvent);
                    } catch (Exception error) {
                        Engine.LOGGER.error("Java UI event subscriber failed for event '{}'.", normalized, error);
                    }
                }
            }

            if (!luaSubscribers.isEmpty()) {
                LuaTable event = eventTable(normalized, source, rawEvent, resolvedPayload);
                LuaValue component = source == null ? LuaValue.NIL : source.toLuaTable();
                for (LuaValue subscriber : luaSubscribers) {
                    if (subscriber != null && subscriber.isfunction()) {
                        subscriber.call(event, component);
                    }
                }
            }
        } catch (Exception error) {
            Engine.LOGGER.error("Lua UI event dispatch failed for event '{}'.", normalized, error);
        } finally {
            eventDepth.set(depth);
        }
    }

    public LuaTable eventTable(String eventName,
                               JComponent component,
                               ComponentAttributes attributes,
                               Object rawEvent,
                               LuaValue payload) {
        return eventTable(eventName, apiFor(component, attributes), rawEvent, payload);
    }

    public LuaTable eventTable(String eventName, UiComponentApi source, Object rawEvent, LuaValue payload) {
        LuaTable event = table();
        event.set("name", value(eventName));
        event.set("time", LuaValue.valueOf(Instant.now().toString()));
        event.set("payload", payload == null ? LuaValue.NIL : payload);

        if (source != null) {
            event.set("componentId", value(source.id()));
            event.set("componentType", value(source.type()));
            event.set("value", source.getValue());
        } else {
            event.set("componentId", LuaValue.NIL);
            event.set("componentType", LuaValue.NIL);
            event.set("value", LuaValue.NIL);
        }

        if (rawEvent instanceof MouseEvent mouseEvent) {
            event.set("x", LuaValue.valueOf(mouseEvent.getX()));
            event.set("y", LuaValue.valueOf(mouseEvent.getY()));
            event.set("button", LuaValue.valueOf(mouseEvent.getButton()));
            event.set("clickCount", LuaValue.valueOf(mouseEvent.getClickCount()));
            event.set("modifiers", LuaValue.valueOf(mouseEvent.getModifiersEx()));
        } else if (rawEvent instanceof KeyEvent keyEvent) {
            event.set("keyCode", LuaValue.valueOf(keyEvent.getKeyCode()));
            event.set("keyChar", LuaValue.valueOf(String.valueOf(keyEvent.getKeyChar())));
            event.set("modifiers", LuaValue.valueOf(keyEvent.getModifiersEx()));
        } else if (rawEvent instanceof FocusEvent focusEvent) {
            event.set("temporary", LuaValue.valueOf(focusEvent.isTemporary()));
        } else if (rawEvent instanceof DocumentEvent documentEvent) {
            event.set("offset", LuaValue.valueOf(documentEvent.getOffset()));
            event.set("length", LuaValue.valueOf(documentEvent.getLength()));
        }

        return event;
    }

    public LuaTable uiTable() {
        LuaTable ui = table();
        ui.set("log", function(args -> {
            Engine.LOGGER.info("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        ui.set("info", ui.get("log"));
        ui.set("warn", function(args -> {
            Engine.LOGGER.warn("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        ui.set("error", function(args -> {
            Engine.LOGGER.error("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        ui.set("find", function(this::luaFind));
        ui.set("has", function(this::luaHas));
        ui.set("show", function(this::luaShow));
        ui.set("hide", function(this::luaHide));
        ui.set("enable", function(this::luaEnable));
        ui.set("disable", function(this::luaDisable));
        ui.set("setVisible", function(this::luaSetVisible));
        ui.set("setEnabled", function(this::luaSetEnabled));
        ui.set("setText", function(this::luaSetText));
        ui.set("getText", function(this::luaGetText));
        ui.set("getValue", function(this::luaGetValue));
        ui.set("setValue", function(this::luaSetValue));
        ui.set("emit", function(this::luaEmit));
        ui.set("on", function(this::luaOn));
        ui.set("clearScriptCache", function(args -> {
            clearScriptCache();
            return LuaValue.NIL;
        }));
        return ui;
    }

    public LuaTable engineTable() {
        LuaTable table = table();
        table.set("appTitle", value(engine.getAppTitle()));
        table.set("runtime", value("KaylasUIEngine"));
        table.set("log", function(args -> {
            Engine.LOGGER.info("[lua-engine] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        table.set("lang", function(this::luaLang));
        table.set("langWith", function(this::luaLangWith));
        table.set("localeIndex", function(args -> LuaValue.valueOf(engine.getLANG().getLocaleIndex())));
        table.set("localeCount", function(args -> LuaValue.valueOf(engine.getLANG().getLocalesSet().length)));
        return table;
    }

    private LuaValue luaLang(Varargs args) {
        String key = stringArg(args, 1, "");
        if (key.isBlank()) {
            return LuaValue.valueOf("");
        }
        return value(engine.getLANG().getString(key));
    }

    private LuaValue luaLangWith(Varargs args) {
        String key = stringArg(args, 1, "");
        LuaValue replacements = arg(args, 2);
        if (key.isBlank()) {
            return LuaValue.valueOf("");
        }
        if (!replacements.istable()) {
            return value(engine.getLANG().getString(key));
        }

        java.util.List<String> replaceKeys = new java.util.ArrayList<>();
        java.util.List<String> replaceValues = new java.util.ArrayList<>();
        LuaValue nextKey = LuaValue.NIL;
        while (true) {
            org.luaj.vm2.Varargs pair = replacements.next(nextKey);
            nextKey = pair.arg1();
            if (nextKey.isnil()) {
                break;
            }
            LuaValue nextValue = pair.arg(2);
            replaceKeys.add(nextKey.tojstring());
            replaceValues.add(nextValue.isnil() ? "" : nextValue.tojstring());
        }
        return value(engine.getLANG().getStringWithKey(
                key,
                replaceKeys.toArray(String[]::new),
                replaceValues.toArray(String[]::new)
        ));
    }

    private LuaValue luaFind(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        return api == null ? LuaValue.NIL : api.toLuaTable();
    }

    private LuaValue luaHas(Varargs args) {
        return LuaValue.valueOf(findApi(stringArg(args, 1, "")) != null);
    }

    private LuaValue luaShow(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            runOnEdt(() -> api.component().setVisible(true));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaHide(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            runOnEdt(() -> api.component().setVisible(false));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaEnable(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            runOnEdt(() -> api.component().setEnabled(true));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaDisable(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            runOnEdt(() -> api.component().setEnabled(false));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaSetVisible(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        boolean visible = booleanArg(args, 2, true);
        if (api != null) {
            runOnEdt(() -> api.component().setVisible(visible));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaSetEnabled(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        boolean enabled = booleanArg(args, 2, true);
        if (api != null) {
            runOnEdt(() -> api.component().setEnabled(enabled));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaSetText(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            api.setText(stringArg(args, 2, ""));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaGetText(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        return api == null ? LuaValue.NIL : value(api.getText());
    }

    private LuaValue luaGetValue(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        return api == null ? LuaValue.NIL : api.getValue();
    }

    private LuaValue luaSetValue(Varargs args) {
        UiComponentApi api = findApi(stringArg(args, 1, ""));
        if (api != null) {
            api.setValue(arg(args, 2));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaEmit(Varargs args) {
        String eventName = stringArg(args, 1, "");
        LuaValue payload = arg(args, 2);
        if (!eventName.isBlank()) {
            emit(eventName, null, payload);
        }
        return LuaValue.NIL;
    }

    private LuaValue luaOn(Varargs args) {
        String eventName = normalizeEventName(stringArg(args, 1, ""));
        LuaValue handler = arg(args, 2);
        if (eventName.isBlank() || !handler.isfunction()) {
            Engine.LOGGER.warn("Invalid ui.on(...) registration: event='{}', handlerType='{}'", eventName, handler.typename());
            return LuaValue.NIL;
        }
        eventSubscribers.computeIfAbsent(eventName, key -> new CopyOnWriteArrayList<>()).add(handler);
        return LuaValue.NIL;
    }

    private List<LuaValue> subscribersFor(String eventName) {
        List<LuaValue> subscribers = new ArrayList<>();
        subscribers.addAll(eventSubscribers.getOrDefault(eventName, Collections.emptyList()));
        subscribers.addAll(eventSubscribers.getOrDefault("*", Collections.emptyList()));
        subscribers.addAll(eventSubscribers.getOrDefault("all", Collections.emptyList()));
        return subscribers;
    }

    private List<UiScriptEventListener> javaSubscribersFor(String eventName) {
        List<UiScriptEventListener> subscribers = new ArrayList<>();
        subscribers.addAll(javaEventSubscribers.getOrDefault(eventName, Collections.emptyList()));
        subscribers.addAll(javaEventSubscribers.getOrDefault("*", Collections.emptyList()));
        subscribers.addAll(javaEventSubscribers.getOrDefault("all", Collections.emptyList()));
        subscribers.addAll(namedSubscribersFor(eventName));
        subscribers.addAll(namedSubscribersFor("*"));
        subscribers.addAll(namedSubscribersFor("all"));
        return subscribers;
    }

    private List<UiScriptEventListener> namedSubscribersFor(String eventName) {
        Map<String, UiScriptEventListener> namedSubscribers = namedJavaEventSubscribers.get(eventName);
        if (namedSubscribers == null || namedSubscribers.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(namedSubscribers.values());
    }

    private String loadScriptSource(String scriptPath) {
        String normalized = normalizeResourcePath(scriptPath);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = UiScriptContext.class.getClassLoader();
        }

        try (InputStream resource = classLoader.getResourceAsStream(normalized)) {
            if (resource != null) {
                return readAll(resource);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read Lua resource: " + scriptPath, error);
        }

        Path path = Path.of(scriptPath);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir", ".")).resolve(path).normalize();
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Lua script was not found as classpath resource or file: " + scriptPath);
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read Lua script file: " + path, error);
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private String componentId(JComponent component, ComponentAttributes attributes) {
        if (attributes != null && attributes.getComponentId() != null && !attributes.getComponentId().isBlank()) {
            return attributes.getComponentId();
        }
        return component != null ? component.getName() : null;
    }
}
