# Component Accessor Runtime

## Purpose

`componentAccessor` is the query and form-binding layer between `GuiBuilder`, Swing component trees,
and application classes.

The updated runtime supports:

- refreshable component indexes;
- logical child-panel traversal;
- recursive Swing/container traversal;
- Component Constructor 2.1 scoped ids;
- inherited and optional field binding;
- immutable index snapshots;
- configurable duplicate handling;
- extensible typed value adapters;
- native or compatibility string form values;
- cycle and maximum-depth protection;
- custom component sources for tests and alternate UI registries.

## Architecture

```text
GuiBuilder / custom source
    -> ComponentAccessSource
        -> ComponentsAccessor.refresh()
            -> global named-component index
            -> selected panel views
            -> IndexedComponent metadata
            -> @Component field binding
            -> ComponentAccessSnapshot

JComponent
    -> ComponentValueRegistry
        -> ComponentValueAdapter<T>
            -> read native value
            -> optionally write value
```

## Compatibility constructor

Existing code remains valid:

```java
public final class Settings extends ComponentsAccessor {
    public Settings(GuiBuilder guiBuilder) {
        super(
                guiBuilder,
                "settings",
                List.of(TextField.class, Checkbox.class, Combobox.class)
        );
    }
}
```

Default options preserve the previous form behavior:

```text
value mode                 STRING
duplicate policy           REPLACE
unsupported value policy   EMPTY_STRING
nested form values         false
field injection            true
nested lookup traversal    true
```

## Global and selected indexes

Every named descendant is available through global lookup:

```java
JComponent component = accessor.getComponent("volume.slider");
Slider slider = accessor.requireComponent("volume.slider", Slider.class);
```

The configured `componentTypes` list still controls:

- `getComponentsForPanel(...)`;
- `getSelectedComponentMap()`;
- form-value eligibility.

This means internal constructor nodes can be addressed without automatically leaking into forms.

## Scoped constructor lookup

```java
Slider slider = accessor.requireLocal(
        "volume",
        "slider",
        Slider.class
);
```

Equivalent full id:

```text
volume.slider
```

Optional lookup:

```java
Optional<Spinner> spinner = accessor.findLocal(
        "ramAmount",
        "spinner",
        Spinner.class
);
```

## Field binding

### Exact id

```java
@Component("login")
private TextField login;
```

### Field name as id

```java
@Component
private TextArea settingsInfo;
```

### Constructor scope

```java
@Component(scope = "volume", localId = "slider")
private Slider volumeSlider;
```

### Optional field

```java
@Component(value = "experimentalWidget", required = false)
private Optional<JComponent> experimentalWidget;
```

Bindings are discovered in the concrete accessor class and inherited accessor subclasses.

The runtime rejects:

- static annotated fields;
- final annotated fields;
- incompatible field types;
- missing required components;
- `localId` without a scope.

## Refresh and snapshots

Dynamic GUI rebuilds are supported:

```java
ComponentAccessSnapshot snapshot = accessor.refresh();
```

A snapshot contains:

```text
revision
timestamp
root panel id
all indexed components
direct selected components by panel
IndexedComponent metadata
```

```java
long revision = accessor.getRevision();
ComponentAccessSnapshot current = accessor.snapshot();
```

Snapshots are defensive and immutable.

## Indexed metadata

```java
IndexedComponent metadata = accessor
        .findIndexedComponent("volume.slider")
        .orElseThrow();
```

Available metadata:

```text
id
component instance
logical owner panel
nearest named parent component
tree depth
nested flag
selected flag
formEligible flag
catalog component type
constructor scope
constructor local id
```

## Value registry

The default registry supports:

- `JTextComponent` and password fields;
- `AbstractButton` selections;
- `JSlider`;
- `JSpinner`;
- `JComboBox` selected index;
- `JProgressBar`;
- KaylasUI `ProgressBar`;
- `FileSelector`;
- `CompositeSlider`;
- `CompositeComponent` and constructed composite roots.

### Read and write

```java
Object value = accessor.readValue("volume.slider");
accessor.writeValue("volume.slider", 80);
```

### Custom adapter

```java
ComponentValueRegistry registry = ComponentValueRegistry.defaults()
        .registerWritable(
                JLabel.class,
                JLabel::getText,
                (label, value) -> label.setText(String.valueOf(value)),
                200
        );
```

Then pass it to the accessor:

```java
ComponentsAccessor accessor = new ComponentsAccessor(
        guiBuilder,
        "mainFrame",
        List.of(JLabel.class),
        ComponentAccessorOptions.defaults(),
        registry
);
```

Adapter resolution order:

```text
exact runtime class
    -> nearest assignable type
        -> highest adapter priority
```

## Form values

### Compatibility strings

```java
Map<String, Object> values = accessor.collectStringFormValuesForPanel("settings");
```

Examples:

```text
"true"
"75"
"Kayla"
```

### Native values

```java
Map<String, Object> values = accessor.collectNativeFormValuesForPanel("settings");
```

Examples:

```text
Boolean.TRUE
Integer.valueOf(75)
"Kayla"
```

### Configured mode

```java
ComponentAccessorOptions options = ComponentAccessorOptions.builder()
        .valueMode(ComponentValueMode.NATIVE)
        .build();
```

`getFormCredentials()` and `collectFormCredentialsForPanel(...)` use the configured mode.

## Nested form controls

Nested controls are indexed by default but excluded from form maps.

To include selected nested controls:

```java
ComponentAccessorOptions options = ComponentAccessorOptions.builder()
        .includeNestedValuesInForms(true)
        .build();
```

A nested component must also match one of the configured `componentTypes`. This prevents internal
labels and buttons from entering persisted configuration accidentally.

## Duplicate ids

```java
ComponentAccessorOptions options = ComponentAccessorOptions.builder()
        .duplicatePolicy(DuplicateComponentPolicy.FAIL)
        .build();
```

Policies:

| Policy | Behavior |
| --- | --- |
| `KEEP_FIRST` | Keep the first indexed component |
| `REPLACE` | Keep the most recently discovered component |
| `FAIL` | Throw `ComponentAccessException` |

## Unsupported form values

```java
ComponentAccessorOptions options = ComponentAccessorOptions.builder()
        .unsupportedValuePolicy(UnsupportedValuePolicy.SKIP)
        .build();
```

Policies:

| Policy | Behavior |
| --- | --- |
| `SKIP` | Do not add the component to the form map |
| `EMPTY_STRING` | Add `""` for compatibility |
| `FAIL` | Throw `ComponentAccessException` |

## Custom sources

`ComponentsAccessor` no longer requires direct coupling to `GuiBuilder`.

```java
ComponentAccessSource source = new ComponentAccessSource() {
    @Override
    public Optional<JPanel> findPanel(String panelId) {
        return Optional.ofNullable(panels.get(panelId));
    }

    @Override
    public List<JComponent> components(String panelId) {
        return components.getOrDefault(panelId, List.of());
    }

    @Override
    public List<String> childPanels(String panelId) {
        return children.getOrDefault(panelId, List.of());
    }
};
```

Use it with:

```java
ComponentsAccessor accessor = new ComponentsAccessor(
        source,
        "settings",
        List.of(JTextField.class),
        ComponentAccessorOptions.defaults(),
        ComponentValueRegistry.defaults()
);
```

## Cycle and depth protection

Panel cycles are detected and skipped. Component instances are tracked by identity, so the same
Swing instance is never indexed twice during one refresh.

Maximum traversal depth is configurable:

```java
ComponentAccessorOptions options = ComponentAccessorOptions.builder()
        .maximumTraversalDepth(64)
        .build();
```

## Verification and JavaDoc

```powershell
.\gradlew.bat componentAccessorCheck componentAccessorJavadoc
```

Generated JavaDoc:

```text
build/docs/javadoc-component-accessor/
```

The standard `check` task depends on both accessor verification and accessor JavaDoc generation.
