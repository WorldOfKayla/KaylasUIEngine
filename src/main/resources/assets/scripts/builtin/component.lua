-- KaylasUIEngine built-in component lifecycle script.
-- Loaded automatically for every component before descriptor-level scripts.

local id = component:getId()
local type = component:getType()

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin", true)
    component:putProperty("kaylas.ui.script.componentType", type)
    component:putProperty("kaylas.ui.script.componentId", id)
end

component:emit("component:" .. event.name, {
    id = id,
    type = type,
    value = event.value
})

component:emit("component:" .. type .. ":" .. event.name, {
    id = id,
    type = type,
    value = event.value
})
