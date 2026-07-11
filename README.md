# KaylasUI Engine

**Version:** `2.2.0-AURELIA`

**Runtime:** Java 17 / Swing

**Codename:** `AURELIA 2 — Extensible Runtime`

KaylasUI Engine is a declarative Swing UI runtime with XML descriptors, inheritable styles, Lua scripting, and a catalog of basic and composite components.

## AURELIA 2.2

Version 2.2 introduces the **Extensible Component Runtime**:

- engine-wide component configuration groups;
- deep configuration composition before Swing object creation;
- global, type, group/type, instance, and group/instance fragments;
- runtime activation and deactivation of configuration groups;
- structural extension of composite components through `childComponents`;
- collection merge strategies: `replace`, `append`, `prepend`, and `unique_append`;
- the generic built-in `tabs` composite;
- Lua-driven tab selection, navigation, visibility, enablement, and transition events.

`tabs` is not a settings-specific mechanism. It is an ordinary composite component and the first consumer of the general configuration-extension pipeline. The same mechanism can extend future menus, toolbars, accordions, forms, panels, and existing component types.

## AURELIA 2.1

Version 2.1 introduced the **Component Constructor Runtime**:

- one `AbstractComponentDefinition` hierarchy for every catalog entry;
- `BASIC` and `COMPOSITE` component kinds;
- the shared `ComponentCatalog` registry;
- the launcher-facing `ComponentConstructor` API;
- reusable composite graphs assembled from existing components;
- isolated prototypes and scoped runtime IDs;
- declarative connections between child components;
- targeted Lua listeners and directed signal routing;
- automatic cleanup of routes, Lua closures, and component-registry entries;
- validation for unknown child types, alias collisions, and recursive graphs.

The common abstract class represents **engine definitions**, not Swing instances. Swing components therefore retain their correct inheritance from `JButton`, `JSlider`, `JLabel`, `JTextField`, and other framework classes.

Documentation:

- [Component Configuration Groups 2.2](docs/COMPONENT_CONFIG_GROUPS_2_2.md)
- [Component Constructor Runtime 2.1](docs/component-constructor.md)
- [Component Accessor Runtime](docs/component-accessor.md)
- [Components Runtime 2.0](docs/components.md)

## Component Accessor Runtime

`componentAccessor` provides a refreshable index for ordinary and composite Swing components:

```java
ComponentsAccessor accessor = new ComponentsAccessor(
        getGuiBuilder(),
        "settings",
        List.of(TextField.class, Checkbox.class),
        ComponentAccessorOptions.builder()
                .valueMode(ComponentValueMode.NATIVE)
                .duplicatePolicy(DuplicateComponentPolicy.FAIL)
                .build()
);
```

Scoped child lookup:

```java
Slider slider = accessor.requireLocal("volume", "slider", Slider.class);
```

Field binding:

```java
@Component(scope = "volume", localId = "slider")
private Slider volumeSlider;
```

Value adapters are extensible through `ComponentValueRegistry`, while snapshots and form maps are immutable.

## Component catalog

```java
ComponentCatalog catalog = getGuiBuilder().getComponentCatalog();

List<String> basicTypes = catalog.types(ComponentKind.BASIC);
List<String> compositeTypes = catalog.types(ComponentKind.COMPOSITE);

AbstractComponentDefinition<? extends JComponent> definition =
        catalog.find("linked-status-control").orElseThrow();
```

Built-in atomic implementations such as `button`, `label`, `slider`, and `textField` are registered as `BASIC`. `compositeSlider`, `fileSelector`, `compositeComponent`, `tabs`, and launcher-defined graphs are registered as `COMPOSITE`.

## Creating a basic type

```java
ComponentConstructor constructor = getGuiBuilder().getComponentConstructor();

ComponentDefinition<JButton> commandButton = constructor
        .<JButton>basic("commandButton")
        .defaultStyle("buttonMain")
        .aliases("command", "cmd-button")
        .creator(context -> new JButton())
        .configure((button, context) -> {
            button.setText(context.engine().getLANG().getString(
                    context.attributes().getLocaleKey()
            ));
            button.setActionCommand(context.componentId());
        })
        .build();

constructor.register(commandButton);
```

## Creating a composite type

```java
ComponentAttributes toggle = ComponentAttributes.builder("checkbox")
        .style("solid1")
        .localeKey("launcher.enableFeature")
        .bounds(0, 0, 190, 32)
        .build();

ComponentAttributes status = ComponentAttributes.builder("label")
        .style("promptLabel")
        .localeKey("launcher.ready")
        .bounds(200, 0, 220, 32)
        .script("assets/scripts/components/linked-status.lua")
        .build();

CompositeComponentDefinition linkedStatus = constructor
        .composite("linkedStatusControl")
        .alias("linked-status-control")
        .layout(CompositeComponent.LayoutMode.ABSOLUTE)
        .child("toggle", toggle)
        .child("status", status)
        .connect("toggle", "action", "status", "linkedChanged")
        .build();

constructor.register(linkedStatus);
```

After registration, the new type is used exactly like a built-in component.

Boolean state in XML is expressed with empty markers. Missing `visible`, `enabled`, and `opaque` markers resolve to `false`; enabled state uses the mutually exclusive `<enabled/>` and `<disabled/>` markers.

```xml
<component
    type="linkedStatusControl"
    id="networkControl"
    style="default">
    <visible/>
    <enabled/>
    <bounds x="24" y="80" width="430" height="36" />
</component>
```

Child runtime IDs are scoped by the composite instance ID:

```text
networkControl
networkControl.toggle
networkControl.status
```

## Lua connections between components

A targeted listener on the destination component:

```lua
if event.name == "init" then
    component:on("linkedChanged", function(signal, target)
        target:setText("linked: " .. tostring(signal.payload))
        target:putProperty("signal.received", true)
    end)
end
```

A dynamic connection:

```lua
local routeId = ui.connect(
    "networkControl.toggle",
    "action",
    "networkControl.status",
    "linkedChanged",
    "networkControl"
)
```

Directed delivery:

```lua
ui.send("networkControl.status", "refresh", {
    reason = "manual"
})
```

Local addressing from a child component:

```lua
component:connectLocal("change", "status", "valueChanged")
component:sendLocal("status", "refresh", component:getValue())
local status = component:findLocal("status")
```

## Programmatic descriptor

```java
ComponentAttributes attributes = ComponentAttributes.builder("button")
        .id("launchButton")
        .style("buttonMain", "compact")
        .styleOverride("fontSize", 14)
        .bounds(32, 32, 220, 48)
        .localeKey("launcher.start")
        .enabled(true)
        .visible(true)
        .accessible("Start game", "Starts the selected game profile")
        .property("telemetry.role", "primary-action")
        .script("action", "assets/scripts/start.lua")
        .build();

JComponent component = getGuiBuilder()
        .getComponentFactory()
        .createComponent(attributes);
```

## Style inheritance and composition

```json
{
  "styles": {
    "button": {
      "default": {
        "font": "Primary",
        "fontSize": 13,
        "color": "#ffffff"
      },
      "danger": {
        "extends": "default",
        "color": "#ff5c68"
      },
      "compact": {
        "fontSize": 11,
        "borderRadius": 6
      }
    }
  }
}
```

```xml
<component type="button" style="danger compact" id="deleteButton">
    <visible/>
    <enabled/>
    <bounds x="20" y="20" width="180" height="40" />
    <styleOverrides>
        <property name="fontSize" value="12" />
    </styleOverrides>
</component>
```

Precedence:

```text
inherited parent styles
    -> ordered descriptor styles
        -> inline styleOverrides
            -> component-specific descriptor fields
```

## Build and verification

```bash
./gradlew test componentRuntimeCheck componentAccessorCheck componentAccessorJavadoc smokeRun
```

On Windows:

```powershell
.\gradlew.bat test componentRuntimeCheck componentAccessorCheck componentAccessorJavadoc smokeRun
```
