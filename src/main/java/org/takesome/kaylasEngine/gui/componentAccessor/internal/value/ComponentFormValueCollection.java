package org.takesome.kaylasEngine.gui.componentAccessor.internal.value;

import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueMode;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueRegistry;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;
import org.takesome.kaylasEngine.gui.componentAccessor.UnsupportedValuePolicy;

import java.util.Collection;
import java.util.Map;

/** Internal boundary for collecting semantic values from indexed form controls. */
public interface ComponentFormValueCollection {
    static ComponentFormValueCollection create(ComponentAccessSource source,
                                               ComponentAccessorOptions options,
                                               ComponentValueRegistry valueRegistry) {
        return new DefaultComponentFormValueCollector(source, options, valueRegistry);
    }

    Map<String, Object> collectAll(Collection<IndexedComponent> components,
                                   ComponentValueMode valueMode,
                                   UnsupportedValuePolicy unsupportedPolicy);

    Map<String, Object> collectPanel(String panelId,
                                     Collection<IndexedComponent> components,
                                     ComponentValueMode valueMode,
                                     UnsupportedValuePolicy unsupportedPolicy);
}
