# Changelog

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
