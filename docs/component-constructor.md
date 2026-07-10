# Component Constructor Runtime 2.1

## Purpose

Component Constructor separates the engine-level component model from Swing inheritance.

Swing controls cannot share one Java superclass because they already inherit from different framework classes such as `JButton`, `JSlider`, `JLabel`, and `JTextField`. KaylasUI therefore uses one common hierarchy for **component definitions**:

```text
AbstractComponentDefinition<T extends JComponent>
├── ComponentDefinition<T>            BASIC or prebuilt COMPOSITE implementation
└── CompositeComponentDefinition      declarative reusable component graph
```

Every definition is stored in one `ComponentCatalog`. The launcher can query, replace, derive, compose, and instantiate these definitions through `ComponentConstructor` and `ComponentFactory`.

## Runtime architecture

```text
Launcher
  └── ComponentConstructor
        ├── ComponentCatalog
        │     ├── BASIC definitions
        │     └── COMPOSITE definitions
        └── CompositeComponentDefinition.Builder
              ├── ComponentNode prototypes
              ├── ComponentConnection routes
              └── layout policy

XML / JSON descriptor
  └── ComponentFactory
        └── AbstractComponentDefinition.create(context)
              ├── basic Swing component
              └── ConstructedCompositeComponent
                    ├── instance.child scoped ids
                    ├── child component graph
                    └── scoped Lua signal routes
```

## Component kinds

```java
public enum ComponentKind {
    BASIC,
    COMPOSITE
}
```

A built-in implementation such as `button` is `BASIC`. Prebuilt engine composites such as `compositeSlider`, `fileSelector`, and `compositeComponent` are cataloged as `COMPOSITE`. Launcher-defined graphs created with `CompositeComponentDefinition` are also `COMPOSITE`.

## Component catalog

```java
ComponentCatalog catalog = guiBuilder.getComponentCatalog();

Map<String, AbstractComponentDefinition<? extends JComponent>> all =
        catalog.definitions();

Map<String, AbstractComponentDefinition<? extends JComponent>> composites =
        catalog.definitions(ComponentKind.COMPOSITE);

Optional<AbstractComponentDefinition<? extends JComponent>> definition =
        catalog.find("linked-status-control");
```

The catalog provides:

- case-insensitive canonical lookup;
- aliases;
- separation by `ComponentKind`;
- replacement of definitions;
- alias cleanup when a definition is replaced;
- immutable snapshots for launcher inspection.

## Registering a basic component

```java
ComponentConstructor constructor = guiBuilder.getComponentConstructor();

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

The definition inherits from `AbstractComponentDefinition`. Its Swing product remains free to inherit from the correct Swing class.

## Building a reusable composite

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

The composite can then be used like a built-in component:

```xml
<component
    type="linkedStatusControl"
    id="networkControl"
    style="default"
    visible="true">
    <bounds x="24" y="80" width="430" height="36" />
</component>
```

## Prototype isolation

Every `ComponentNode` stores a defensive descriptor copy. Every runtime instance receives another copy. Mutating one instance cannot modify the catalog prototype or another instance.

```text
catalog prototype
    └── copy for instance A
    └── copy for instance B
```

`ComponentAttributes.copy()` performs a deep descriptor copy, including child components, scripts, styles, properties, bounds, and layout configuration.

## Scoped runtime identifiers

A composite instance id becomes its signal and lookup scope:

```text
networkControl                  root
networkControl.toggle           child
networkControl.status           child
```

Local node ids remain stable inside the definition, while fully qualified ids remain unique across multiple instances.

```java
ConstructedCompositeComponent component = ...;

JComponent toggle = component.getNode("toggle");
JComponent status = component.getNode("status");
String globalStatusId = component.qualify("status");
```

Use `$root` in a connection to address the composite root.

## Declarative component connections

```java
.connect(
    "toggle",        // source local node
    "action",        // source event
    "status",        // target local node
    "linkedChanged"  // target event
)
```

When the source event fires:

1. source event scripts execute;
2. global and targeted Lua subscribers execute;
3. matching routes are resolved;
4. source value becomes the routed payload when no explicit payload exists;
5. target event scripts execute;
6. targeted subscribers of the target execute.

Route cycles are bounded by the Lua event-depth guard and repeated route ids are not traversed twice in one delivery path.

## Lua targeted listeners

A component can listen only to events addressed to itself:

```lua
if event.name == "init" then
    component:on("linkedChanged", function(signal, target)
        target:setText("linked: " .. tostring(signal.payload))
        target:putProperty("signal.received", true)
    end)
end
```

The handler receives:

- `signal`: routed event table;
- `target`: Lua API table of the target component.

Routed event fields:

| Field | Meaning |
| --- | --- |
| `name` | Target event name |
| `payload` | Explicit payload or source component value |
| `routed` | `true` for routed delivery |
| `routeId` | Unique route id |
| `scopeId` | Composite instance scope |
| `sourceComponentId` | Fully qualified source id |
| `sourceEvent` | Original source event |
| `targetComponentId` | Fully qualified target id |
| `targetEvent` | Delivered target event |

## Lua global API

### Global listener

```lua
ui.on("applicationReady", function(event, component)
    ui.log("Application ready")
end)
```

### Targeted listener

```lua
ui.on("networkControl.status", "linkedChanged", function(event, target)
    target:setText("updated")
end)
```

### Connect components dynamically

```lua
local routeId = ui.connect(
    "networkControl.toggle",
    "action",
    "networkControl.status",
    "linkedChanged",
    "networkControl"
)
```

### Disconnect

```lua
ui.disconnect(routeId)
```

### Directed send

```lua
ui.send("networkControl.status", "refresh", {
    reason = "manual"
})
```

## Lua component API

Every component table provides:

```lua
component:on(eventName, handler)
component:send(targetId, eventName, payload)
component:sendLocal(localId, eventName, payload)
component:connect(sourceEvent, targetId, targetEvent, scopeId)
component:connectLocal(sourceEvent, targetLocalId, targetEvent)
component:findLocal(localId)
component:getScopeId()
component:getLocalId()
```

Example from a child component:

```lua
component:connectLocal("change", "status", "valueChanged")
component:sendLocal("status", "refresh", component:getValue())
```

## Lifecycle and cleanup

`ConstructedCompositeComponent` owns its connections. On removal it releases:

- signal routes in its scope;
- targeted Lua subscriptions in its scope;
- component and API registry entries for the root and children.

This prevents repeated creation of a launcher screen from retaining Lua closures or stale component instances.

## Validation

Registration fails when:

- a node references an unknown component type;
- a composite directly contains itself;
- a connection references a missing local node;
- a local node id is duplicated;
- required type, node, or event identifiers are blank.

Runtime construction additionally detects recursive composite creation through the active creation-context stack.

## Recommended launcher organization

```text
launcher/ui/components/
├── LauncherComponentLibrary.java
├── AccountSelectorDefinition.java
├── InstallLocationDefinition.java
├── RuntimeSelectorDefinition.java
└── scripts/
    ├── account-selector.lua
    ├── install-location.lua
    └── runtime-selector.lua
```

Register definitions immediately after `buildGui(...)` creates the `GuiBuilder` and before loading XML/JSON screens that reference custom types.
