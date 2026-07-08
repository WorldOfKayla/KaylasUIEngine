-- KaylasUIEngine built-in script for progressBar.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.progressBar", true)
end

component:emit("componentType:progressBar:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
