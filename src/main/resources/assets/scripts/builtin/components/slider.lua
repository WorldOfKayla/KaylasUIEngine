-- KaylasUIEngine built-in script for slider.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.slider", true)
end

component:emit("componentType:slider:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
