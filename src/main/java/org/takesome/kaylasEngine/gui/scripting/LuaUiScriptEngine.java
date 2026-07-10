package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.normalizeEventName;

/**
 * Lua-backed UI scripting bridge for KaylasUIEngine components.
 *
 * <p>The engine handles component lifecycle, listener installation and script execution. Every
 * component receives engine built-in scripts first; descriptor-level scripts are treated as
 * component-specific extensions.</p>
 */
@SuppressWarnings("unused")
public final class LuaUiScriptEngine implements UiSignalBridge {
    private static final String SCRIPT_BOUND_KEY = "kaylas.ui.lua.bound";
    private static final String SCRIPT_MAP_KEY = "kaylas.ui.lua.scripts";
    private static final String ATTRIBUTES_KEY = "kaylas.ui.lua.attributes";
    private static final String BUILTIN_COMPONENT_SCRIPT = "assets/scripts/builtin/component.lua";
    private static final String BUILTIN_COMPONENT_SCRIPT_ROOT = "assets/scripts/builtin/components/";
    private static final int MAX_REENTRANT_DEPTH = 8;

    private final UiScriptContext context;
    private final LuaUiRuntime runtime;
    private final ComponentSignalRouter signalRouter = new ComponentSignalRouter();
    private final ThreadLocal<Integer> eventDepth = ThreadLocal.withInitial(() -> 0);

    public LuaUiScriptEngine(Engine engine) {
        this.context = new UiScriptContext(Objects.requireNonNull(engine, "engine"));
        this.runtime = new LuaUiRuntime(context);
        this.context.setSignalBridge(this);
    }

    /**
     * Registers a component and installs Lua event listeners.
     *
     * <p>Binding no longer depends on descriptor scripts. The built-in engine script is attached to
     * every component, so all components participate in the Lua UI runtime by default.</p>
     *
     * @param component  created Swing component.
     * @param attributes source component attributes.
     */
    public void bind(JComponent component, ComponentAttributes attributes) {
        if (component == null || attributes == null) {
            return;
        }

        context.registerComponent(component, attributes);
        component.putClientProperty(ATTRIBUTES_KEY, attributes);

        Map<String, List<String>> scripts = collectScripts(attributes);
        component.putClientProperty(SCRIPT_MAP_KEY, scripts);

        if (!Boolean.TRUE.equals(component.getClientProperty(SCRIPT_BOUND_KEY))) {
            installCommonListeners(component);
            component.putClientProperty(SCRIPT_BOUND_KEY, Boolean.TRUE);
        }

        emit("init", component, attributes, null);
    }

    public UiScriptContext getContext() {
        return context;
    }

    public JComponent findComponent(String componentId) {
        return context.findComponent(componentId);
    }

    public Map<String, JComponent> getComponentsById() {
        return context.componentsById();
    }


    public void executeScript(String scriptPath, Map<String, ?> payload) {
        LuaTable event = new LuaTable();
        event.set("name", LuaValue.valueOf("script"));
        event.set("payload", LuaRuntimeSupport.toLuaValue(payload == null ? Map.of() : payload));
        runtime.execute(scriptPath, new LuaTable(), event);
    }

    public void clearScriptCache() {
        context.clearScriptCache();
        runtime.clearCompiledScripts();
    }

    public int getCompiledScriptCount() {
        return runtime.compiledScriptCount();
    }

    public long getScriptExecutionCount() {
        return runtime.executionCount();
    }

    public long getScriptCompilationCount() {
        return runtime.compilationCount();
    }

    public void emitComponentEvent(String eventName, JComponent component, Object rawEvent) {
        emit(eventName, component, rawEvent);
    }

    public ComponentSignalRouter.Connection connectComponents(String sourceId,
                                                              String sourceEvent,
                                                              String targetId,
                                                              String targetEvent,
                                                              String scopeId) {
        return signalRouter.connect(sourceId, sourceEvent, targetId, targetEvent, scopeId);
    }

    public int disconnectSignalScope(String scopeId) {
        return signalRouter.disconnectScope(scopeId);
    }

    public int releaseScope(String scopeId) {
        int disconnectedRoutes = signalRouter.disconnectScope(scopeId);
        int releasedSubscriptions = context.releaseScope(scopeId);
        return disconnectedRoutes + releasedSubscriptions;
    }

    public ComponentSignalRouter getSignalRouter() {
        return signalRouter;
    }

    @Override
    public String connect(String sourceId,
                          String sourceEvent,
                          String targetId,
                          String targetEvent,
                          String scopeId) {
        return connectComponents(sourceId, sourceEvent, targetId, targetEvent, scopeId).id();
    }

    @Override
    public boolean disconnect(String routeId) {
        return signalRouter.disconnect(routeId);
    }

    @Override
    public boolean send(String targetId, String eventName, LuaValue payload) {
        JComponent target = context.findComponent(targetId);
        if (target == null || eventName == null || eventName.isBlank()) {
            return false;
        }
        Runnable delivery = () -> emit(
                eventName,
                target,
                attributesFor(target),
                new UiRoutedEvent(
                        "direct",
                        null,
                        null,
                        "send",
                        targetId,
                        eventName
                ),
                payload == null ? LuaValue.NIL : payload,
                new LinkedHashSet<>()
        );
        if (SwingUtilities.isEventDispatchThread()) {
            delivery.run();
        } else {
            SwingUtilities.invokeLater(delivery);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> scriptsFor(JComponent component) {
        Object scripts = component.getClientProperty(SCRIPT_MAP_KEY);
        if (scripts instanceof Map<?, ?>) {
            return (Map<String, List<String>>) scripts;
        }
        return Collections.emptyMap();
    }

    private Map<String, List<String>> collectScripts(ComponentAttributes attributes) {
        Map<String, List<String>> scripts = new LinkedHashMap<>();

        if (context.scriptExists(BUILTIN_COMPONENT_SCRIPT)) {
            addScript(scripts, "*", BUILTIN_COMPONENT_SCRIPT);
        }
        addTypeBuiltInScripts(scripts, attributes);

        if (attributes.getScript() != null && !attributes.getScript().isBlank()) {
            addScript(scripts, "*", attributes.getScript().trim());
        }
        if (attributes.getScripts() != null) {
            attributes.getScripts().forEach((eventName, scriptPath) -> {
                if (eventName == null || eventName.isBlank() || scriptPath == null || scriptPath.isBlank()) {
                    return;
                }
                addScript(scripts, eventName, scriptPath.trim());
            });
        }
        return scripts;
    }

    private void addTypeBuiltInScripts(Map<String, List<String>> scripts, ComponentAttributes attributes) {
        String type = attributes.getComponentType();
        if (type == null || type.isBlank()) {
            return;
        }
        String normalizedType = type.trim();
        String scriptPath = BUILTIN_COMPONENT_SCRIPT_ROOT + normalizedType + ".lua";
        if (context.scriptExists(scriptPath)) {
            addScript(scripts, "*", scriptPath);
        }
    }

    private void addScript(Map<String, List<String>> scripts, String eventName, String scriptPath) {
        if (eventName == null || eventName.isBlank() || scriptPath == null || scriptPath.isBlank()) {
            return;
        }
        scripts.computeIfAbsent(normalizeEventName(eventName), key -> new ArrayList<>()).add(scriptPath);
    }

    private void installCommonListeners(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                emit("click", component, event);
            }

            @Override
            public void mousePressed(MouseEvent event) {
                emit("mousePressed", component, event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                emit("mouseReleased", component, event);
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                emit("hover", component, event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                emit("hoverExit", component, event);
            }
        });

        component.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                emit("focus", component, event);
            }

            @Override
            public void focusLost(FocusEvent event) {
                emit("blur", component, event);
            }
        });

        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                emit("keyPressed", component, event);
            }

            @Override
            public void keyReleased(KeyEvent event) {
                emit("keyReleased", component, event);
            }

            @Override
            public void keyTyped(KeyEvent event) {
                emit("keyTyped", component, event);
            }
        });

        if (component instanceof AbstractButton button) {
            button.addActionListener(event -> emit("action", component, event));
        }

        if (component instanceof JTextField textField) {
            textField.addActionListener(event -> emit("action", component, event));
        }

        if (component instanceof JTextComponent textComponent) {
            textComponent.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    emit("textChanged", component, event);
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    emit("textChanged", component, event);
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    emit("textChanged", component, event);
                }
            });
        }

        if (component instanceof JSlider slider) {
            slider.addChangeListener(event -> emit("change", component, event));
        }

        if (component instanceof JSpinner spinner) {
            spinner.addChangeListener(event -> emit("change", component, event));
        }

        if (component instanceof JComboBox<?> comboBox) {
            comboBox.addActionListener(event -> emit("change", component, event));
        }
    }

    private void emit(String eventName, JComponent component, Object rawEvent) {
        emit(
                eventName,
                component,
                attributesFor(component),
                rawEvent,
                LuaValue.NIL,
                new LinkedHashSet<>()
        );
    }

    private void emit(String eventName,
                      JComponent component,
                      ComponentAttributes attributes,
                      Object rawEvent) {
        emit(eventName, component, attributes, rawEvent, LuaValue.NIL, new LinkedHashSet<>());
    }

    private void emit(String eventName,
                      JComponent component,
                      ComponentAttributes attributes,
                      Object rawEvent,
                      LuaValue payload,
                      Set<String> traversedRoutes) {
        UiComponentApi source = context.apiFor(component, attributes);
        Map<String, List<String>> scripts = scriptsFor(component);
        LuaValue resolvedPayload = payload == null ? LuaValue.NIL : payload;

        int depth = eventDepth.get();
        if (depth >= MAX_REENTRANT_DEPTH) {
            Engine.LOGGER.warn(
                    "Lua UI event '{}' ignored for component '{}' because max reentrant depth was reached.",
                    eventName,
                    source != null ? source.id() : null
            );
            return;
        }

        eventDepth.set(depth + 1);
        try {
            for (String scriptPath : scriptsForEvent(scripts, eventName)) {
                runScript(
                        scriptPath,
                        eventName,
                        component,
                        attributes,
                        rawEvent,
                        resolvedPayload
                );
            }
            context.dispatch(eventName, source, rawEvent, resolvedPayload);
            routeSignals(eventName, source, resolvedPayload, traversedRoutes);
        } finally {
            eventDepth.set(depth);
        }
    }

    private void routeSignals(String eventName,
                              UiComponentApi source,
                              LuaValue payload,
                              Set<String> traversedRoutes) {
        if (source == null || source.id() == null || source.id().isBlank()) {
            return;
        }
        LuaValue forwardedPayload = payload == null || payload.isnil()
                ? source.getValue()
                : payload;

        for (ComponentSignalRoute route : signalRouter.routesFor(source.id(), eventName)) {
            if (traversedRoutes.contains(route.id())) {
                continue;
            }
            JComponent target = context.findComponent(route.targetId());
            if (target == null) {
                Engine.LOGGER.warn(
                        "Signal route '{}' skipped because target component '{}' is not registered.",
                        route.id(),
                        route.targetId()
                );
                continue;
            }

            Set<String> nextPath = new LinkedHashSet<>(traversedRoutes);
            nextPath.add(route.id());
            emit(
                    route.targetEvent(),
                    target,
                    attributesFor(target),
                    new UiRoutedEvent(
                            route.id(),
                            route.scopeId(),
                            route.sourceId(),
                            route.sourceEvent(),
                            route.targetId(),
                            route.targetEvent()
                    ),
                    forwardedPayload,
                    nextPath
            );
        }
    }

    private List<String> scriptsForEvent(Map<String, List<String>> scripts, String eventName) {
        if (scripts.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> paths = new LinkedHashSet<>();
        String normalized = normalizeEventName(eventName);
        addIfPresent(paths, scripts, "*");
        addIfPresent(paths, scripts, "all");
        addIfPresent(paths, scripts, normalized);
        return new ArrayList<>(paths);
    }

    private void addIfPresent(Set<String> paths, Map<String, List<String>> scripts, String eventName) {
        List<String> eventScripts = scripts.get(eventName);
        if (eventScripts == null || eventScripts.isEmpty()) {
            return;
        }
        for (String scriptPath : eventScripts) {
            if (scriptPath != null && !scriptPath.isBlank()) {
                paths.add(scriptPath);
            }
        }
    }

    private void runScript(String scriptPath,
                           String eventName,
                           JComponent component,
                           ComponentAttributes attributes,
                           Object rawEvent,
                           LuaValue payload) {
        try {
            runtime.execute(
                    scriptPath,
                    context.componentTable(component, attributes),
                    context.eventTable(eventName, component, attributes, rawEvent, payload)
            );
        } catch (LuaError error) {
            Engine.LOGGER.error("Lua UI script failed: {}", scriptPath, error);
        } catch (Exception error) {
            Engine.LOGGER.error("Unable to run Lua UI script: {}", scriptPath, error);
        }
    }

    private ComponentAttributes attributesFor(JComponent component) {
        if (component == null) {
            return null;
        }
        Object attributes = component.getClientProperty(ATTRIBUTES_KEY);
        return attributes instanceof ComponentAttributes componentAttributes ? componentAttributes : null;
    }
}
