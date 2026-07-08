-- Demo Lua handler for KaylasUIEngine UI components.
-- Safe as a template: logs events, performs tiny visual feedback, and demonstrates ui.on/ui.emit.

if event.name == "init" then
    ui.on("demo:component-activated", function(customEvent, source)
        ui.log("custom event=" .. customEvent.name .. " payload=" .. tostring(customEvent.payload))
    end)
end

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
    component:emit("demo:component-activated", { id = component:getId(), type = component:getType() })
end
