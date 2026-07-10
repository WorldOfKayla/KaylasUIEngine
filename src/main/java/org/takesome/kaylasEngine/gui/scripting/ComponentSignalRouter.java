package org.takesome.kaylasEngine.gui.scripting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe registry of directed component signal routes. */
public final class ComponentSignalRouter {
    private final Map<String, ComponentSignalRoute> routesById = new ConcurrentHashMap<>();
    private final Map<RouteKey, List<ComponentSignalRoute>> routesBySource = new ConcurrentHashMap<>();

    public Connection connect(String sourceId,
                              String sourceEvent,
                              String targetId,
                              String targetEvent,
                              String scopeId) {
        ComponentSignalRoute route = new ComponentSignalRoute(
                UUID.randomUUID().toString(),
                normalizeOptional(scopeId),
                require(sourceId, "source component id"),
                normalizeEvent(sourceEvent),
                require(targetId, "target component id"),
                normalizeEvent(targetEvent)
        );
        routesById.put(route.id(), route);
        routesBySource.computeIfAbsent(
                new RouteKey(route.sourceId(), route.sourceEvent()),
                key -> new CopyOnWriteArrayList<>()
        ).add(route);
        return new Connection(this, route.id());
    }

    public boolean disconnect(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return false;
        }
        ComponentSignalRoute route = routesById.remove(routeId);
        if (route == null) {
            return false;
        }
        RouteKey key = new RouteKey(route.sourceId(), route.sourceEvent());
        List<ComponentSignalRoute> routes = routesBySource.get(key);
        if (routes != null) {
            routes.removeIf(candidate -> candidate.id().equals(route.id()));
            if (routes.isEmpty()) {
                routesBySource.remove(key, routes);
            }
        }
        return true;
    }

    public int disconnectScope(String scopeId) {
        String normalized = normalizeOptional(scopeId);
        if (normalized == null) {
            return 0;
        }
        List<String> routeIds = routesById.values().stream()
                .filter(route -> normalized.equals(route.scopeId()))
                .map(ComponentSignalRoute::id)
                .toList();
        routeIds.forEach(this::disconnect);
        return routeIds.size();
    }

    public List<ComponentSignalRoute> routesFor(String sourceId, String sourceEvent) {
        if (sourceId == null || sourceId.isBlank() || sourceEvent == null || sourceEvent.isBlank()) {
            return List.of();
        }
        List<ComponentSignalRoute> routes = routesBySource.get(
                new RouteKey(sourceId.trim(), normalizeEvent(sourceEvent))
        );
        return routes == null ? List.of() : List.copyOf(routes);
    }

    public Map<String, ComponentSignalRoute> routes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(routesById));
    }

    public int size() {
        return routesById.size();
    }

    private static String normalizeEvent(String value) {
        return LuaRuntimeSupport.normalizeEventName(require(value, "event name"));
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RouteKey(String componentId, String eventName) {
        private RouteKey {
            componentId = Objects.requireNonNull(componentId, "componentId");
            eventName = Objects.requireNonNull(eventName, "eventName");
        }
    }

    public static final class Connection implements AutoCloseable {
        private final ComponentSignalRouter owner;
        private final String id;
        private volatile boolean closed;

        private Connection(ComponentSignalRouter owner, String id) {
            this.owner = owner;
            this.id = id;
        }

        public String id() {
            return id;
        }

        public boolean isActive() {
            return !closed && owner.routesById.containsKey(id);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.disconnect(id);
            }
        }
    }
}
