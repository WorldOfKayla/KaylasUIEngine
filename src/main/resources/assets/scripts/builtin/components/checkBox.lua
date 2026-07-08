-- KaylasUIEngine built-in script for checkBox.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.checkBox", true)
end

component:emit("componentType:checkBox:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
