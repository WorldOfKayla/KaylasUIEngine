-- KaylasUIEngine built-in script for dropBox.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.dropBox", true)
end

component:emit("componentType:dropBox:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
