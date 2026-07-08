package org.takesome.kaylasEngine.events;

import java.time.Instant;
import java.util.UUID;

public interface EngineEvent {
    UUID eventId();

    Instant createdAt();

    Object source();

    String type();
}
