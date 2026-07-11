package org.takesome.kaylasEngine.gui.componentAccessor.internal.state;

import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSnapshot;
import org.takesome.kaylasEngine.gui.componentAccessor.IndexedComponent;
import org.takesome.kaylasEngine.gui.componentAccessor.internal.index.ComponentGraphIndexing;

import javax.swing.JComponent;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Package-private lock-based index state with atomic replacement semantics. */
final class LockedComponentIndexState implements ComponentIndexState {
    private final String rootPanelId;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, JComponent> components = new LinkedHashMap<>();
    private final Map<String, List<JComponent>> panelComponents = new LinkedHashMap<>();
    private final Map<String, IndexedComponent> metadata = new LinkedHashMap<>();
    private long revision;

    LockedComponentIndexState(String rootPanelId) {
        if (rootPanelId == null || rootPanelId.isBlank()) {
            throw new IllegalArgumentException("rootPanelId must not be blank");
        }
        this.rootPanelId = rootPanelId.trim();
    }

    @Override
    public void replace(ComponentGraphIndexing.Index index) {
        Objects.requireNonNull(index, "index");
        lock.writeLock().lock();
        try {
            components.clear();
            components.putAll(index.components());
            panelComponents.clear();
            panelComponents.putAll(index.panelComponents());
            metadata.clear();
            metadata.putAll(index.metadata());
            revision++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long revision() {
        lock.readLock().lock();
        try {
            return revision;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ComponentAccessSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new ComponentAccessSnapshot(
                    revision,
                    Instant.now(),
                    rootPanelId,
                    components,
                    panelComponents,
                    metadata
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<JComponent> findComponent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            return Optional.ofNullable(components.get(id.trim()));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IndexedComponent> findMetadata(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            return Optional.ofNullable(metadata.get(id.trim()));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, JComponent> components() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(components));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, IndexedComponent> metadata() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        } finally {
            lock.readLock().unlock();
        }
    }
}
