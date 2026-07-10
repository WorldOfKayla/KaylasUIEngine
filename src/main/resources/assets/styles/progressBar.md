# Composite ProgressBar

`type="progressBar"` creates `org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar`.
The component is a `CompositeComponent` with three named visual layers:

- `track` — background, border and optional track texture.
- `fill` — determinate/indeterminate fill, optional texture and stripes.
- `text` — centered or aligned text with independent color and shadow.

The component keeps a `BoundedRangeModel`, supports smooth value interpolation, indeterminate mode,
vertical/horizontal orientation, inversion and Lua runtime control.

## Value configuration

| Property | Type | Default | Meaning |
|---|---:|---:|---|
| `minValue` | int | `0` | Minimum model value. |
| `maxValue` | int | `minValue + 100` | Maximum model value. |
| `initialValue` | int/string | `minValue` | Initial value, clamped to the configured range. |
| `indeterminate` | boolean | `false` | Enables moving indeterminate segment. |
| `orientation` | string | `horizontal` | `horizontal` or `vertical`. |
| `inverted` | boolean | `false` | Reverses fill direction. |

## Track and fill

| Property | Type | Default/source | Meaning |
|---|---:|---:|---|
| `trackColor` | color | style `trackColor` / `background` | Track color. |
| `fillColor` | color | style `fillColor` / legacy `color` | Fill color. |
| `borderColor` | color | style `borderColor` | Border color. |
| `borderWidth` | int | style | Border width in pixels. |
| `borderPainted` | boolean | style | Enables border rendering. |
| `borderRadius` | int | style | Track corner radius. |
| `fillBorderRadius` | int | style / track radius | Fill corner radius. |
| `trackPadding` | int | style | Inner padding between track and fill. |
| `trackImage` | resource path | style | Optional track texture. |
| `fillImage` | resource path | style `texture` | Optional fill texture. |
| `textureMode` | string | `stretch` | `stretch`, `tile`, `cover` or `contain`. |

`color` remains a backward-compatible alias for `fillColor`; it no longer controls progress text.

## Text

| Property | Type | Default/source | Meaning |
|---|---:|---:|---|
| `stringPainted` | boolean | style | Enables the internal text layer. |
| `showPercent` | boolean | `true` | Includes percentage output. When disabled, localized/custom text remains visible. |
| `textColor` | color | `#ffffffff` | Text color. |
| `textShadowColor` | color | `#000000a0` | Text shadow color. |
| `textShadow` | boolean | `true` | Enables text shadow. |
| `textShadowOffsetX` | int | `1` | Horizontal shadow offset. |
| `textShadowOffsetY` | int | `1` | Vertical shadow offset. |
| `progressText` | string | generated | Fixed custom string. |
| `localeKey` | string | none | Localized fixed string when `progressText` is absent. |
| `progressTextFormat` | string | `{percent}%` | Dynamic text template. |
| `font` | string | style font | Font resource/name override. |
| `fontSize` | int | style font size | Text size. |
| `fontStyle` | string | `plain` | `plain`, `bold`, `italic` or `boldItalic`. |
| `alignment` | string | style `align` | `left`, `center` or `right`. |

Available `progressTextFormat` tokens:

- `{value}`
- `{min}`
- `{max}`
- `{percent}`
- `{text}` — custom/runtime text set through `progressText`, `localeKey` or `setString()`.

## Stripes and animation

| Property | Type | Default/source | Meaning |
|---|---:|---:|---|
| `striped` | boolean | style | Enables diagonal fill stripes. |
| `stripeColor` | color | `#ffffff26` | Stripe color. |
| `stripeWidth` | int | `12` | Stripe width. |
| `stripeGap` | int | `8` | Gap between stripes. |
| `stripeSpeedMs` | int | `0` | Time for one stripe period; `0` disables movement. |
| `animateValue` | boolean | `true` | Smoothly interpolates model value changes. |
| `animationDurationMs` | int | `140` | Value animation duration. |
| `animationFrameDelayMs` | int | `16` | Swing timer frame delay. |
| `indeterminateCycleMs` | int | `1200` | Indeterminate ping-pong cycle duration. |
| `indeterminateSizePercent` | int | `28` | Indeterminate segment size, clamped to `5..100`. |
| `antialias` | boolean | `true` | Enables shape/text antialiasing. |

## XML example

```xml
<component type="progressBar"
           style="progressMini"
           id="loadProgress"
           minValue="0"
           maxValue="100"
           initialValue="0"
           stringPainted="true"
           showPercent="false"
           textColor="#ffffffff"
           font="mcfont"
           fontSize="11"
           fontStyle="plain"
           textShadowColor="#000000d0"
           progressTextFormat="{text} — {percent}%"
           fillColor="#ffee61ff"
           trackColor="#15191dff"
           borderColor="#183824ff"
           borderWidth="1"
           borderPainted="true"
           borderRadius="20"
           fillBorderRadius="18"
           trackPadding="2"
           striped="true"
           stripeSpeedMs="700"
           animateValue="true">
    <bounds x="40" y="100" width="350" height="30" />
</component>
```

## JSON5 example

```json5
{
  type: "progressBar",
  style: "progress",
  id: "downloadProgress",
  minValue: 0,
  maxValue: 100,
  initialValue: 0,
  stringPainted: true,
  showPercent: true,
  progressTextFormat: "{value} / {max} — {percent}%",
  textColor: "#ffffffff",
  font: "mcfont",
  fontSize: 11,
  fontStyle: "bold",
  fillColor: "#b0976bff",
  trackColor: "#202720ff",
  orientation: "horizontal",
  animateValue: true,
  bounds: { x: 5, y: 370, width: 790, height: 30 }
}
```

## Lua runtime API

```lua
local value = component:getValue()
local minimum = component:getMinimum()
local maximum = component:getMaximum()
local percent = component:getPercent()
local showPercent = component:isShowPercent()
local currentStyle = component:getStyle()
local fontName = component:getFontName()
local fontSize = component:getFontSize()
local fontStyle = component:getFontStyle()

component:setStyle("progressMini")
component:setFont("mcfont", 11, "bold")
component:setValue(45)
component:setMinimum(0)
component:setMaximum(100)
component:setStringPainted(true)
component:setShowPercent(false)
component:setString("Downloading...")
component:setTextFormat("{percent}%")
component:setTextColor("#ffffffff")
component:setTrackColor("#202720ff")
component:setFillColor("#b0976bff")
component:setIndeterminate(false)
component:setInverted(false)
component:setOrientation("horizontal")
```

The built-in progress script emits `componentType:progressBar:<event>` with the current value,
range, percent, text, orientation, inversion and indeterminate state.
