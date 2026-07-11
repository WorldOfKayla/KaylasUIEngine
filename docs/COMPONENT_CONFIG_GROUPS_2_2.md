# Component Configuration Groups — AURELIA 2.2

AURELIA 2.2 resolves component configuration before runtime creation. The mechanism is independent of component type: `tabs` is only the first built-in component using it.

## Resolution order

The registry applies matching fragments by priority, specificity and registration order:

1. global fragments;
2. component-type fragments;
3. active runtime groups;
4. groups declared by the component;
5. group/type fragments;
6. instance fragments;
7. group/instance extensions.

The original descriptor remains the base document. Resolution produces a new `ComponentAttributes` instance and never mutates the prototype.

## XML groups

```xml
<component type="tabs"
           id="settingsTabs"
           groups="settings compact"
           orientation="top">
    <bounds x="20" y="20" width="600" height="400"/>
    <childComponents>
        <component type="compositeComponent" id="general">
            <property name="tab.titleKey" value="settings.tabs.general"/>
            <bounds x="0" y="0" width="600" height="350"/>
        </component>
    </childComponents>
</component>
```

## Runtime registration

```java
ComponentConfigGroupRegistry groups = factory.getConfigGroupRegistry();

groups.registerGroupType(
        "compact",
        "tabs",
        Map.of("properties", Map.of("tabs.gap", 2))
);

groups.extendComponent(
        "admin",
        "settingsTabs",
        Map.of("properties", Map.of("tabs.selected", "administration"))
);

factory.activateConfigGroup("admin");
```

## Appending structure

```java
groups.appendChildren(
        "admin",
        "settingsTabs",
        List.of(ComponentAttributes.builder("compositeComponent")
                .id("administration")
                .property("tab.titleKey", "settings.tabs.administration")
                .bounds(0, 0, 600, 350)
                .build())
);
```

`appendChildren(...)` is not tabs-specific. It extends `childComponents`, so the same operation is available to any current or future composite.

## Generic collection directives

```java
Map.of(
    "childComponents", Map.of(
        "$merge", "append",
        "$value", children
    )
)
```

Supported strategies:

- `replace`
- `append`
- `prepend`
- `unique_append`

## Tabs metadata

Each child component becomes one page. Metadata is carried through generic component properties:

```xml
<properties>
    <property name="tab.id" value="general"/>
    <property name="tab.titleKey" value="settings.tabs.general"/>
    <property name="tab.enabled" value="true" type="boolean"/>
    <property name="tab.visible" value="true" type="boolean"/>
</properties>
```

Tabs-level properties:

```xml
<properties>
    <property name="tabs.placement" value="left"/>
    <property name="tabs.gap" value="4" type="int"/>
    <property name="tabs.selected" value="general"/>
</properties>
```

## Lua API

```lua
local tabs = ui.find("settingsTabs")

tabs:on("tabChanged", function(event)
    ui.log("Selected tab: " .. event.payload.tabId)
end)

tabs:select("general")
tabs:next()
tabs:previous()
tabs:setTabEnabled("administration", true)
tabs:setTabVisible("experimental", false)
```

Transition payload:

```lua
{
    previousTabId = "general",
    tabId = "administration",
    index = 2,
    source = "lua"
}
```

## Runtime group changes

Activating a group affects subsequently resolved components. Existing components can be rebuilt or refreshed by application policy; the registry intentionally does not silently reconstruct live Swing trees.

This separation keeps configuration resolution deterministic and lets applications choose safe refresh boundaries.
