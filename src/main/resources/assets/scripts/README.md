# KaylasUIEngine Lua UI scripts

Components can declare Lua scripts directly in their UI attributes.

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

Use `*` or `all` in the `scripts` map to run a script on every component event.
Prefer registering `ui.on(...)` handlers from an `init` script to avoid duplicate runtime listeners.
