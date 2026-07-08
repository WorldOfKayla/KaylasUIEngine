-- KaylasUIEngine built-in script for textField.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.textField", true)
end

component:emit("componentType:textField:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
