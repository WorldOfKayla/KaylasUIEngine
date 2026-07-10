# KaylasUI Engine

**Версия:** `2.0.0-AURELIA`
**Runtime:** Java 17 / Swing
**Кодовое имя:** `AURELIA 2`

KaylasUI Engine — декларативный Swing UI runtime с JSON, JSON5 и XML-дескрипторами, наследуемыми стилями, Lua-сценариями и расширяемой фабрикой компонентов.

## Что изменилось в 2.0

- `ComponentFactory` переведена на изолированный `ComponentCreationContext`.
- Вложенное и асинхронное создание больше не использует общие mutable `style/attributes/bounds`.
- Добавлены `ComponentDefinition`, aliases и наследование definition pipelines.
- Создание Swing-компонентов через `createComponentAsync` всегда выполняется на EDT.
- Стили поддерживают `extends`, `inherits`, `parent`, ordered composition и inline overrides.
- XML-дескрипторы поддерживают `styleClasses`, `styleOverrides`, `properties` и `scripts`.
- Общие свойства компонентов: `enabled`, `visible`, `opaque`, `focusable`, `doubleBuffered`, cursor и accessibility metadata.
- Добавлен programmatic `ComponentAttributes.Builder`.

Подробный контракт компонентов: [`docs/components.md`](docs/components.md).

## Подключение движка

Создайте класс, наследующий `org.takesome.kaylasEngine.Engine`:

```java
public final class Launcher extends Engine {
    public Launcher() {
        super(
                Runtime.getRuntime().availableProcessors(),
                "launcher-worker",
                Map.of()
        );
    }

    @Override
    protected void preInit() {
    }

    @Override
    public void init() {
    }

    @Override
    protected void postInit() {
    }

    // Реализуйте GuiBuilderListener, FocusStatusListener и ActionListener callbacks.
}
```

## Programmatic component descriptor

```java
ComponentAttributes attributes = ComponentAttributes.builder("button")
        .id("launchButton")
        .style("buttonMain", "compact")
        .styleOverride("fontSize", 14)
        .styleOverride("fontStyle", "bold")
        .bounds(32, 32, 220, 48)
        .localeKey("launcher.start")
        .enabled(true)
        .visible(true)
        .accessible("Start game", "Starts the selected game profile")
        .property("telemetry.role", "primary-action")
        .build();

JComponent component = getGuiBuilder()
        .getComponentFactory()
        .createComponent(attributes);
```

## Наследование стилей

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

Композиция выполняется слева направо:

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

## Расширение ComponentFactory

```java
ComponentDefinition<JButton> definition = ComponentDefinition
        .<JButton>builder("commandButton")
        .defaultStyle("default")
        .aliases("command", "cmd-button")
        .creator(context -> new JButton())
        .configure((button, context) -> button.setText(
                context.engine().getLANG().getString(context.attributes().getLocaleKey())
        ))
        .build();

factory.registerDefinition(definition);
```

Definition можно наследовать без создания дополнительного Java subclass:

```java
ComponentDefinition<JButton> destructive = definition
        .derive("destructiveCommandButton")
        .defaultStyle("danger")
        .configure((button, context) -> button.putClientProperty("destructive", true))
        .build();

factory.registerDefinition(destructive);
```

## Сборка и проверка

```bash
./gradlew clean test smokeRun
```

Для Windows:

```powershell
.\gradlew.bat clean test smokeRun
```
