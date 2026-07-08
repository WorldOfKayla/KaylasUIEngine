package org.takesome.kaylasEngine.events;

import java.util.UUID;

public record EventContext(
        EventBus eventBus,
        UUID deliveryId,
        String listenerId
) {
}
