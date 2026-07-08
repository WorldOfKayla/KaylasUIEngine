-- KaylasUIEngine built-in script for label.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.label", true)
end

component:emit("componentType:label:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
