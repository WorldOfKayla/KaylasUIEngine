-- KaylasUIEngine built-in script for fileSelector.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.fileSelector", true)
end

component:emit("componentType:fileSelector:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
