-- KaylasUIEngine built-in script for spriteImage.
-- Loaded automatically for every component of this type.

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.spriteImage", true)
end

component:emit("componentType:spriteImage:" .. event.name, {
    id = component:getId(),
    type = component:getType(),
    value = event.value
})
