package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.tabs.TabChangeEvent;
import org.takesome.kaylasEngine.gui.components.tabs.TabChangeListener;
import org.takesome.kaylasEngine.gui.components.tabs.TabDefinition;
import org.takesome.kaylasEngine.gui.components.tabs.Tabs;
import org.takesome.kaylasEngine.gui.scripting.LuaUiScriptEngine;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.utils.IconUtils;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Builds recursive composite and tab containers independently of factory registration/state. */
final class CompositeComponentBuilder {
    private final ComponentFactory factory;
    private final LanguageProvider language;
    private final IconUtils iconUtils;
    private final LuaUiScriptEngine scriptEngine;

    CompositeComponentBuilder(ComponentFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.language = factory.getLangProvider();
        this.iconUtils = factory.getIconUtils();
        this.scriptEngine = factory.getLuaUiScriptEngine();
    }

    JComponent createTabs(ComponentAttributes attributes) {
        String placement = stringProperty(
                attributes,
                "tabs.placement",
                valueOr(attributes.getOrientation(), Tabs.PLACEMENT_TOP)
        );
        int gap = intProperty(attributes, "tabs.gap", 4);
        Tabs tabs = new Tabs(placement, gap);

        for (ComponentAttributes childPrototype : attributes.getChildComponents()) {
            if (childPrototype == null) {
                continue;
            }
            ComponentAttributes child = childPrototype.copy();
            if (child.getConfigGroups().isEmpty()) {
                child.setConfigGroups(attributes.getConfigGroups());
            }

            JComponent content = factory.createComponent(child);
            String tabId = stringProperty(child, "tab.id", child.getComponentId());
            if (tabId == null || tabId.isBlank()) {
                tabId = "tab-" + tabs.getTabCount();
            }
            String titleKey = stringProperty(child, "tab.titleKey", child.getLocaleKey());
            String title = stringProperty(child, "tab.title", "");
            if (title.isBlank() && titleKey != null && !titleKey.isBlank()) {
                title = language.getString(titleKey);
            }
            if (title.isBlank()) {
                title = tabId;
            }

            Icon icon = iconUtils.getIcon(child);
            boolean enabled = booleanProperty(child, "tab.enabled", true);
            boolean visible = booleanProperty(child, "tab.visible", true);
            tabs.addTab(new TabDefinition(tabId, title, icon, enabled, visible), content);
        }

        String selected = stringProperty(attributes, "tabs.selected", "");
        if (selected.isBlank() && attributes.getInitialValue() != null) {
            selected = String.valueOf(attributes.getInitialValue());
        }
        if (!selected.isBlank()) {
            tabs.selectTab(selected, "configuration");
        } else if (attributes.getSelectedIndex() >= 0
                && attributes.getSelectedIndex() < tabs.getTabIds().size()) {
            tabs.selectTab(tabs.getTabIds().get(attributes.getSelectedIndex()), "configuration");
        }

        tabs.addTabChangeListener(new TabChangeListener() {
            @Override
            public void tabChanging(TabChangeEvent event) {
                scriptEngine.emitComponentEvent(
                        "tabChanging",
                        tabs,
                        event,
                        tabPayload(event)
                );
            }

            @Override
            public void tabChanged(TabChangeEvent event) {
                scriptEngine.emitComponentEvent(
                        "tabChanged",
                        tabs,
                        event,
                        tabPayload(event)
                );
            }
        });
        return tabs;
    }

    JComponent createComposite(ComponentAttributes attributes) {
        CompositeComponent composite = new CompositeComponent(resolveLayout(attributes));
        composite.setLayoutConfig(attributes.getLayoutConfig());
        composite.setValue(attributes.getInitialValue());

        List<ComponentAttributes> children = attributes.getChildComponents();
        if (children == null || children.isEmpty()) {
            Engine.LOGGER.debug(
                    "CompositeComponent '{}' has no child components.",
                    attributes.getComponentId()
            );
            return composite;
        }

        for (ComponentAttributes child : children) {
            if (child == null) {
                Engine.LOGGER.warn(
                        "CompositeComponent '{}' ignored a null child descriptor.",
                        attributes.getComponentId()
                );
                continue;
            }
            inheritDefaults(attributes, child);
            JComponent childComponent = factory.createComponent(child);
            composite.addSubComponent(
                    childComponent,
                    composite.getLayoutConfigFor(child.getComponentType())
            );
        }
        return composite;
    }

    private void inheritDefaults(ComponentAttributes parent, ComponentAttributes child) {
        if (child.getInitialValue() == null && parent.getInitialValue() != null) {
            child.setInitialValue(parent.getInitialValue());
        }
        if (child.getStyleChain().isEmpty()) {
            String targetedStyle = parent.getStyles().get(child.getComponentType());
            if (targetedStyle == null) {
                targetedStyle = parent.getStyles().get(normalizeType(child.getComponentType()));
            }
            if (targetedStyle != null && !targetedStyle.isBlank()) {
                child.setComponentStyle(targetedStyle);
            }
        }
    }

    private CompositeComponent.LayoutMode resolveLayout(ComponentAttributes attributes) {
        String mode = valueOr(attributes.getAlignment(), "absolute").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "vertical", "y", "box-y" -> CompositeComponent.LayoutMode.VERTICAL;
            case "horizontal", "x", "box-x" -> CompositeComponent.LayoutMode.HORIZONTAL;
            case "flow" -> CompositeComponent.LayoutMode.FLOW;
            default -> CompositeComponent.LayoutMode.ABSOLUTE;
        };
    }

    private Map<String, Object> tabPayload(TabChangeEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousTabId", event.previousTabId());
        payload.put("tabId", event.tabId());
        payload.put("index", event.index());
        payload.put("source", event.source());
        return payload;
    }

    private String stringProperty(ComponentAttributes attributes, String key, String fallback) {
        Object value = attributes.getProperties().get(key);
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isBlank() ? (fallback == null ? "" : fallback) : resolved;
    }

    private int intProperty(ComponentAttributes attributes, String key, int fallback) {
        Object value = attributes.getProperties().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean booleanProperty(ComponentAttributes attributes, String key, boolean fallback) {
        Object value = attributes.getProperties().get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("component type must not be blank");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
