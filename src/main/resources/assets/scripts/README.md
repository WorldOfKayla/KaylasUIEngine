# KaylasUIEngine Lua UI scripts

Every component is now connected to engine built-in Lua scripts automatically. Descriptor-level `script` and `scripts` entries are still supported, but they extend the built-in runtime instead of enabling it.

## Engine built-in scripts

The runtime attaches these scripts to every component event:

```text
assets/scripts/builtin/component.lua
assets/scripts/builtin/components/<componentType>.lua
```

The type-specific script is optional. If a custom component type has no built-in script resource, it is skipped safely.

## Loading order

For every event, scripts run in this order:

1. Engine built-in wildcard scripts.
2. Descriptor-level wildcard scripts: `script`, `scripts["*"]`, `scripts["all"]`.
3. Descriptor-level exact event scripts: `scripts["click"]`, `scripts["change"]`, etc.

## Single script for all supported events

```json
{
  "id": "playButton",
  "type": "button",
  "script": "assets/scripts/ui-event-demo.lua"
}
```

## Event-specific scripts

```json
{
  "id": "volumeSlider",
  "type": "slider",
  "scripts": {
    "init": "assets/scripts/ui-event-demo.lua",
    "change": "assets/scripts/ui-event-demo.lua",
    "hover": "assets/scripts/ui-event-demo.lua",
    "click": "assets/scripts/ui-event-demo.lua"
  }
}
```

## Runtime globals

Each Lua script receives:

- `component` — strict API for the current component.
- `event` — current event metadata.
- `ui` — shared UI runtime context.
- `engine` — limited engine API.

## Component API

```lua
component:getId()
component:getType()
component:getText()
component:setText("Text")
component:getValue()
component:setValue(42)
component:show()
component:hide()
component:enable()
component:disable()
component:setBounds(10, 10, 160, 40)
component:emit("custom:event", { source = component:getId() })
```

## UI context API

```lua
local target = ui.find("statusLabel")

ui.show("settingsPanel")
ui.hide("loginPanel")
ui.enable("playButton")
ui.disable("debugButton")
ui.setText("statusLabel", "Ready")
ui.getText("statusLabel")
ui.getValue("volumeSlider")
ui.setValue("volumeSlider", 70)

ui.on("settings:open", function(event, source)
    ui.show("settingsPanel")
    ui.hide("mainPanel")
end)

ui.emit("settings:open", { reason = "button-click" })
```

## Built-in bridge events

The engine built-in scripts emit normalized events that screen scripts can subscribe to:

```lua
ui.on("component:button:click", function(event, source)
    ui.log("button clicked: " .. event.payload.id)
end)

ui.on("componentType:slider:change", function(event, source)
    ui.log("slider changed: " .. tostring(event.payload.value))
end)
```

## Common event names

- `init`
- `click`
- `mousePressed`
- `mouseReleased`
- `hover`
- `hoverExit`
- `focus`
- `blur`
- `keyPressed`
- `keyReleased`
- `keyTyped`
- `action`
- `change`
- `textChanged`

Use `*` or `all` in the `scripts` map to run a descriptor script on every component event.
Prefer registering `ui.on(...)` handlers from an `init` script to avoid duplicate runtime listeners.
