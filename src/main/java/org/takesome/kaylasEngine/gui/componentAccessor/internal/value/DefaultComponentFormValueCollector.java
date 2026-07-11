package org.takesome.kaylasEngine.gui.componentAccessor.internal.value;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessException;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueMode;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueRegistry;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;
import org.takesome.kaylasEngine.gui.componentAccessor.UnsupportedValuePolicy;

import javax.swing.JComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collects semantic form values from an already-built component index.
 */
final class DefaultComponentFormValueCollector implements ComponentFormValueCollection {
    private final ComponentAccessSource source;
    private final ComponentAccessorOptions options;
    private final ComponentValueRegistry valueRegistry;

    DefaultComponentFormValueCollector(ComponentAccessSource source,
                                ComponentAccessorOptions options,
                                ComponentValueRegistry valueRegistry) {
        this.source = Objects.requireNonNull(source, "source");
        this.options = Objects.requireNonNull(options, "options");
        this.valueRegistry = Objects.requireNonNull(valueRegistry, "valueRegistry");
    }

    @Override
    public Map<String, Object> collectAll(Collection<IndexedComponent> components,
                                   ComponentValueMode valueMode,
                                   UnsupportedValuePolicy unsupportedPolicy) {
        return collectValues(
                components.stream().filter(IndexedComponent::formEligible).toList(),
                valueMode,
                unsupportedPolicy
        );
    }

    @Override
    public Map<String, Object> collectPanel(String panelId,
                                     Collection<IndexedComponent> components,
                                     ComponentValueMode valueMode,
                                     UnsupportedValuePolicy unsupportedPolicy) {
        if (panelId == null || panelId.isBlank()) {
            return Map.of();
        }
        Set<String> panelIds = new LinkedHashSet<>();
        collectPanelIds(panelId.trim(), 0, panelIds);
        return collectValues(
                components.stream()
                        .filter(IndexedComponent::formEligible)
                        .filter(metadata -> panelIds.contains(metadata.panelId()))
                        .toList(),
                valueMode,
                unsupportedPolicy
        );
    }

    private Map<String, Object> collectValues(Collection<IndexedComponent> components,
                                              ComponentValueMode valueMode,
                                              UnsupportedValuePolicy unsupportedPolicy) {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(valueMode, "valueMode");
        Objects.requireNonNull(unsupportedPolicy, "unsupportedPolicy");

        Map<String, Object> values = new LinkedHashMap<>();
        for (IndexedComponent metadata : components) {
            FormValue formValue = formValue(metadata.component(), valueMode, unsupportedPolicy);
            if (formValue.include()) {
                values.put(metadata.id(), formValue.value());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private FormValue formValue(JComponent component,
                                ComponentValueMode valueMode,
                                UnsupportedValuePolicy unsupportedPolicy) {
        if (!valueRegistry.supports(component)) {
            return switch (unsupportedPolicy) {
                case SKIP -> FormValue.skip();
                case EMPTY_STRING -> FormValue.include("");
                case FAIL -> throw new ComponentAccessException(
                        "No form value adapter for component " + component.getClass().getName()
                                + " with id '" + component.getName() + "'"
                );
            };
        }

        Object nativeValue = valueRegistry.read(component);
        if (valueMode == ComponentValueMode.STRING) {
            return FormValue.include(nativeValue == null ? "" : String.valueOf(nativeValue));
        }
        return FormValue.include(nativeValue);
    }

    private void collectPanelIds(String panelId, int depth, Set<String> result) {
        guardDepth(depth, panelId);
        if (!result.add(panelId)) {
            return;
        }
        for (String child : source.childPanels(panelId)) {
            if (child != null && !child.isBlank()) {
                collectPanelIds(child.trim(), depth + 1, result);
            }
        }
    }

    private void guardDepth(int depth, String identifier) {
        if (depth > options.maximumTraversalDepth()) {
            throw new ComponentAccessException(
                    "Maximum panel traversal depth exceeded at '" + identifier + "'"
            );
        }
    }

    private record FormValue(boolean include, Object value) {
        private static FormValue include(Object value) {
            return new FormValue(true, value);
        }

        private static FormValue skip() {
            return new FormValue(false, null);
        }
    }
}
