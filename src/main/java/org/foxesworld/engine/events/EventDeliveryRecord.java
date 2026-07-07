package org.foxesworld.engine.events;

import java.time.Instant;
import java.util.UUID;

public record EventDeliveryRecord(
        UUID eventId,
        UUID deliveryId,
        String eventType,
        String listenerId,
        EventDeliveryStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
