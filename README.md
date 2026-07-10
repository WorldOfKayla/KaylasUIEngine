# KaylasUI Engine

**Версия:** `2.1.0-AURELIA`
**Runtime:** Java 17 / Swing
**Кодовое имя:** `AURELIA 2 — Constructor Runtime`

KaylasUI Engine — декларативный Swing UI runtime с XML-дескрипторами, наследуемыми стилями, Lua-сценариями и каталогом базовых и составных компонентов.

## AURELIA 2.1

Версия 2.1 вводит **Component Constructor Runtime**:

- единый `AbstractComponentDefinition` для всех записей каталога;
- типы `BASIC` и `COMPOSITE`;
- общую базу компонентов `ComponentCatalog`;
- launcher-facing API `ComponentConstructor`;
- reusable composite graphs из существующих компонентов;
- изолированные prototypes и scoped runtime ids;
- декларативные связи между дочерними компонентами;
- targeted Lua listeners и directed signal routing;
- автоматическое освобождение routes, Lua closures и component registry entries;
- защита от неизвестных child types, alias collisions и рекурсивных graphs.

Общий абстрактный класс относится к **engine definitions**, а не к Swing instances. Это сохраняет корректное наследование `JButton`, `JSlider`, `JLabel`, `JTextField` и других Swing-классов.

Документация:

- [Component Constructor Runtime 2.1](docs/component-constructor.md)
- [Component Accessor Runtime](docs/component-accessor.md)
- [Components Runtime 2.0](docs/components.md)

## Component Accessor Runtime

`componentAccessor` предоставляет refreshable index для обычных и составных Swing-компонентов:

```java
ComponentsAccessor accessor = new ComponentsAccessor(
        getGuiBuilder(),
        "settings",
        List.of(TextField.class, Checkbox.class),
        ComponentAccessorOptions.builder()
                .valueMode(ComponentValueMode.NATIVE)
                .duplicatePolicy(DuplicateComponentPolicy.FAIL)
                .build()
);
```

Scoped child lookup:

```java
Slider slider = accessor.requireLocal("volume", "slider", Slider.class);
```

Field binding:

```java
@Component(scope = "volume", localId = "slider")
private Slider volumeSlider;
```

Value adapters are extensible through `ComponentValueRegistry`, while snapshots and form maps are immutable.

## Каталог компонентов

```java
ComponentCatalog catalog = getGuiBuilder().getComponentCatalog();

List<String> basicTypes = catalog.types(ComponentKind.BASIC);
List<String> compositeTypes = catalog.types(ComponentKind.COMPOSITE);

AbstractComponentDefinition<? extends JComponent> definition =
        catalog.find("linked-status-control").orElseThrow();
```

Встроенные `button`, `label`, `slider`, `textField` и другие атомарные реализации зарегистрированы как `BASIC`. `compositeSlider`, `fileSelector`, `compositeComponent` и launcher-defined graphs зарегистрированы как `COMPOSITE`.

## Создание базового типа

```java
ComponentConstructor constructor = getGuiBuilder().getComponentConstructor();

ComponentDefinition<JButton> commandButton = constructor
        .<JButton>basic("commandButton")
        .defaultStyle("buttonMain")
        .aliases("command", "cmd-button")
        .creator(context -> new JButton())
        .configure((button, context) -> {
            button.setText(context.engine().getLANG().getString(
                    context.attributes().getLocaleKey()
            ));
            button.setActionCommand(context.componentId());
        })
        .build();

constructor.register(commandButton);
```

## Создание составного типа

```java
ComponentAttributes toggle = ComponentAttributes.builder("checkbox")
        .style("solid1")
        .localeKey("launcher.enableFeature")
        .bounds(0, 0, 190, 32)
        .build();

ComponentAttributes status = ComponentAttributes.builder("label")
        .style("promptLabel")
        .localeKey("launcher.ready")
        .bounds(200, 0, 220, 32)
        .script("assets/scripts/components/linked-status.lua")
        .build();

CompositeComponentDefinition linkedStatus = constructor
        .composite("linkedStatusControl")
        .alias("linked-status-control")
        .layout(CompositeComponent.LayoutMode.ABSOLUTE)
        .child("toggle", toggle)
        .child("status", status)
        .connect("toggle", "action", "status", "linkedChanged")
        .build();

constructor.register(linkedStatus);
```

После регистрации новый тип используется так же, как встроенный:

```xml
<component
    type="linkedStatusControl"
    id="networkControl"
    style="default"
    visible="true">
    <bounds x="24" y="80" width="430" height="36" />
</component>
```

Runtime ids дочерних компонентов scoped по instance id:

```text
networkControl
networkControl.toggle
networkControl.status
```

## Lua-связи между компонентами

Targeted listener целевого компонента:

```lua
if event.name == "init" then
    component:on("linkedChanged", function(signal, target)
        target:setText("linked: " .. tostring(signal.payload))
        target:putProperty("signal.received", true)
    end)
end
```

Динамическое соединение:

```lua
local routeId = ui.connect(
    "networkControl.toggle",
    "action",
    "networkControl.status",
    "linkedChanged",
    "networkControl"
)
```

Directed send:

```lua
ui.send("networkControl.status", "refresh", {
    reason = "manual"
})
```

Локальная адресация из дочернего компонента:

```lua
component:connectLocal("change", "status", "valueChanged")
component:sendLocal("status", "refresh", component:getValue())
local status = component:findLocal("status")
```

## Programmatic descriptor

```java
ComponentAttributes attributes = ComponentAttributes.builder("button")
        .id("launchButton")
        .style("buttonMain", "compact")
        .styleOverride("fontSize", 14)
        .bounds(32, 32, 220, 48)
        .localeKey("launcher.start")
        .enabled(true)
        .visible(true)
        .accessible("Start game", "Starts the selected game profile")
        .property("telemetry.role", "primary-action")
        .script("action", "assets/scripts/start.lua")
        .build();

JComponent component = getGuiBuilder()
        .getComponentFactory()
        .createComponent(attributes);
```

## Наследование и композиция стилей

```json
{
  "styles": {
    "button": {
      "default": {
        "font": "Primary",
        "fontSize": 13,
        "color": "#ffffff"
      },
      "danger": {
        "extends": "default",
        "color": "#ff5c68"
      },
      "compact": {
        "fontSize": 11,
        "borderRadius": 6
      }
    }
  }
}
```

```xml
<component type="button" style="danger compact" id="deleteButton">
    <bounds x="20" y="20" width="180" height="40" />
    <styleOverrides>
        <property name="fontSize" value="12" />
    </styleOverrides>
</component>
```

Приоритет:

```text
inherited parent styles
    -> ordered descriptor styles
        -> inline styleOverrides
            -> component-specific descriptor fields
```

## Сборка и проверка

```bash
./gradlew test componentRuntimeCheck componentAccessorCheck componentAccessorJavadoc smokeRun
```

Для Windows:

```powershell
.\gradlew.bat test componentRuntimeCheck componentAccessorCheck componentAccessorJavadoc smokeRun
```
