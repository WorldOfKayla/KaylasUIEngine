# Changelog

## 2.3.0-KINETICA - 2026-07-11

### Modular animation runtime

- Reorganized `gui.animation` into stable public facades and responsibility-focused internal packages.
- Isolated easing and cubic-Bezier evaluation from the public `AnimationCurve` value object.
- Moved the adaptive shared Swing pulse into a hidden runtime implementation.
- Separated timer/resource ownership, timeline sampling, and timeline execution.
- Moved overlay painting and fade lifecycle behind `LayeredPaneOverlay`.
- Moved scripted window transition state and diagnostics behind `ScriptedWindowAnimator`.
- Moved progress-loop resources and execution behind `ProgressBarAnimator` while preserving its subclass hook and options API.
- Moved snapshot drawer capture, geometry, placement state, and transition execution behind `SnapshotDrawerAnimator`.

### Kaylas Look and Feel

- Added `KaylasLookAndFeel`, an engine-owned FlatLaf derivative with runtime theme installation.
- Added the immutable `KaylasTheme` palette and metric contract and the built-in `KINETICA Dark` theme.
- Added consistent defaults for standard Swing buttons, text components, combo boxes, spinners, sliders, scrollbars, progress bars, tabs, menus and tooltips.
- Added enhanced `KaylasButton`, `KaylasCheckbox`, `KaylasTextField`, `KaylasTextArea`, `KaylasPassField`, `KaylasSlider`, `KaylasSpinner`, `KaylasLabel`, `KaylasMultiButton` and `KaylasCombobox` components.
- Switched canonical XML component types to the enhanced subclasses while preserving all base-class APIs.
- Added focus-ring painting, keyboard focus, combobox keyboard selection, disabled text consistency and semantic client metadata.
- Updated `CompositeSlider` and `FileSelector` to use the same enhanced component set internally.
- Added Look and Feel runtime verification and public API JavaDoc validation.

### Encapsulation and verification

- Reorganized `componentAccessor` into binding, indexing, source, state, support, and value responsibility zones.
- Split `ComponentFactory` construction, common attribute application, tooltip loading, composite assembly, and scoped state.
- Split loading UI defaults, parsing, coercion, geometry resolution, and progress adaptation.
- Added animation architecture, curve, options, resource ownership, and internal visibility verification.
- Added public animation API JavaDoc validation.
- Kept internal concrete implementations package-private and final.

### Compatibility

- Preserved the public animation, component-accessor, component-factory, and loading-UI contracts used by KaylasLauncher.
- Renamed the current engine generation from AURELIA to KINETICA.

## 2.2.0-AURELIA - 2026-07-11

### Architectural revolution

- Component configuration is now resolved through an engine-wide group and extension pipeline before any Swing object is created.
- `tabs` is the first consumer of the mechanism, not a settings-specific special case.
- The same resolver can extend atomic components, composites, forms, menus, toolbars, accordions and complete panel graphs.

### Added

- `ComponentConfigGroupRegistry` for global, type, group/type, instance and group/instance fragments.
- Ordered runtime group activation and deactivation.
- `ComponentConfigResolver` integrated into `ComponentFactory.createComponent(...)`.
- `DeepConfigMerger` with nested map composition and `replace`, `append`, `prepend`, and `unique_append` collection policies.
- XML `groups="..."` support and programmatic `ComponentAttributes.Builder.groups(...)`.
- Generic component child extension through `appendChildren(...)`.
- Built-in `tabs` composite with arbitrary engine components as pages.
- Tab placement, selection, visibility, enablement, cyclic navigation and transition events.
- Lua tab API: `select`, `next`, `previous`, `getSelectedTab`, `getSelectedIndex`, `getTabIds`, `setTabEnabled`, and `setTabVisible`.
- Lua `tabChanging` and `tabChanged` payloads with previous/current ids, index and source.

### Verification

- Added deep merge, collection strategy, XML group order, runtime group lifecycle, instance extension, child append and tabs state-transition checks.
- Component runtime verification now identifies itself as 2.2.

## 2.1.0-AURELIA - 2026-07-10

### Breaking

- XML is now the only supported declarative UI descriptor format.
- Removed the JSON, JSON5 and experimental YAML frame loaders and the runtime adapter registry.
- Removed `EngineData.loadAdapters`, `GuiBuilder.registerFrameAttributesLoader(...)`, and `GuiBuilder.getFrameLoaderAdapters()`.
- Replaced `XmlFrameAttributesLoader` with the canonical `XmlUiDescriptorLoader` API.


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
- Refreshable `ComponentsAccessor` index with global nested lookup and selected panel views.
- `ComponentAccessSource` abstraction for `GuiBuilder`, tests, and alternate UI registries.
- Extensible `ComponentValueRegistry` with typed read/write adapters.
- Scoped constructor lookup through `findLocal(...)` and `requireLocal(...)`.
- Immutable `ComponentAccessSnapshot` and detailed `IndexedComponent` metadata.
- Configurable duplicate, unsupported-value, traversal, refresh, and value-representation policies.

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
- `@Component` binding now supports inherited fields, optional bindings, and composite scope/local ids.
- Component traversal now detects panel cycles, guards maximum depth, and indexes named descendants by identity.
- Form values are read live instead of retaining constructor-time snapshots.
- Legacy string form semantics remain the default while native values are available explicitly.

### Verification

- Added catalog, prototype isolation, definition inheritance, alias collision, cycle detection, and signal-router checks.
- Added an end-to-end launcher fixture that registers a custom composite, instantiates it from XML, routes a checkbox event, and updates a linked label through a scoped Lua listener.
- Existing slider, directory-selector, transparent-panel, and checkbox-panel regressions remain covered.
- Added `componentAccessorCheck` for traversal, scoped lookup, inherited/optional binding, adapters, refresh, form modes, and duplicate policies.
- Added `componentAccessorJavadoc` to generate and validate the complete accessor API documentation.

## 2.0.0-AURELIA - 2026-07-10

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
