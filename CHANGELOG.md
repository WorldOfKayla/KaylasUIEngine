# Changelog

## 2.1.0-AURELIA — 2026-07-10

### Added

- Common `AbstractComponentDefinition` hierarchy for engine component definitions.
- `ComponentKind.BASIC` and `ComponentKind.COMPOSITE` classification.
- Central thread-safe `ComponentCatalog` for built-in and launcher-defined component types.
- Launcher-facing `ComponentConstructor` API.
- Declarative reusable `CompositeComponentDefinition` graphs.
- Immutable `ComponentNode` prototypes with deep `ComponentAttributes` copies.
- Scoped composite instance ids in the form `instance.child`.
- Declarative `ComponentConnection` routes between composite nodes.
- Directed `ComponentSignalRouter` integrated into the existing Lua event pipeline.
- Targeted Lua listeners through `component:on(...)` and `ui.on(componentId, event, handler)`.
- Lua APIs: `ui.connect`, `ui.disconnect`, `ui.send`, `component:connectLocal`, `component:sendLocal`, and `component:findLocal`.
- Routed event metadata containing route, scope, source, target, and payload information.
- Automatic route, targeted-listener, component-registry, and Lua-closure cleanup by composite scope.
- Constructor runtime architecture and launcher integration documentation.

### Improved

- Built-in preassembled components are cataloged as `COMPOSITE`; atomic controls remain `BASIC`.
- `ComponentFactory` now resolves all definitions through a single catalog source of truth.
- `GuiBuilder` exposes `getComponentCatalog()` and `getComponentConstructor()`.
- Component descriptors support deep copying and per-event script registration.
- Definition replacement removes obsolete aliases.
- Catalog aliases cannot hijack canonical component type names.
- Composite registration validates unknown child types, duplicate nodes, missing connection endpoints, direct self-reference, and indirect dependency cycles.
- Runtime creation detects recursive composite construction through the active creation stack.
- Existing global Lua `ui.on` and `ui.emit` behavior remains compatible.

### Verification

- Added catalog, prototype isolation, definition inheritance, alias collision, cycle detection, and signal-router checks.
- Added an end-to-end launcher fixture that registers a custom composite, instantiates it from XML, routes a checkbox event, and updates a linked label through a scoped Lua listener.
- Existing slider, directory-selector, transparent-panel, and checkbox-panel regressions remain covered.

## 2.0.0-AURELIA — 2026-07-10

### Breaking

- Replaced shared mutable `ComponentFactory` creation state with scoped `ComponentCreationContext`.
- `createComponentAsync` now creates Swing objects on the EDT and completes exceptionally on failure.
- Component type lookup is case-insensitive and aliases replace duplicate registrations that differ only by case.
- Descriptor booleans now preserve unspecified versus explicit `false` semantics.

### Added

- `ComponentDefinition` registry with aliases, default styles and ordered configurators.
- Definition inheritance through `ComponentDefinition.derive(...)`.
- Style inheritance through `extends`, `inherits` and `parent`.
- Ordered style composition and cached inline style overrides.
- XML `styleClasses`, `styleOverrides`, `properties` and `scripts` sections.
- Programmatic `ComponentAttributes.Builder`.
- Accessibility metadata, cursor configuration and custom Swing client properties.
- Style and descriptor runtime metadata on created components.
- Regression verification for composite sliders, directory selectors and transparent checkbox panels.

### Improved

- Null-safe base attributes and child collections.
- Secure XML parser configuration with external entity access disabled.
- Button styling, texture handling and alignment normalization.
- Scoped child-style overrides for `CompositeSlider` and `FileSelector` internals.
- Composite slider tick labels now use intrinsic dimensions, preventing negative track geometry and clipped thumbs.
- Directory selection aliases including `folder`, `directory` and `directories-only`.
- Transparent panel defaults; empty panel backgrounds no longer become white.
- Panel replacement, z-order application and de-duplicated parent-child registration.
- Built-in component aliases and diagnostic errors for unsupported types.
- Portable ASCII startup banner for Windows terminals.

### Documentation

- Added the Components Runtime 2.0 architecture and migration guide.
