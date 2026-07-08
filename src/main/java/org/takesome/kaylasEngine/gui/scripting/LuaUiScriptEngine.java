package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JSlider;
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
public final class LuaUiScriptEngine {
    private static final String SCRIPT_BOUND_KEY = "kaylas.ui.lua.bound";
    private static final String SCRIPT_MAP_KEY = "kaylas.ui.lua.scripts";
    private static final String ATTRIBUTES_KEY = "kaylas.ui.lua.attributes";
    private static final String BUILTIN_COMPONENT_SCRIPT = "assets/scripts/builtin/component.lua";
    private static final String BUILTIN_COMPONENT_SCRIPT_ROOT = "assets/scripts/builtin/components/";
    private static final int MAX_REENTRANT_DEPTH = 8;

    private final UiScriptContext context;
    private final ThreadLocal<Integer> eventDepth = ThreadLocal.withInitial(() -> 0);

    public LuaUiScriptEngine(Engine engine) {
        this.context = new UiScriptContext(Objects.requireNonNull(engine, "engine"));
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

    public void clearScriptCache() {
        context.clearScriptCache();
    }

    public void emitComponentEvent(String eventName, JComponent component, Object rawEvent) {
        emit(eventName, component, rawEvent);
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
        ComponentAttributes attributes = attributesFor(component);
        emit(eventName, component, attributes, rawEvent);
    }

    private void emit(String eventName, JComponent component, ComponentAttributes attributes, Object rawEvent) {
        UiComponentApi source = context.apiFor(component, attributes);
        Map<String, List<String>> scripts = scriptsFor(component);

        int depth = eventDepth.get();
        if (depth >= MAX_REENTRANT_DEPTH) {
            Engine.LOGGER.warn("Lua UI event '{}' ignored for component '{}' because max reentrant depth was reached.",
                    eventName,
                    source != null ? source.id() : null);
            return;
        }

        eventDepth.set(depth + 1);
        try {
            for (String scriptPath : scriptsForEvent(scripts, eventName)) {
                runScript(scriptPath, eventName, component, attributes, rawEvent);
            }
            context.dispatch(eventName, source, rawEvent, LuaValue.NIL);
        } finally {
            eventDepth.set(depth);
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
                           Object rawEvent) {
        try {
            Globals globals = JsePlatform.standardGlobals();
            globals.set("component", context.componentTable(component, attributes));
            globals.set("event", context.eventTable(eventName, component, attributes, rawEvent, LuaValue.NIL));
            globals.set("ui", context.uiTable());
            globals.set("engine", context.engineTable());

            String scriptSource = context.scriptSource(scriptPath);
            globals.load(scriptSource, scriptPath).call();
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
