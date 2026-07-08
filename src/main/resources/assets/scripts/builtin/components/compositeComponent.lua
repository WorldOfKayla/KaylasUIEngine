-- KaylasUIEngine built-in script for compositeComponent.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.compositeComponent", true)
end

component:emit("componentType:compositeComponent:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
