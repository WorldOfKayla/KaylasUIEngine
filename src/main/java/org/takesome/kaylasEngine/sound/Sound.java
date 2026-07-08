package org.takesome.kaylasEngine.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.events.EngineEventListener;
import org.takesome.kaylasEngine.events.EventContext;
import org.takesome.kaylasEngine.events.SoundEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Sound {
    private final SoundPlayer soundPlayer;
    private final Engine engine;
    private final Map<String, Map<String, List<String>>> soundsMap = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public Sound(Engine engine, InputStream inputStream) {
        this.engine = engine;
        Engine.getLOGGER().debug("FoxesSound init");
        this.soundPlayer = new SoundPlayer(engine);
        loadSounds(inputStream);
        subscribeToSoundEvents();
    }

    private void subscribeToSoundEvents() {
        engine.getEventBus().subscribe(SoundEvent.class, new EngineEventListener<SoundEvent>() {
            @Override
            public void onEvent(SoundEvent event, EventContext context) {
                playSelectedSound(event.category(), event.subCategory(), event.loop(), event.playbackStatusListener());
            }

            @Override
            public String listenerId() {
                return "engine.sound.dispatcher";
            }
        });
    }

    private void loadSounds(InputStream inputStream) {
        if (inputStream == null) {
            Engine.getLOGGER().warn("Bundled sound map is missing; component sounds are disabled.");
            loaded = false;
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            if (jsonObject == null) {
                Engine.getLOGGER().warn("Bundled sound map is empty; component sounds are disabled.");
                loaded = false;
                return;
            }

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                String category = entry.getKey();
                JsonObject categoryObj = entry.getValue().getAsJsonObject();
                Map<String, List<String>> subCategorySoundsMap = new ConcurrentHashMap<>();

                for (Map.Entry<String, JsonElement> subCategoryEntry : categoryObj.entrySet()) {
                    if (!subCategoryEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    String subCategory = subCategoryEntry.getKey();
                    JsonArray soundsArray = subCategoryEntry.getValue().getAsJsonObject().getAsJsonArray("sounds");
                    List<String> subCategorySounds = new ArrayList<>();
                    if (soundsArray != null) {
                        for (JsonElement soundElement : soundsArray) {
                            String soundPath = soundElement.getAsString();
                            if (hasBundledSound(soundPath)) {
                                subCategorySounds.add(soundPath);
                            } else {
                                Engine.getLOGGER().warn("Bundled sound resource is missing and will be skipped: {}", soundPath);
                            }
                        }
                    }
                    if (!subCategorySounds.isEmpty()) {
                        subCategorySoundsMap.put(subCategory, List.copyOf(subCategorySounds));
                    }
                }

                if (!subCategorySoundsMap.isEmpty()) {
                    soundsMap.put(category, Map.copyOf(subCategorySoundsMap));
                }
            }
            loaded = true;
            Engine.getLOGGER().info("Sounds loaded successfully: categories={}", soundsMap.keySet());
        } catch (Exception e) {
            loaded = false;
            soundsMap.clear();
            Engine.getLOGGER().error("Failed to load bundled sounds", e);
        }
    }

    private boolean hasBundledSound(String soundPath) {
        if (soundPath == null || soundPath.isBlank()) {
            return false;
        }
        return engine.getClass().getClassLoader().getResource(soundPath) != null;
    }

    public List<String> getSounds(String category, String subCategory) {
        Map<String, List<String>> subCategorySoundsMap = soundsMap.get(category);
        if (subCategorySoundsMap != null) {
            return subCategorySoundsMap.getOrDefault(subCategory, Collections.emptyList());
        }
        return Collections.emptyList();
    }

    public boolean hasSound(String category, String subCategory) {
        return !getSounds(category, subCategory).isEmpty();
    }

    public String playSound(String category, String subCategory) {
        return playSelectedSound(category, subCategory, false, null);
    }

    public String playSound(String category, String subCategory, PlaybackStatusListener playbackStatusListener) {
        return playSelectedSound(category, subCategory, false, playbackStatusListener);
    }

    public void playSound(String category, String subCategory, boolean loop) {
        playSelectedSound(category, subCategory, loop, null);
    }

    private String playSelectedSound(String category, String subCategory, boolean loop, PlaybackStatusListener listener) {
        List<String> subCategorySounds = getSounds(category, subCategory);
        if (subCategorySounds.isEmpty()) {
            Engine.getLOGGER().debug("No bundled sound registered for {}.{}", category, subCategory);
            return "";
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(subCategorySounds.size());
        String randomSound = subCategorySounds.get(randomIndex);
        if (listener != null) {
            this.soundPlayer.playSound(randomSound, loop, listener);
        } else {
            this.soundPlayer.playSound(randomSound, loop);
        }
        return randomSound;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Set<String> getCategories() {
        return Collections.unmodifiableSet(soundsMap.keySet());
    }

    public SoundPlayer getSoundPlayer() {
        return soundPlayer;
    }
}
