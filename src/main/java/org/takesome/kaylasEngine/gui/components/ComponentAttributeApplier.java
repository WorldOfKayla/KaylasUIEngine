package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.utils.tooltip.CustomTooltip;
import org.takesome.kaylasEngine.gui.components.utils.tooltip.TooltipAttributes;
import org.takesome.kaylasEngine.gui.scripting.LuaUiScriptEngine;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.locale.LanguageProvider;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.util.Locale;
import java.util.Objects;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/** Applies descriptor, style, accessibility, tooltip and script metadata to created components. */
final class ComponentAttributeApplier {
    static final String STYLE_PROPERTY = "kaylas.ui.style";
    static final String STYLE_CHAIN_PROPERTY = "kaylas.ui.styleChain";
    static final String ATTRIBUTES_PROPERTY = "kaylas.ui.attributes";

    private final Engine engine;
    private final LanguageProvider language;
    private final LuaUiScriptEngine scriptEngine;
    private final ComponentTooltipRepository tooltipRepository;

    ComponentAttributeApplier(Engine engine,
                              LanguageProvider language,
                              LuaUiScriptEngine scriptEngine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.language = Objects.requireNonNull(language, "language");
        this.scriptEngine = Objects.requireNonNull(scriptEngine, "scriptEngine");
        this.tooltipRepository = new ComponentTooltipRepository(engine.getClass().getClassLoader());
    }

    void apply(JComponent component, ComponentCreationContext context) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(context, "context");

        ComponentAttributes attributes = context.attributes();
        StyleAttributes style = context.style();

        component.setName(attributes.getComponentId());
        component.setBounds(context.bounds());
        component.setEnabled(attributes.isEnabled());
        component.setVisible(attributes.isVisible());
        component.setOpaque(attributes.hasOpaque() ? attributes.isOpaque() : style.isOpaque());
        if (context.definition().applyBaseStyle()) {
            applyResolvedBaseStyle(component, attributes, style);
        }
        if (attributes.hasFocusable()) {
            component.setFocusable(attributes.isFocusable());
        }
        if (attributes.hasDoubleBuffered()) {
            component.setDoubleBuffered(attributes.isDoubleBuffered());
        }
        applyCursor(component, attributes.getCursor());
        applyAccessibility(component, attributes);
        attributes.getProperties().forEach(component::putClientProperty);

        component.putClientProperty(STYLE_PROPERTY, style);
        component.putClientProperty(STYLE_CHAIN_PROPERTY, context.styleChain());
        component.putClientProperty(ATTRIBUTES_PROPERTY, attributes);

        initializeTooltip(component, attributes);
        scriptEngine.bind(component, attributes);
    }

    private void applyResolvedBaseStyle(JComponent component,
                                        ComponentAttributes attributes,
                                        StyleAttributes style) {
        component.setForeground(hexToColor(valueOr(attributes.getColor(), style.getColor())));

        String background = valueOr(attributes.getBackground(), style.getBackground());
        if (background != null
                && !background.isBlank()
                && !"transparent".equalsIgnoreCase(background)) {
            component.setBackground(hexToColor(background));
        }

        component.setFont(engine.getFONTUTILS().getFont(
                valueOr(attributes.getFont(), style.getFont()),
                attributes.getFontSize() > 0 ? attributes.getFontSize() : style.getFontSize(),
                valueOr(attributes.getFontStyle(), style.getFontStyle())
        ));
    }

    private void applyAccessibility(JComponent component, ComponentAttributes attributes) {
        if (attributes.getAccessibleName() != null && !attributes.getAccessibleName().isBlank()) {
            component.getAccessibleContext().setAccessibleName(attributes.getAccessibleName());
        }
        if (attributes.getAccessibleDescription() != null
                && !attributes.getAccessibleDescription().isBlank()) {
            component.getAccessibleContext().setAccessibleDescription(
                    attributes.getAccessibleDescription()
            );
        }
    }

    private void applyCursor(JComponent component, String cursorName) {
        if (cursorName == null || cursorName.isBlank()) {
            return;
        }
        int cursorType = switch (cursorName.trim().toLowerCase(Locale.ROOT)) {
            case "hand", "pointer" -> Cursor.HAND_CURSOR;
            case "text" -> Cursor.TEXT_CURSOR;
            case "wait", "busy" -> Cursor.WAIT_CURSOR;
            case "move" -> Cursor.MOVE_CURSOR;
            case "crosshair" -> Cursor.CROSSHAIR_CURSOR;
            case "resize-e", "e-resize" -> Cursor.E_RESIZE_CURSOR;
            case "resize-w", "w-resize" -> Cursor.W_RESIZE_CURSOR;
            case "resize-n", "n-resize" -> Cursor.N_RESIZE_CURSOR;
            case "resize-s", "s-resize" -> Cursor.S_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
        component.setCursor(Cursor.getPredefinedCursor(cursorType));
    }

    private void initializeTooltip(JComponent component, ComponentAttributes attributes) {
        if (attributes.getToolTip() == null || attributes.getToolTip().isBlank()) {
            return;
        }
        String tooltipStyle = valueOr(attributes.getTooltipStyle(), "default");
        tooltipRepository.find(tooltipStyle).ifPresent(style -> attachTooltip(component, attributes, style));
    }

    private void attachTooltip(JComponent component,
                               ComponentAttributes attributes,
                               TooltipAttributes tooltipAttributes) {
        CustomTooltip tooltip = new CustomTooltip(
                hexToColor(tooltipAttributes.getBgColor()),
                hexToColor(tooltipAttributes.getTextColor()),
                tooltipAttributes.getBorderRadius(),
                engine.getFONTUTILS().getFont(
                        tooltipAttributes.getFont(),
                        tooltipAttributes.getFontSize()
                )
        );
        tooltip.attachToComponent(component, language.getString(attributes.getToolTip()), 2000);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
