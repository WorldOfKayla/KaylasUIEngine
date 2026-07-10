-- Component Constructor 2.1 targeted-listener regression fixture.
if event.name == "init" and component:getProperty("constructor.listener.bound") ~= true then
    component:putProperty("constructor.listener.bound", true)

    component:on("linkedChanged", function(signal, target)
        target:setText("linked:" .. tostring(signal.payload))
        target:putProperty("constructor.signal.received", true)
        target:putProperty("constructor.signal.source", signal.sourceComponentId)
        target:putProperty("constructor.signal.route", signal.routeId)
    end)
end
