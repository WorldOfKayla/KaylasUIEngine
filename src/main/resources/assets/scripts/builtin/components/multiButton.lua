-- KaylasUIEngine built-in script for multiButton.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.multiButton", true)
end

component:emit("componentType:multiButton:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
