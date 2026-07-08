package org.takesome.kaylasEngine.events;

import java.time.Instant;
import java.util.UUID;

public record EventDispatchResult(
        UUID eventId,
        String eventType,
        int listenerCount,
        Instant dispatchedAt
) {
}
