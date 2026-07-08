-- KaylasUIEngine built-in script for button.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.button", true)
end

component:emit("componentType:button:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
