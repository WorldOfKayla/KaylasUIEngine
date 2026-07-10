package org.takesome.kaylasEngine.gui.scripting;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.constructor.ConstructedCompositeComponent;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBarStyle;
import org.takesome.kaylasEngine.utils.FontUtils;
import org.takesome.kaylasEngine.gui.components.slider.Slider;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.arg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.booleanArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.colorArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.function;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.intArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.javaValue;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.runOnEdt;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.stringArg;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.table;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.toLuaValue;
import static org.takesome.kaylasEngine.gui.scripting.LuaRuntimeSupport.value;

/**
 * Strict Lua-facing wrapper around a Swing component.
 *
 * <p>Lua receives this API instead of the raw Swing component. This keeps scripting powerful enough
 * for UI behavior while keeping the Java runtime surface controlled.</p>
 */
public final class UiComponentApi {
    private final UiScriptContext context;
    private final JComponent component;
    private final ComponentAttributes attributes;

    public UiComponentApi(UiScriptContext context, JComponent component, ComponentAttributes attributes) {
        this.context = Objects.requireNonNull(context, "context");
        this.component = Objects.requireNonNull(component, "component");
        this.attributes = attributes;
    }

    public JComponent component() {
        return component;
    }

    public ComponentAttributes attributes() {
        return attributes;
    }

    public String id() {
        if (attributes != null && attributes.getComponentId() != null && !attributes.getComponentId().isBlank()) {
            return attributes.getComponentId();
        }
        return component.getName();
    }

    public String type() {
        if (attributes != null && attributes.getComponentType() != null && !attributes.getComponentType().isBlank()) {
            return attributes.getComponentType();
        }
        return component.getClass().getSimpleName();
    }

    public LuaTable toLuaTable() {
        LuaTable lua = table();
        lua.set("id", value(id()));
        lua.set("type", value(type()));
        lua.set("className", value(component.getClass().getName()));

        lua.set("getId", function(args -> value(id())));
        lua.set("getType", function(args -> value(type())));
        lua.set("getName", function(args -> value(component.getName())));
        lua.set("getText", function(args -> value(getText())));
        lua.set("setText", function(this::luaSetText));
        lua.set("getValue", function(args -> getValue()));
        lua.set("setValue", function(this::luaSetValue));
        lua.set("setVisible", function(this::luaSetVisible));
        lua.set("isVisible", function(args -> LuaValue.valueOf(component.isVisible())));
        lua.set("show", function(this::luaShow));
        lua.set("hide", function(this::luaHide));
        lua.set("setEnabled", function(this::luaSetEnabled));
        lua.set("isEnabled", function(args -> LuaValue.valueOf(component.isEnabled())));
        lua.set("enable", function(this::luaEnable));
        lua.set("disable", function(this::luaDisable));
        lua.set("setBounds", function(this::luaSetBounds));
        lua.set("setLocation", function(this::luaSetLocation));
        lua.set("setSize", function(this::luaSetSize));
        lua.set("setForeground", function(this::luaSetForeground));
        lua.set("setBackground", function(this::luaSetBackground));
        lua.set("putProperty", function(this::luaPutProperty));
        lua.set("getProperty", function(this::luaGetProperty));
        lua.set("requestFocus", function(this::luaRequestFocus));
        lua.set("repaint", function(this::luaRepaint));
        lua.set("emit", function(this::luaEmit));
        lua.set("on", function(this::luaOn));
        lua.set("send", function(this::luaSend));
        lua.set("sendLocal", function(this::luaSendLocal));
        lua.set("connect", function(this::luaConnect));
        lua.set("connectLocal", function(this::luaConnectLocal));
        lua.set("findLocal", function(this::luaFindLocal));
        lua.set("getScopeId", function(args -> value(scopeId())));
        lua.set("getLocalId", function(args -> value(localId())));

        if (component instanceof ProgressBar progressBar) {
            lua.set("getStyle", function(args -> value(progressBar.getStyleName())));
            lua.set("setStyle", function(args -> {
                String styleName = stringArg(args, 1, "default");
                String appliedStyle = ProgressBarStyle.applyNamedStyle(
                        context.engine().getGuiBuilder().getComponentFactory(),
                        progressBar,
                        styleName
                );
                return value(appliedStyle);
            }));
            lua.set("getFontName", function(args -> value(progressBar.getTextFont().getFamily())));
            lua.set("getFontSize", function(args -> LuaValue.valueOf(progressBar.getTextFont().getSize2D())));
            lua.set("getFontStyle", function(args -> value(FontUtils.styleName(progressBar.getTextFont().getStyle()))));
            lua.set("setFont", function(args -> {
                Font currentFont = progressBar.getTextFont();
                String fontName = stringArg(args, 1, currentFont.getFamily());
                int fontSize = intArg(args, 2, currentFont.getSize());
                String fontStyle = stringArg(args, 3, FontUtils.styleName(currentFont.getStyle()));
                Font resolvedFont = context.engine().getFONTUTILS().getFont(fontName, fontSize, fontStyle);
                runOnEdt(() -> progressBar.setTextFont(resolvedFont));
                return LuaValue.NIL;
            }));
            lua.set("getMinimum", function(args -> LuaValue.valueOf(progressBar.getMinimum())));
            lua.set("setMinimum", function(args -> {
                int minimum = intArg(args, 1, progressBar.getMinimum());
                runOnEdt(() -> progressBar.setMinimum(minimum));
                return LuaValue.NIL;
            }));
            lua.set("getMaximum", function(args -> LuaValue.valueOf(progressBar.getMaximum())));
            lua.set("setMaximum", function(args -> {
                int maximum = intArg(args, 1, progressBar.getMaximum());
                runOnEdt(() -> progressBar.setMaximum(maximum));
                return LuaValue.NIL;
            }));
            lua.set("getPercent", function(args -> LuaValue.valueOf(progressBar.getPercentComplete())));
            lua.set("getString", function(args -> value(progressBar.getString())));
            lua.set("setString", function(args -> {
                String progressString = stringArg(args, 1, "");
                runOnEdt(() -> progressBar.setString(progressString));
                return LuaValue.NIL;
            }));
            lua.set("isStringPainted", function(args -> LuaValue.valueOf(progressBar.isStringPainted())));
            lua.set("setStringPainted", function(args -> {
                boolean painted = booleanArg(args, 1, true);
                runOnEdt(() -> progressBar.setStringPainted(painted));
                return LuaValue.NIL;
            }));
            lua.set("isShowPercent", function(args -> LuaValue.valueOf(progressBar.isShowPercent())));
            lua.set("setShowPercent", function(args -> {
                boolean showPercent = booleanArg(args, 1, true);
                runOnEdt(() -> progressBar.setShowPercent(showPercent));
                return LuaValue.NIL;
            }));
            lua.set("isIndeterminate", function(args -> LuaValue.valueOf(progressBar.isIndeterminate())));
            lua.set("setIndeterminate", function(args -> {
                boolean indeterminate = booleanArg(args, 1, true);
                runOnEdt(() -> progressBar.setIndeterminate(indeterminate));
                return LuaValue.NIL;
            }));
            lua.set("isInverted", function(args -> LuaValue.valueOf(progressBar.isInverted())));
            lua.set("setInverted", function(args -> {
                boolean inverted = booleanArg(args, 1, true);
                runOnEdt(() -> progressBar.setInverted(inverted));
                return LuaValue.NIL;
            }));
            lua.set("getOrientation", function(args -> value(
                    progressBar.getOrientation() == javax.swing.SwingConstants.VERTICAL ? "vertical" : "horizontal")));
            lua.set("setOrientation", function(args -> {
                String orientation = stringArg(args, 1, "horizontal");
                int resolved = "vertical".equalsIgnoreCase(orientation)
                        ? javax.swing.SwingConstants.VERTICAL
                        : javax.swing.SwingConstants.HORIZONTAL;
                runOnEdt(() -> progressBar.setOrientation(resolved));
                return LuaValue.NIL;
            }));
            lua.set("setTextColor", function(args -> {
                Color color = colorArg(args, 1, progressBar.getTextColor());
                runOnEdt(() -> progressBar.setTextColor(color));
                return LuaValue.NIL;
            }));
            lua.set("setTrackColor", function(args -> {
                Color color = colorArg(args, 1, progressBar.getTrackColor());
                runOnEdt(() -> progressBar.setTrackColor(color));
                return LuaValue.NIL;
            }));
            lua.set("setFillColor", function(args -> {
                Color color = colorArg(args, 1, progressBar.getFillColor());
                runOnEdt(() -> progressBar.setFillColor(color));
                return LuaValue.NIL;
            }));
            lua.set("setTextFormat", function(args -> {
                String format = stringArg(args, 1, "{percent}%");
                runOnEdt(() -> progressBar.setTextFormat(format));
                return LuaValue.NIL;
            }));
        }
        return lua;
    }

    public String getText() {
        if (component instanceof ProgressBar progressBar) {
            return progressBar.getString();
        }
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

    public void setText(String text) {
        runOnEdt(() -> {
            if (component instanceof ProgressBar progressBar) {
                progressBar.setString(text);
            } else if (component instanceof JLabel label) {
                label.setText(text);
            } else if (component instanceof AbstractButton button) {
                button.setText(text);
            } else if (component instanceof JTextComponent textComponent) {
                textComponent.setText(text);
            } else {
                component.setToolTipText(text);
            }
        });
    }

    public LuaValue getValue() {
        if (component instanceof ProgressBar progressBar) {
            return LuaValue.valueOf(progressBar.getValue());
        }
        if (component instanceof AbstractButton button) {
            return LuaValue.valueOf(button.isSelected());
        }
        if (component instanceof JTextComponent textComponent) {
            return value(textComponent.getText());
        }
        if (component instanceof JSlider slider) {
            return LuaValue.valueOf(slider.getValue());
        }
        if (component instanceof JSpinner spinner) {
            return toLuaValue(spinner.getValue());
        }
        if (component instanceof JComboBox<?> comboBox) {
            return toLuaValue(comboBox.getSelectedItem());
        }
        if (component instanceof JProgressBar progressBar) {
            return LuaValue.valueOf(progressBar.getValue());
        }
        return LuaValue.NIL;
    }

    public void setValue(LuaValue value) {
        runOnEdt(() -> {
            if (component instanceof ProgressBar progressBar) {
                progressBar.setValue(value.toint());
            } else if (component instanceof AbstractButton button) {
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
        });
    }

    private LuaValue luaOn(Varargs args) {
        return LuaValue.valueOf(context.subscribeComponent(
                id(),
                stringArg(args, 1, ""),
                arg(args, 2)
        ));
    }

    private LuaValue luaSend(Varargs args) {
        return LuaValue.valueOf(context.send(
                stringArg(args, 1, ""),
                stringArg(args, 2, ""),
                arg(args, 3)
        ));
    }

    private LuaValue luaSendLocal(Varargs args) {
        return LuaValue.valueOf(context.send(
                qualifyLocal(stringArg(args, 1, "")),
                stringArg(args, 2, ""),
                arg(args, 3)
        ));
    }

    private LuaValue luaConnect(Varargs args) {
        String routeId = context.connect(
                id(),
                stringArg(args, 1, ""),
                stringArg(args, 2, ""),
                stringArg(args, 3, ""),
                stringArg(args, 4, scopeId())
        );
        return value(routeId);
    }

    private LuaValue luaConnectLocal(Varargs args) {
        String routeId = context.connect(
                id(),
                stringArg(args, 1, ""),
                qualifyLocal(stringArg(args, 2, "")),
                stringArg(args, 3, ""),
                scopeId()
        );
        return value(routeId);
    }

    private LuaValue luaFindLocal(Varargs args) {
        UiComponentApi api = context.findApi(qualifyLocal(stringArg(args, 1, "")));
        return api == null
                ? LuaValue.NIL
                : context.componentTable(api.component(), api.attributes());
    }

    public String scopeId() {
        Object value = component.getClientProperty(ConstructedCompositeComponent.SCOPE_PROPERTY);
        return value == null ? null : String.valueOf(value);
    }

    public String localId() {
        Object value = component.getClientProperty(ConstructedCompositeComponent.LOCAL_ID_PROPERTY);
        return value == null ? null : String.valueOf(value);
    }

    public String qualifyLocal(String targetLocalId) {
        if (targetLocalId == null || targetLocalId.isBlank()) {
            return targetLocalId;
        }
        String scope = scopeId();
        String target = targetLocalId.trim();
        if (scope == null || scope.isBlank()
                || target.equals(scope)
                || target.startsWith(scope + ".")) {
            return target;
        }
        if ("$root".equals(target)) {
            return scope;
        }
        return scope + "." + target;
    }

    private LuaValue luaSetText(Varargs args) {
        setText(stringArg(args, 1, ""));
        return LuaValue.NIL;
    }

    private LuaValue luaSetValue(Varargs args) {
        setValue(arg(args, 1));
        return LuaValue.NIL;
    }

    private LuaValue luaSetVisible(Varargs args) {
        boolean visible = booleanArg(args, 1, true);
        runOnEdt(() -> component.setVisible(visible));
        return LuaValue.NIL;
    }

    private LuaValue luaShow(Varargs args) {
        runOnEdt(() -> component.setVisible(true));
        return LuaValue.NIL;
    }

    private LuaValue luaHide(Varargs args) {
        runOnEdt(() -> component.setVisible(false));
        return LuaValue.NIL;
    }

    private LuaValue luaSetEnabled(Varargs args) {
        boolean enabled = booleanArg(args, 1, true);
        runOnEdt(() -> component.setEnabled(enabled));
        return LuaValue.NIL;
    }

    private LuaValue luaEnable(Varargs args) {
        runOnEdt(() -> component.setEnabled(true));
        return LuaValue.NIL;
    }

    private LuaValue luaDisable(Varargs args) {
        runOnEdt(() -> component.setEnabled(false));
        return LuaValue.NIL;
    }

    private LuaValue luaSetBounds(Varargs args) {
        int x = intArg(args, 1, component.getX());
        int y = intArg(args, 2, component.getY());
        int width = intArg(args, 3, component.getWidth());
        int height = intArg(args, 4, component.getHeight());
        runOnEdt(() -> component.setBounds(x, y, width, height));
        return LuaValue.NIL;
    }

    private LuaValue luaSetLocation(Varargs args) {
        int x = intArg(args, 1, component.getX());
        int y = intArg(args, 2, component.getY());
        runOnEdt(() -> component.setLocation(x, y));
        return LuaValue.NIL;
    }

    private LuaValue luaSetSize(Varargs args) {
        int width = intArg(args, 1, component.getWidth());
        int height = intArg(args, 2, component.getHeight());
        runOnEdt(() -> component.setSize(width, height));
        return LuaValue.NIL;
    }

    private LuaValue luaSetForeground(Varargs args) {
        Color fallback = component instanceof ProgressBar progressBar
                ? progressBar.getTextColor()
                : component.getForeground();
        Color color = colorArg(args, 1, fallback);
        runOnEdt(() -> {
            if (component instanceof ProgressBar progressBar) {
                progressBar.setTextColor(color);
            } else {
                component.setForeground(color);
            }
        });
        return LuaValue.NIL;
    }

    private LuaValue luaSetBackground(Varargs args) {
        Color fallback = component instanceof ProgressBar progressBar
                ? progressBar.getTrackColor()
                : component.getBackground();
        Color color = colorArg(args, 1, fallback);
        runOnEdt(() -> {
            if (component instanceof ProgressBar progressBar) {
                progressBar.setTrackColor(color);
            } else {
                component.setBackground(color);
            }
        });
        return LuaValue.NIL;
    }

    private LuaValue luaPutProperty(Varargs args) {
        String key = stringArg(args, 1, "");
        LuaValue value = arg(args, 2);
        if (!key.isBlank()) {
            runOnEdt(() -> component.putClientProperty(key, javaValue(value)));
        }
        return LuaValue.NIL;
    }

    private LuaValue luaGetProperty(Varargs args) {
        String key = stringArg(args, 1, "");
        return key.isBlank() ? LuaValue.NIL : toLuaValue(component.getClientProperty(key));
    }

    private LuaValue luaRequestFocus(Varargs args) {
        runOnEdt(component::requestFocusInWindow);
        return LuaValue.NIL;
    }

    private LuaValue luaRepaint(Varargs args) {
        runOnEdt(component::repaint);
        return LuaValue.NIL;
    }

    private LuaValue luaEmit(Varargs args) {
        String eventName = stringArg(args, 1, "");
        LuaValue payload = arg(args, 2);
        if (!eventName.isBlank()) {
            context.emit(eventName, this, payload);
        }
        return LuaValue.NIL;
    }
}
