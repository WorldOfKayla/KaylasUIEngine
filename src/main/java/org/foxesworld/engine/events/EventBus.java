package org.foxesworld.engine.events;

import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class EventBus {
    private static final int DEFAULT_HISTORY_LIMIT = 2048;

    private final Map<Class<? extends EngineEvent>, CopyOnWriteArrayList<EngineEventListener<? extends EngineEvent>>> listeners = new ConcurrentHashMap<>();
    private final Deque<EventDeliveryRecord> deliveryHistory = new ArrayDeque<>();
    private final Deque<EventDeliveryRecord> deadLetters = new ArrayDeque<>();
    private final Executor executor;
    private final Logger logger;
    private final int historyLimit;

    public EventBus(Executor executor, Logger logger) {
        this(executor, logger, DEFAULT_HISTORY_LIMIT);
    }

    public EventBus(Executor executor, Logger logger, int historyLimit) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.historyLimit = Math.max(64, historyLimit);
    }

    public <T extends EngineEvent> void subscribe(Class<T> eventType, EngineEventListener<? super T> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
                .add((EngineEventListener<? extends EngineEvent>) listener);
        logger.debug("Event listener registered: eventType={}, listener={}", eventType.getName(), listener.listenerId());
    }

    public <T extends EngineEvent> boolean unsubscribe(Class<T> eventType, EngineEventListener<? super T> listener) {
        List<EngineEventListener<? extends EngineEvent>> eventListeners = listeners.get(eventType);
        return eventListeners != null && eventListeners.remove(listener);
    }

    public EventDispatchResult publish(EngineEvent event) {
        Objects.requireNonNull(event, "event");
        List<EngineEventListener<? extends EngineEvent>> resolvedListeners = resolveListeners(event);
        if (resolvedListeners.isEmpty()) {
            record(new EventDeliveryRecord(
                    event.eventId(),
                    UUID.randomUUID(),
                    event.type(),
                    "<none>",
                    EventDeliveryStatus.NO_SUBSCRIBERS,
                    Instant.now(),
                    Instant.now(),
                    null
            ));
            logger.debug("No listeners for event {} ({})", event.eventId(), event.type());
            return new EventDispatchResult(event.eventId(), event.type(), 0, Instant.now());
        }

        for (EngineEventListener<? extends EngineEvent> listener : resolvedListeners) {
            if (listener.async()) {
                queueAsync(event, listener);
            } else {
                deliver(event, listener);
            }
        }
        return new EventDispatchResult(event.eventId(), event.type(), resolvedListeners.size(), Instant.now());
    }

    private List<EngineEventListener<? extends EngineEvent>> resolveListeners(EngineEvent event) {
        List<EngineEventListener<? extends EngineEvent>> resolved = new ArrayList<>();
        Class<?> eventClass = event.getClass();
        listeners.forEach((registeredType, registeredListeners) -> {
            if (registeredType.isAssignableFrom(eventClass)) {
                resolved.addAll(registeredListeners);
            }
        });
        return resolved;
    }

    private void queueAsync(EngineEvent event, EngineEventListener<? extends EngineEvent> listener) {
        UUID deliveryId = UUID.randomUUID();
        Instant now = Instant.now();
        record(new EventDeliveryRecord(
                event.eventId(),
                deliveryId,
                event.type(),
                listener.listenerId(),
                EventDeliveryStatus.QUEUED,
                now,
                now,
                null
        ));
        executor.execute(() -> deliver(event, listener, deliveryId));
    }

    private void deliver(EngineEvent event, EngineEventListener<? extends EngineEvent> listener) {
        deliver(event, listener, UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private void deliver(EngineEvent event, EngineEventListener<? extends EngineEvent> listener, UUID deliveryId) {
        Instant started = Instant.now();
        try {
            ((EngineEventListener<EngineEvent>) listener).onEvent(event, new EventContext(this, deliveryId, listener.listenerId()));
            record(new EventDeliveryRecord(
                    event.eventId(),
                    deliveryId,
                    event.type(),
                    listener.listenerId(),
                    EventDeliveryStatus.DELIVERED,
                    started,
                    Instant.now(),
                    null
            ));
        } catch (Exception ex) {
            EventDeliveryRecord failed = new EventDeliveryRecord(
                    event.eventId(),
                    deliveryId,
                    event.type(),
                    listener.listenerId(),
                    EventDeliveryStatus.FAILED,
                    started,
                    Instant.now(),
                    ex.getMessage()
            );
            record(failed);
            deadLetter(failed);
            logger.error("Event delivery failed: eventId={}, type={}, listener={}", event.eventId(), event.type(), listener.listenerId(), ex);
        }
    }

    private void record(EventDeliveryRecord record) {
        synchronized (deliveryHistory) {
            deliveryHistory.addLast(record);
            while (deliveryHistory.size() > historyLimit) {
                deliveryHistory.removeFirst();
            }
        }
    }

    private void deadLetter(EventDeliveryRecord record) {
        synchronized (deadLetters) {
            deadLetters.addLast(record);
            while (deadLetters.size() > historyLimit) {
                deadLetters.removeFirst();
            }
        }
    }

    public List<EventDeliveryRecord> getDeliveryHistory() {
        synchronized (deliveryHistory) {
            return List.copyOf(deliveryHistory);
        }
    }

    public List<EventDeliveryRecord> getDeadLetters() {
        synchronized (deadLetters) {
            return List.copyOf(deadLetters);
        }
    }

    public int listenerCount(Class<? extends EngineEvent> eventType) {
        List<EngineEventListener<? extends EngineEvent>> eventListeners = listeners.get(eventType);
        return eventListeners == null ? 0 : eventListeners.size();
    }

    public Map<Class<? extends EngineEvent>, List<EngineEventListener<? extends EngineEvent>>> snapshotListeners() {
        Map<Class<? extends EngineEvent>, List<EngineEventListener<? extends EngineEvent>>> snapshot = new ConcurrentHashMap<>();
        listeners.forEach((key, value) -> snapshot.put(key, Collections.unmodifiableList(value)));
        return Collections.unmodifiableMap(snapshot);
    }
}
