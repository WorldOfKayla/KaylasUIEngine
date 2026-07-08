-- KaylasUIEngine built-in script for textArea.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.textArea", true)
end

component:emit("componentType:textArea:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
