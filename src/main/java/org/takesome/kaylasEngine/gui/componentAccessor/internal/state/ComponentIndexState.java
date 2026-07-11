package org.takesome.kaylasEngine.gui.componentAccessor.internal.state;

import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSnapshot;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.index.ComponentGraphIndexing;

import javax.swing.JComponent;
import java.util.Map;
import java.util.Optional;

/** Internal thread-safe state boundary for the currently committed component index. */
public interface ComponentIndexState {
    static ComponentIndexState create(String rootPanelId) {
        return new LockedComponentIndexState(rootPanelId);
    }

    void replace(ComponentGraphIndexing.Index index);

    long revision();

    ComponentAccessSnapshot snapshot();

    Optional<JComponent> findComponent(String id);

    Optional<IndexedComponent> findMetadata(String id);

    Map<String, JComponent> components();

    Map<String, IndexedComponent> metadata();
}
