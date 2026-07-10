# Components Runtime 2.0

> Version 2.1 adds the shared BASIC/COMPOSITE catalog, reusable constructor graphs, and Lua signal routing. See [Component Constructor Runtime 2.1](component-constructor.md).

## Creation pipeline

Each component is created through the following deterministic pipeline:

```text
ComponentAttributes
    -> component type / alias resolution
    -> AbstractComponentDefinition resolution through ComponentCatalog
    -> ordered style-chain resolution
    -> inline style override merge
    -> immutable ComponentCreationContext
    -> component creator
    -> definition configurators
    -> common Swing configuration
    -> tooltip and Lua binding
```

`ComponentFactory` keeps no shared mutable current style. Legacy style adapters receive the active state through a thread-local context stack. Nested composite creation pushes a child context and restores the parent context in `finally`.

## ComponentAttributes

### Common fields

| Field | Meaning | Default |
| --- | --- | --- |
| `type` | Registered type or alias | required |
| `id` | Swing name / scripting id | `null` |
| `style` | Ordered style chain separated by spaces, commas, `+` or `>` | definition default |
| `styleClasses` | Additional ordered style names | empty |
| `styleOverrides` | Final style property overrides | empty |
| `properties` | Swing client properties | empty |
| `enabled` | Component enabled state | `true` |
| `visible` | Component visibility | `true` |
| `opaque` | Explicit opacity override | resolved style |
| `editable` | Editable state for text components | `true` |
| `focusable` | Explicit focusability | component default |
| `doubleBuffered` | Explicit double-buffering state | component default |
| `cursor` | `default`, `hand`, `text`, `wait`, `move`, resize variants | unchanged |
| `accessibleName` | AccessibleContext name | unset |
| `accessibleDescription` | AccessibleContext description | unset |

Boolean fields use boxed values internally. This preserves the difference between “not specified” and an explicit `false`.

### XML example

```xml
<component
    type="button"
    style="buttonMain compact"
    id="launch"
    localeKey="launcher.start"
    cursor="hand"
    enabled="true"
    visible="true"
    accessibleName="Start game">

    <bounds x="24" y="24" width="220" height="48" />

    <styleOverrides>
        <property name="fontSize" value="14" />
        <property name="fontStyle" value="bold" />
    </styleOverrides>

    <properties>
        <property name="action.role" value="primary" />
        <property name="action.priority" value="10" type="int" />
        <property name="analytics.enabled" value="true" type="boolean" />
    </properties>

    <scripts>
        <script event="click" path="assets/scripts/launch.lua" />
    </scripts>
</component>
```

Supported property types in XML:

- `string`
- `boolean` / `bool`
- `int` / `integer`
- `long`
- `float`
- `double` / `number`
- `json`

Without an explicit type, booleans and numeric values are inferred.

## Style inheritance

A style can inherit through any of the following keys:

- `extends`
- `inherits`
- `parent`

Single inheritance:

```json
"danger": {
  "extends": "default",
  "color": "#ff5060"
}
```

Multiple inheritance:

```json
"dangerCompact": {
  "extends": ["danger", "compact"],
  "fontStyle": "bold"
}
```

Inheritance is resolved in declaration order. Cycles and unknown parents fail with an explicit error containing the inheritance path.

## Ordered style composition

Descriptors may compose independent styles:

```xml
<component type="button" style="default danger compact" />
```

Later styles override earlier styles. Mixins should normally contain only the fields they intend to override. For example:

```json
"compact": {
  "fontSize": 11,
  "borderRadius": 6
}
```

Inline overrides are applied after composition:

```xml
<styleOverrides>
    <property name="fontSize" value="13" />
</styleOverrides>
```

Nested override paths are supported by the resolver, for example `shadow.offsetX`, when a style model exposes a matching nested structure.

## Component definitions

A definition contains:

- canonical type;
- aliases;
- default style;
- creator function;
- ordered configurator chain.

```java
ComponentDefinition<JLabel> badge = ComponentDefinition
        .<JLabel>builder("badge")
        .defaultStyle("default")
        .aliases("tag", "status-badge")
        .creator(context -> new JLabel())
        .configure((label, context) -> {
            label.setText(String.valueOf(context.attributes().getInitialValue()));
            label.setHorizontalAlignment(SwingConstants.CENTER);
        })
        .build();

factory.registerDefinition(badge);
```

### Definition inheritance

```java
ComponentDefinition<JLabel> warningBadge = badge
        .derive("warningBadge")
        .defaultStyle("warning")
        .alias("warning-tag")
        .configure((label, context) -> label.putClientProperty("severity", "warning"))
        .build();
```

The derived definition inherits the parent creator and existing configurators. New configurators are appended. `configureFirst` can prepend a configurator.

## Aliases

Aliases are normalized case-insensitively. Built-in examples:

| Alias | Canonical type |
| --- | --- |
| `checkbox`, `check-box` | `checkBox` |
| `textfield`, `text-field` | `textField` |
| `textarea`, `text-area` | `textArea` |
| `progress`, `progress-bar` | `progressBar` |
| `sprite` | `spriteImage` |
| `dropdown` | `dropBox` |
| `select` | `combobox` |
| `composite` | `compositeComponent` |

## Scoped child styles

Composite components must not mutate the factory fallback style while constructing internal controls. Use `withStyle(...)` for a bounded override:

```java
StyleAttributes childStyle = engine.getStyleProvider().getStyle("button", "compact");
Button child = factory.withStyle(childStyle, () -> new Button(factory, ""));
```

The override is stack-based, supports nested composites and is removed in `finally`. `setStyle(...)` remains only as a deprecated fallback API outside active creation.

## Panel invariants

- Empty or `transparent` panel backgrounds resolve to `#00000000`, never white.
- Rounded panels are filled only when an explicit visible background or `opaque=true` is configured.
- `zIndex` is applied after the panel is attached to its parent.
- Rebuilding a panel with the same id replaces the previous Swing instance instead of layering a duplicate.
- Parent-child registry entries are unique.
- Blank layout means absolute positioning; `absolute` and `none` are explicit aliases.

## Async creation

Swing components must be created on the EDT. `createComponentAsync` now enforces this:

```java
factory.createComponentAsync(attributes)
        .thenAccept(component -> {
            // Completion happens after EDT creation.
        })
        .exceptionally(error -> {
            logger.error("Component creation failed", error);
            return null;
        });
```

The future is completed exceptionally on failure instead of silently returning `null`.

## Runtime metadata

The factory attaches the following client properties:

| Property | Value |
| --- | --- |
| `kaylas.ui.style` | Resolved `StyleAttributes` |
| `kaylas.ui.styleChain` | Ordered style list |
| `kaylas.ui.attributes` | Source `ComponentAttributes` |

Descriptor-defined `properties` are attached after the standard metadata.

## Migration from 1.x

1. Do not depend on `ComponentFactory` retaining a mutable current style outside component creation.
2. Handle exceptional completion from `createComponentAsync`.
3. Use aliases or canonical type names instead of registering duplicate names differing only by case.
4. Add a `default` style to every component style file where practical.
5. Use style mixins for reusable partial overrides and inheritance for semantic variants.
6. Prefer `ComponentDefinition` over direct registry mutation for custom basic component types.
7. Use `ComponentConstructor` and `CompositeComponentDefinition` for reusable linked component graphs.
