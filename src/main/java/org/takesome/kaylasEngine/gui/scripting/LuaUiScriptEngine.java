package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.slider.Slider;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua-backed UI scripting bridge for KaylasUIEngine components.
 *
 * <p>Scripts are declared in {@link ComponentAttributes} using either a single {@code script}
 * path or an event-to-script map named {@code scripts}. A single script is executed for every
 * supported event, while the map allows binding event-specific handlers.</p>
 *
 * <pre>{@code
 * {
 *   "id": "playButton",
 *   "type": "button",
 *   "script": "assets/scripts/play-button.lua"
 * }
 * }</pre>
 *
 * <pre>{@code
 * {
 *   "id": "volumeSlider",
 *   "type": "slider",
 *   "scripts": {
 *     "init": "assets/scripts/volume.lua",
 *     "change": "assets/scripts/volume.lua"
 *   }
 * }
 * }</pre>
 */
@SuppressWarnings("unused")
public final class LuaUiScriptEngine {
    private static final String SCRIPT_BOUND_KEY = "kaylas.ui.lua.bound";
    private static final String SCRIPT_MAP_KEY = "kaylas.ui.lua.scripts";
    private static final String ATTRIBUTES_KEY = "kaylas.ui.lua.attributes";
    private static final int MAX_REENTRANT_DEPTH = 8;

    private final Engine engine;
    private final Map<String, JComponent> componentsById = new ConcurrentHashMap<>();
    private final Map<String, String> scriptSourceCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> eventDepth = ThreadLocal.withInitial(() -> 0);

    public LuaUiScriptEngine(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Registers a component and installs Lua event listeners if the component declares scripts.
     *
     * @param component  created Swing component.
     * @param attributes source component attributes.
     */
    public void bind(JComponent component, ComponentAttributes attributes) {
        if (component == null || attributes == null) {
            return;
        }

        registerComponent(component, attributes);
        Map<String, String> scripts = collectScripts(attributes);
        if (scripts.isEmpty()) {
            return;
        }

        component.putClientProperty(SCRIPT_MAP_KEY, scripts);
        component.putClientProperty(ATTRIBUTES_KEY, attributes);

        if (!Boolean.TRUE.equals(component.getClientProperty(SCRIPT_BOUND_KEY))) {
            installCommonListeners(component);
            component.putClientProperty(SCRIPT_BOUND_KEY, Boolean.TRUE);
        }

        emit("init", component, attributes, null);
    }

    public void registerComponent(JComponent component, ComponentAttributes attributes) {
        String componentId = componentId(component, attributes);
        if (componentId != null && !componentId.isBlank()) {
            componentsById.put(componentId, component);
        }
    }

    public JComponent findComponent(String componentId) {
        return componentsById.get(componentId);
    }

    public Map<String, JComponent> getComponentsById() {
        return Collections.unmodifiableMap(componentsById);
    }

    public void clearScriptCache() {
        scriptSourceCache.clear();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> scriptsFor(JComponent component) {
        Object scripts = component.getClientProperty(SCRIPT_MAP_KEY);
        if (scripts instanceof Map<?, ?>) {
            return (Map<String, String>) scripts;
        }
        return Collections.emptyMap();
    }

    private Map<String, String> collectScripts(ComponentAttributes attributes) {
        Map<String, String> scripts = new LinkedHashMap<>();
        if (attributes.getScript() != null && !attributes.getScript().isBlank()) {
            scripts.put("*", attributes.getScript().trim());
        }
        if (attributes.getScripts() != null) {
            attributes.getScripts().forEach((eventName, scriptPath) -> {
                if (eventName == null || eventName.isBlank() || scriptPath == null || scriptPath.isBlank()) {
                    return;
                }
                scripts.put(normalizeEventName(eventName), scriptPath.trim());
            });
        }
        return scripts;
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
        Map<String, String> scripts = scriptsFor(component);
        if (scripts.isEmpty()) {
            return;
        }

        int depth = eventDepth.get();
        if (depth >= MAX_REENTRANT_DEPTH) {
            Engine.LOGGER.warn("Lua UI event '{}' ignored for component '{}' because max reentrant depth was reached.",
                    eventName,
                    componentId(component, attributes));
            return;
        }

        List<String> scriptPaths = scriptsForEvent(scripts, eventName);
        if (scriptPaths.isEmpty()) {
            return;
        }

        eventDepth.set(depth + 1);
        try {
            for (String scriptPath : scriptPaths) {
                runScript(scriptPath, eventName, component, attributes, rawEvent);
            }
        } finally {
            eventDepth.set(depth);
        }
    }

    private List<String> scriptsForEvent(Map<String, String> scripts, String eventName) {
        Set<String> paths = new LinkedHashSet<>();
        String normalized = normalizeEventName(eventName);
        addIfPresent(paths, scripts, normalized);
        addIfPresent(paths, scripts, "*");
        addIfPresent(paths, scripts, "all");
        return new ArrayList<>(paths);
    }

    private void addIfPresent(Set<String> paths, Map<String, String> scripts, String eventName) {
        String path = scripts.get(eventName);
        if (path != null && !path.isBlank()) {
            paths.add(path);
        }
    }

    private void runScript(String scriptPath,
                           String eventName,
                           JComponent component,
                           ComponentAttributes attributes,
                           Object rawEvent) {
        try {
            Globals globals = JsePlatform.standardGlobals();
            globals.set("component", componentApi(component, attributes));
            globals.set("event", eventApi(eventName, component, attributes, rawEvent));
            globals.set("ui", uiApi());
            globals.set("engine", engineApi());

            String scriptSource = scriptSource(scriptPath);
            globals.load(scriptSource, scriptPath).call();
        } catch (LuaError error) {
            Engine.LOGGER.error("Lua UI script failed: {}", scriptPath, error);
        } catch (Exception error) {
            Engine.LOGGER.error("Unable to run Lua UI script: {}", scriptPath, error);
        }
    }

    private String scriptSource(String scriptPath) {
        return scriptSourceCache.computeIfAbsent(scriptPath, this::loadScriptSource);
    }

    private String loadScriptSource(String scriptPath) {
        String normalized = normalizeResourcePath(scriptPath);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = LuaUiScriptEngine.class.getClassLoader();
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

    private LuaTable componentApi(JComponent component, ComponentAttributes attributes) {
        LuaTable table = new LuaTable();
        table.set("id", value(componentId(component, attributes)));
        table.set("type", value(attributes != null ? attributes.getComponentType() : component.getClass().getSimpleName()));
        table.set("className", value(component.getClass().getName()));

        table.set("getId", function(args -> value(componentId(component, attributes))));
        table.set("getType", function(args -> value(attributes != null ? attributes.getComponentType() : component.getClass().getSimpleName())));
        table.set("getName", function(args -> value(component.getName())));
        table.set("getText", function(args -> value(getText(component))));
        table.set("setText", function(args -> {
            String text = stringArg(args, 1, "");
            runOnEdt(() -> setText(component, text));
            return LuaValue.NIL;
        }));
        table.set("getValue", function(args -> toLuaValue(getValue(component))));
        table.set("setValue", function(args -> {
            LuaValue value = arg(args, 1);
            runOnEdt(() -> setValue(component, value));
            return LuaValue.NIL;
        }));
        table.set("setVisible", function(args -> {
            boolean visible = booleanArg(args, 1, true);
            runOnEdt(() -> component.setVisible(visible));
            return LuaValue.NIL;
        }));
        table.set("isVisible", function(args -> LuaValue.valueOf(component.isVisible())));
        table.set("show", function(args -> {
            runOnEdt(() -> component.setVisible(true));
            return LuaValue.NIL;
        }));
        table.set("hide", function(args -> {
            runOnEdt(() -> component.setVisible(false));
            return LuaValue.NIL;
        }));
        table.set("setEnabled", function(args -> {
            boolean enabled = booleanArg(args, 1, true);
            runOnEdt(() -> component.setEnabled(enabled));
            return LuaValue.NIL;
        }));
        table.set("isEnabled", function(args -> LuaValue.valueOf(component.isEnabled())));
        table.set("enable", function(args -> {
            runOnEdt(() -> component.setEnabled(true));
            return LuaValue.NIL;
        }));
        table.set("disable", function(args -> {
            runOnEdt(() -> component.setEnabled(false));
            return LuaValue.NIL;
        }));
        table.set("setBounds", function(args -> {
            int x = intArg(args, 1, component.getX());
            int y = intArg(args, 2, component.getY());
            int width = intArg(args, 3, component.getWidth());
            int height = intArg(args, 4, component.getHeight());
            runOnEdt(() -> component.setBounds(x, y, width, height));
            return LuaValue.NIL;
        }));
        table.set("setLocation", function(args -> {
            int x = intArg(args, 1, component.getX());
            int y = intArg(args, 2, component.getY());
            runOnEdt(() -> component.setLocation(x, y));
            return LuaValue.NIL;
        }));
        table.set("setSize", function(args -> {
            int width = intArg(args, 1, component.getWidth());
            int height = intArg(args, 2, component.getHeight());
            runOnEdt(() -> component.setSize(width, height));
            return LuaValue.NIL;
        }));
        table.set("setForeground", function(args -> {
            Color color = colorArg(args, 1, component.getForeground());
            runOnEdt(() -> component.setForeground(color));
            return LuaValue.NIL;
        }));
        table.set("setBackground", function(args -> {
            Color color = colorArg(args, 1, component.getBackground());
            runOnEdt(() -> component.setBackground(color));
            return LuaValue.NIL;
        }));
        table.set("putProperty", function(args -> {
            String key = stringArg(args, 1, "");
            LuaValue value = arg(args, 2);
            if (!key.isBlank()) {
                runOnEdt(() -> component.putClientProperty(key, javaValue(value)));
            }
            return LuaValue.NIL;
        }));
        table.set("getProperty", function(args -> {
            String key = stringArg(args, 1, "");
            return key.isBlank() ? LuaValue.NIL : toLuaValue(component.getClientProperty(key));
        }));
        table.set("requestFocus", function(args -> {
            runOnEdt(component::requestFocusInWindow);
            return LuaValue.NIL;
        }));
        table.set("repaint", function(args -> {
            runOnEdt(component::repaint);
            return LuaValue.NIL;
        }));
        return table;
    }

    private LuaTable eventApi(String eventName, JComponent component, ComponentAttributes attributes, Object rawEvent) {
        LuaTable table = new LuaTable();
        table.set("name", value(eventName));
        table.set("componentId", value(componentId(component, attributes)));
        table.set("componentType", value(attributes != null ? attributes.getComponentType() : component.getClass().getSimpleName()));
        table.set("time", LuaValue.valueOf(Instant.now().toString()));
        table.set("value", toLuaValue(getValue(component)));

        if (rawEvent instanceof MouseEvent mouseEvent) {
            table.set("x", LuaValue.valueOf(mouseEvent.getX()));
            table.set("y", LuaValue.valueOf(mouseEvent.getY()));
            table.set("button", LuaValue.valueOf(mouseEvent.getButton()));
            table.set("clickCount", LuaValue.valueOf(mouseEvent.getClickCount()));
            table.set("modifiers", LuaValue.valueOf(mouseEvent.getModifiersEx()));
        } else if (rawEvent instanceof KeyEvent keyEvent) {
            table.set("keyCode", LuaValue.valueOf(keyEvent.getKeyCode()));
            table.set("keyChar", LuaValue.valueOf(String.valueOf(keyEvent.getKeyChar())));
            table.set("modifiers", LuaValue.valueOf(keyEvent.getModifiersEx()));
        } else if (rawEvent instanceof FocusEvent focusEvent) {
            table.set("temporary", LuaValue.valueOf(focusEvent.isTemporary()));
        } else if (rawEvent instanceof DocumentEvent documentEvent) {
            table.set("offset", LuaValue.valueOf(documentEvent.getOffset()));
            table.set("length", LuaValue.valueOf(documentEvent.getLength()));
        }

        return table;
    }

    private LuaTable uiApi() {
        LuaTable table = new LuaTable();
        table.set("log", function(args -> {
            Engine.LOGGER.info("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        table.set("info", table.get("log"));
        table.set("warn", function(args -> {
            Engine.LOGGER.warn("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        table.set("error", function(args -> {
            Engine.LOGGER.error("[lua-ui] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        table.set("find", function(args -> {
            String id = stringArg(args, 1, "");
            JComponent component = findComponent(id);
            ComponentAttributes attributes = attributesFor(component);
            return component == null ? LuaValue.NIL : componentApi(component, attributes);
        }));
        table.set("show", function(args -> {
            JComponent component = findComponent(stringArg(args, 1, ""));
            if (component != null) {
                runOnEdt(() -> component.setVisible(true));
            }
            return LuaValue.NIL;
        }));
        table.set("hide", function(args -> {
            JComponent component = findComponent(stringArg(args, 1, ""));
            if (component != null) {
                runOnEdt(() -> component.setVisible(false));
            }
            return LuaValue.NIL;
        }));
        table.set("enable", function(args -> {
            JComponent component = findComponent(stringArg(args, 1, ""));
            if (component != null) {
                runOnEdt(() -> component.setEnabled(true));
            }
            return LuaValue.NIL;
        }));
        table.set("disable", function(args -> {
            JComponent component = findComponent(stringArg(args, 1, ""));
            if (component != null) {
                runOnEdt(() -> component.setEnabled(false));
            }
            return LuaValue.NIL;
        }));
        table.set("setText", function(args -> {
            JComponent component = findComponent(stringArg(args, 1, ""));
            String text = stringArg(args, 2, "");
            if (component != null) {
                runOnEdt(() -> setText(component, text));
            }
            return LuaValue.NIL;
        }));
        return table;
    }

    private LuaTable engineApi() {
        LuaTable table = new LuaTable();
        table.set("appTitle", value(engine.getAppTitle()));
        table.set("runtime", value("KaylasUIEngine"));
        table.set("log", function(args -> {
            Engine.LOGGER.info("[lua-engine] {}", stringArg(args, 1, ""));
            return LuaValue.NIL;
        }));
        return table;
    }

    private ComponentAttributes attributesFor(JComponent component) {
        if (component == null) {
            return null;
        }
        Object attributes = component.getClientProperty(ATTRIBUTES_KEY);
        return attributes instanceof ComponentAttributes componentAttributes ? componentAttributes : null;
    }

    private String componentId(JComponent component, ComponentAttributes attributes) {
        if (attributes != null && attributes.getComponentId() != null && !attributes.getComponentId().isBlank()) {
            return attributes.getComponentId();
        }
        return component != null ? component.getName() : null;
    }

    private String getText(JComponent component) {
        if (component instanceof JLabel label) {
            return label.getText();
        }
        if (component instanceof AbstractButton button) {
            return button.getText();
        }
        if (component instanceof JTextComponent textComponent) {
            return textComponent.getText();
        }
        return component.getToolTipText();
    }

    private void setText(JComponent component, String text) {
        if (component instanceof JLabel label) {
            label.setText(text);
        } else if (component instanceof AbstractButton button) {
            button.setText(text);
        } else if (component instanceof JTextComponent textComponent) {
            textComponent.setText(text);
        } else {
            component.setToolTipText(text);
        }
    }

    private Object getValue(JComponent component) {
        if (component instanceof AbstractButton button) {
            return button.isSelected();
        }
        if (component instanceof JTextComponent textComponent) {
            return textComponent.getText();
        }
        if (component instanceof JSlider slider) {
            return slider.getValue();
        }
        if (component instanceof JSpinner spinner) {
            return spinner.getValue();
        }
        if (component instanceof JComboBox<?> comboBox) {
            return comboBox.getSelectedItem();
        }
        if (component instanceof JProgressBar progressBar) {
            return progressBar.getValue();
        }
        return null;
    }

    private void setValue(JComponent component, LuaValue value) {
        if (component instanceof AbstractButton button) {
            button.setSelected(value.toboolean());
        } else if (component instanceof JTextComponent textComponent) {
            textComponent.setText(value.isnil() ? "" : value.tojstring());
        } else if (component instanceof Slider slider) {
            slider.setValue(value.toint());
        } else if (component instanceof JSlider slider) {
            slider.setValue(value.toint());
        } else if (component instanceof JSpinner spinner) {
            spinner.setValue(value.isnumber() ? value.toint() : value.tojstring());
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setSelectedItem(value.isnil() ? null : value.tojstring());
        } else if (component instanceof JProgressBar progressBar) {
            progressBar.setValue(value.toint());
        }
    }

    private LuaValue toLuaValue(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean booleanValue) {
            return LuaValue.valueOf(booleanValue);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    private Object javaValue(LuaValue value) {
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
        return value.tojstring();
    }

    private LuaValue value(String value) {
        return value == null ? LuaValue.NIL : LuaValue.valueOf(value);
    }

    private LuaValue arg(Varargs args, int logicalIndex) {
        int offset = hasSelf(args) ? 1 : 0;
        int actualIndex = logicalIndex + offset;
        return args.narg() >= actualIndex ? args.arg(actualIndex) : LuaValue.NIL;
    }

    private String stringArg(Varargs args, int logicalIndex, String fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.tojstring();
    }

    private int intArg(Varargs args, int logicalIndex, int fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.toint();
    }

    private boolean booleanArg(Varargs args, int logicalIndex, boolean fallback) {
        LuaValue value = arg(args, logicalIndex);
        return value.isnil() ? fallback : value.toboolean();
    }

    private Color colorArg(Varargs args, int logicalIndex, Color fallback) {
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

    private boolean hasSelf(Varargs args) {
        return args.narg() > 0 && args.arg1().istable();
    }

    private LuaValue function(LuaCallback callback) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return callback.call(args);
            }
        };
    }

    private void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private String normalizeEventName(String eventName) {
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

    private String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    @FunctionalInterface
    private interface LuaCallback {
        LuaValue call(Varargs args);
    }
}
