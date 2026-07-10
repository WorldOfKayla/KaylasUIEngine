-- KaylasUIEngine built-in script for the composite progressBar component.
-- Loaded automatically for every component of this type.

local function snapshot()
    return {
        id = component:getId(),
        type = component:getType(),
        value = component:getValue(),
        minimum = component:getMinimum(),
        maximum = component:getMaximum(),
        percent = component:getPercent(),
        text = component:getString(),
        stringPainted = component:isStringPainted(),
        showPercent = component:isShowPercent(),
        indeterminate = component:isIndeterminate(),
        inverted = component:isInverted(),
        orientation = component:getOrientation(),
        style = component:getStyle(),
        fontName = component:getFontName(),
        fontSize = component:getFontSize(),
        fontStyle = component:getFontStyle()
    }
end

if event.name == "init" then
    component:putProperty("kaylas.ui.script.builtin.progressBar", true)
    component:putProperty("kaylas.ui.progress.minimum", component:getMinimum())
    component:putProperty("kaylas.ui.progress.maximum", component:getMaximum())
end

component:emit("componentType:progressBar:" .. event.name, snapshot())
