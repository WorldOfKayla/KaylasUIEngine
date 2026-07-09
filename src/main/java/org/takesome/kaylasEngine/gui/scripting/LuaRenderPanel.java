package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.takesome.kaylasEngine.Engine;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Script-backed render surface.
 *
 * <p>The engine provides drawing primitives and Lua execution. Applications provide the script and
 * render policy.</p>
 */
public final class LuaRenderPanel extends JPanel {
    private final UiScriptContext context;
    private final String scriptPath;
    private final String renderFunctionPath;
    private final Map<String, Object> state = new LinkedHashMap<>();

    private LuaValue renderFunction = LuaValue.NIL;
    private LuaTable rootTable = new LuaTable();

    public LuaRenderPanel(UiScriptContext context, String scriptPath, String renderFunctionPath) {
        this.context = Objects.requireNonNull(context, "context");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath");
        this.renderFunctionPath = Objects.requireNonNull(renderFunctionPath, "renderFunctionPath");
        setOpaque(false);
        setDoubleBuffered(true);
        reloadScript();
    }

    public void reloadScript() {
        try {
            String source = context.scriptSource(scriptPath);
            Globals globals = JsePlatform.standardGlobals();
            globals.set("engine", context.engineTable());
            globals.set("ui", context.uiTable());

            LuaValue result = globals.load(source, scriptPath).call();
            if (!result.istable()) {
                Engine.getLOGGER().warn("Lua render script did not return a table: {}", scriptPath);
                rootTable = new LuaTable();
                renderFunction = LuaValue.NIL;
                return;
            }
            rootTable = result.checktable();
            renderFunction = resolveFunction(rootTable, renderFunctionPath);
            if (!renderFunction.isfunction()) {
                Engine.getLOGGER().warn("Lua render function '{}' was not found in {}", renderFunctionPath, scriptPath);
            }
        } catch (Exception error) {
            Engine.getLOGGER().warn("Unable to load Lua render script: {}", scriptPath, error);
            rootTable = new LuaTable();
            renderFunction = LuaValue.NIL;
        }
    }

    public void setState(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        state.put(key, value);
        repaint(0, 0, getWidth(), getHeight());
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
            LuaTable ctx = contextTable();
            LuaTable draw = drawTable(graphics2D);
            renderFunction.call(ctx, draw);
        } catch (Exception error) {
            Engine.getLOGGER().warn("Lua render function '{}' failed.", renderFunctionPath, error);
        } finally {
            graphics2D.dispose();
        }
    }

    private LuaTable contextTable() {
        LuaTable ctx = new LuaTable();
        ctx.set("width", LuaValue.valueOf(getWidth()));
        ctx.set("height", LuaValue.valueOf(getHeight()));
        ctx.set("script", LuaValue.valueOf(scriptPath));
        ctx.set("state", stateTable());
        ctx.set("root", rootTable);
        return ctx;
    }

    private LuaTable stateTable() {
        LuaTable table = new LuaTable();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            table.set(entry.getKey(), toLuaValue(entry.getValue()));
        }
        return table;
    }

    private LuaTable drawTable(Graphics2D graphics2D) {
        LuaTable draw = new LuaTable();
        draw.set("fillRect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int x = intArg(args, 1, 0);
                int y = intArg(args, 2, 0);
                int width = intArg(args, 3, getWidth());
                int height = intArg(args, 4, getHeight());
                Color color = colorArg(args, 5, Color.BLACK);
                int alpha = alphaArg(args, 6, color.getAlpha());
                paintWithAlpha(graphics2D, alpha, () -> {
                    graphics2D.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    graphics2D.fillRect(x, y, width, height);
                });
                return LuaValue.NIL;
            }
        });
        draw.set("fillRoundRect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int x = intArg(args, 1, 0);
                int y = intArg(args, 2, 0);
                int width = intArg(args, 3, getWidth());
                int height = intArg(args, 4, getHeight());
                int arc = intArg(args, 5, 0);
                Color color = colorArg(args, 6, Color.BLACK);
                int alpha = alphaArg(args, 7, color.getAlpha());
                paintWithAlpha(graphics2D, alpha, () -> {
                    graphics2D.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    graphics2D.fillRoundRect(x, y, width, height, arc, arc);
                });
                return LuaValue.NIL;
            }
        });
        return draw;
    }

    private void paintWithAlpha(Graphics2D graphics2D, int alpha, Runnable painter) {
        var oldComposite = graphics2D.getComposite();
        graphics2D.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha / 255f))));
        try {
            painter.run();
        } finally {
            graphics2D.setComposite(oldComposite);
        }
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
        try {
            String raw = args.arg(index).tojstring().trim();
            return Color.decode(raw.startsWith("#") ? raw : "#" + raw);
        } catch (Exception error) {
            Engine.getLOGGER().warn("Invalid Lua render color '{}'.", args.arg(index).tojstring());
            return fallback;
        }
    }
}
