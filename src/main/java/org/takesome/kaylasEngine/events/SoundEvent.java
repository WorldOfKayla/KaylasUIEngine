package org.takesome.kaylasEngine.events;

import org.takesome.kaylasEngine.sound.PlaybackStatusListener;

import java.time.Instant;
import java.util.UUID;

public record SoundEvent(
        UUID eventId,
        Instant createdAt,
        Object source,
        String category,
        String subCategory,
        boolean loop,
        PlaybackStatusListener playbackStatusListener
) implements EngineEvent {
    public static SoundEvent of(Object source, String category, String subCategory) {
        return new SoundEvent(UUID.randomUUID(), Instant.now(), source, category, subCategory, false, null);
    }

    public static SoundEvent of(Object source, String category, String subCategory, boolean loop) {
        return new SoundEvent(UUID.randomUUID(), Instant.now(), source, category, subCategory, loop, null);
    }

    public static SoundEvent of(Object source, String category, String subCategory, boolean loop, PlaybackStatusListener listener) {
        return new SoundEvent(UUID.randomUUID(), Instant.now(), source, category, subCategory, loop, listener);
    }

    @Override
    public String type() {
        return "sound." + category + "." + subCategory;
    }
}
