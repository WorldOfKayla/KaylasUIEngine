-- KaylasUIEngine built-in script for passField.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.passField", true)
end

component:emit("componentType:passField:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
