package org.takesome.kaylasEngine.events;

@FunctionalInterface
public interface EngineEventListener<T extends EngineEvent> {
    void onEvent(T event, EventContext context) throws Exception;

    default String listenerId() {
        return getClass().getName();
    }

    default boolean async() {
        return false;
    }
}
