-- KaylasUIEngine built-in script for compositeSlider.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.compositeSlider", true)
end

component:emit("componentType:compositeSlider:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
