package org.foxesworld.engine.events;

import java.util.UUID;

public record EventContext(
        EventBus eventBus,
        UUID deliveryId,
        String listenerId
) {
}
