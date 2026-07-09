-- KaylasUIEngine built-in script for combobox.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.combobox", true)
end

component:emit("componentType:combobox:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
