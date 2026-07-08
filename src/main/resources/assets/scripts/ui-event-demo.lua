-- Demo Lua handler for KaylasUIEngine UI components.
-- This script is safe as a template: it logs all events and performs tiny visual feedback.

ui.log("event=" .. event.name .. " component=" .. component:getId() .. " value=" .. tostring(event.value))

if event.name == "hover" then
    component:putProperty("lua.hover", true)
    component:repaint()
end

if event.name == "hoverExit" then
    component:putProperty("lua.hover", false)
    component:repaint()
end

if event.name == "click" or event.name == "action" then
    component:requestFocus()
end
