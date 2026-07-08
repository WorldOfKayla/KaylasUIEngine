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

- `component` — current component API.
- `event` — current event metadata.
- `ui` — UI registry and logging API.
- `engine` — limited engine API.

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

Use `*` or `all` in the `scripts` map to run a script on every event.
