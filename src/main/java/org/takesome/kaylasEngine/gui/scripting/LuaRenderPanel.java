package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.takesome.kaylasEngine.Engine;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Script-backed render surface.
 *
 * <p>The render context, state table and drawing functions are retained between frames. A paint no
 * longer allocates a new Lua API graph and callback objects for every effect frame.</p>
 */
public final class LuaRenderPanel extends JPanel {
    private static final int MAX_CACHED_COLORS = 64;

    private final UiScriptContext context;
    private final String scriptPath;
    private final String renderFunctionPath;
    private final Map<String, Object> state = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Color> colorCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Color> eldest) {
            return size() > MAX_CACHED_COLORS;
        }
    };
    private final LuaTable stateTable = new LuaTable();
    private final LuaTable renderContext = new LuaTable();
    private final LuaTable drawApi = new LuaTable();

    private LuaValue renderFunction = LuaValue.NIL;
    private LuaTable rootTable = new LuaTable();
    private Graphics2D activeGraphics;

    public LuaRenderPanel(UiScriptContext context, String scriptPath, String renderFunctionPath) {
        this.context = Objects.requireNonNull(context, "context");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath");
        this.renderFunctionPath = Objects.requireNonNull(renderFunctionPath, "renderFunctionPath");
        setOpaque(false);
        setDoubleBuffered(true);
        initializeRenderContext();
        initializeDrawApi();
        reloadScript();
    }

    public void reloadScript() {
        try {
            String source = context.scriptSource(scriptPath);
            Globals globals = LuaRuntimeFactory.createLightweightGlobals();
            globals.set("engine", context.engineTable());
            globals.set("ui", context.uiTable());

            LuaValue result = globals.load(source, scriptPath).call();
            if (!result.istable()) {
                Engine.getLOGGER().warn("Lua render script did not return a table: {}", scriptPath);
                rootTable = new LuaTable();
                renderFunction = LuaValue.NIL;
                renderContext.set("root", rootTable);
                return;
            }
            rootTable = result.checktable();
            renderContext.set("root", rootTable);
            renderFunction = resolveFunction(rootTable, renderFunctionPath);
            if (!renderFunction.isfunction()) {
                Engine.getLOGGER().warn("Lua render function '{}' was not found in {}", renderFunctionPath, scriptPath);
            }
        } catch (Exception error) {
            Engine.getLOGGER().warn("Unable to load Lua render script: {}", scriptPath, error);
            rootTable = new LuaTable();
            renderFunction = LuaValue.NIL;
            renderContext.set("root", rootTable);
        }
    }

    public void setState(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        state.put(key, value);
        Runnable update = () -> {
            stateTable.set(key, toLuaValue(value));
            repaint(0, 0, getWidth(), getHeight());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    public Object getState(String key) {
        return state.get(key);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!renderFunction.isfunction()) {
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderContext.set("width", LuaValue.valueOf(getWidth()));
            renderContext.set("height", LuaValue.valueOf(getHeight()));
            activeGraphics = graphics2D;
            renderFunction.call(renderContext, drawApi);
        } catch (Exception error) {
            Engine.getLOGGER().warn("Lua render function '{}' failed.", renderFunctionPath, error);
        } finally {
            activeGraphics = null;
            graphics2D.dispose();
        }
    }

    private void initializeRenderContext() {
        renderContext.set("width", LuaValue.valueOf(0));
        renderContext.set("height", LuaValue.valueOf(0));
        renderContext.set("script", LuaValue.valueOf(scriptPath));
        renderContext.set("state", stateTable);
        renderContext.set("root", rootTable);
    }

    private void initializeDrawApi() {
        drawApi.set("fillRect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Graphics2D graphics = activeGraphics;
                if (graphics == null) {
                    return LuaValue.NIL;
                }
                int x = intArg(args, 1, 0);
                int y = intArg(args, 2, 0);
                int width = intArg(args, 3, getWidth());
                int height = intArg(args, 4, getHeight());
                Color color = colorArg(args, 5, Color.BLACK);
                int alpha = alphaArg(args, 6, color.getAlpha());
                var previousComposite = graphics.getComposite();
                try {
                    graphics.setComposite(AlphaComposite.SrcOver.derive(normalizedAlpha(alpha)));
                    graphics.setColor(color);
                    graphics.fillRect(x, y, width, height);
                } finally {
                    graphics.setComposite(previousComposite);
                }
                return LuaValue.NIL;
            }
        });
        drawApi.set("fillRoundRect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Graphics2D graphics = activeGraphics;
                if (graphics == null) {
                    return LuaValue.NIL;
                }
                int x = intArg(args, 1, 0);
                int y = intArg(args, 2, 0);
                int width = intArg(args, 3, getWidth());
                int height = intArg(args, 4, getHeight());
                int arc = intArg(args, 5, 0);
                Color color = colorArg(args, 6, Color.BLACK);
                int alpha = alphaArg(args, 7, color.getAlpha());
                var previousComposite = graphics.getComposite();
                try {
                    graphics.setComposite(AlphaComposite.SrcOver.derive(normalizedAlpha(alpha)));
                    graphics.setColor(color);
                    graphics.fillRoundRect(x, y, width, height, arc, arc);
                } finally {
                    graphics.setComposite(previousComposite);
                }
                return LuaValue.NIL;
            }
        });
    }

    private static float normalizedAlpha(int alpha) {
        return Math.max(0f, Math.min(1f, alpha / 255f));
    }

    private LuaValue resolveFunction(LuaTable root, String path) {
        String[] parts = path.split("\\.");
        LuaValue current = root;
        for (String part : parts) {
            if (current == null || !current.istable()) {
                return LuaValue.NIL;
            }
            current = current.get(part);
        }
        return current == null ? LuaValue.NIL : current;
    }

    private LuaValue toLuaValue(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    private int intArg(Varargs args, int index, int fallback) {
        if (args.narg() < index || args.arg(index).isnil()) {
            return fallback;
        }
        return args.arg(index).toint();
    }

    private int alphaArg(Varargs args, int index, int fallback) {
        if (args.narg() < index || args.arg(index).isnil()) {
            return Math.max(0, Math.min(255, fallback));
        }
        LuaValue value = args.arg(index);
        if (value.isnumber()) {
            double numeric = value.todouble();
            if (numeric >= 0.0 && numeric <= 1.0) {
                return (int) Math.round(numeric * 255.0);
            }
            return Math.max(0, Math.min(255, (int) Math.round(numeric)));
        }
        return fallback;
    }

    private Color colorArg(Varargs args, int index, Color fallback) {
        if (args.narg() < index || args.arg(index).isnil()) {
            return fallback;
        }
        String raw = args.arg(index).tojstring().trim();
        try {
            Color cached = colorCache.get(raw);
            if (cached != null) {
                return cached;
            }
            Color parsed = Color.decode(raw.startsWith("#") ? raw : "#" + raw);
            colorCache.put(raw, parsed);
            return parsed;
        } catch (Exception error) {
            Engine.getLOGGER().warn("Invalid Lua render color '{}'.", raw);
            return fallback;
        }
    }
}
