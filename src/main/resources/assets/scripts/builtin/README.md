# KaylasUIEngine built-in Lua scripts

This directory contains engine-owned Lua scripts that are attached automatically by `LuaUiScriptEngine`.

## Loading order

For every component event, the runtime executes scripts in this order:

1. Engine built-in component script: `assets/scripts/builtin/component.lua`
2. Engine built-in component-type script, if present: `assets/scripts/builtin/components/<type>.lua`
3. Descriptor-level wildcard scripts: `script` / `scripts["*"]` / `scripts["all"]`
4. Descriptor-level exact event scripts: `scripts["click"]`, `scripts["change"]`, etc.

## Built-in event bridge

The base built-in script emits generic runtime events:

```lua
component:init
component:click
component:change
component:<type>:init
component:<type>:click
component:<type>:change
```

Type scripts emit typed aliases:

```lua
componentType:button:init
componentType:button:click
componentType:slider:change
```

This allows screen scripts to subscribe to component behavior without wiring Java listeners manually.
