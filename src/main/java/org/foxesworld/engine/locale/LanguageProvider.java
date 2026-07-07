package org.foxesworld.engine.locale;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.foxesworld.engine.Engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public class LanguageProvider {
    private static final String DEFAULT_LOCALE = "en";

    private final Map<String, Map<String, String>> localizationData = new LinkedHashMap<>();
    private final Engine engine;
    private final String langFilePath;
    private final Set<String> sectionsSet = new LinkedHashSet<>();
    private final List<String> locales = new ArrayList<>();
    private int localeIndex = 0;

    public LanguageProvider(Engine engine, String langFilePath, int localeIndex) {
        this.localeIndex = Math.max(0, localeIndex);
        this.engine = engine;
        this.langFilePath = langFilePath;
        loadLocalizationData(engine, langFilePath);
    }

    private void loadLocalizationData(Engine engine, String langFilePath) {
        if (langFilePath == null || langFilePath.isBlank()) {
            Engine.getLOGGER().warn("Localization file path is empty; localization fallback mode is active.");
            return;
        }

        try (InputStream stream = engine.getClass().getClassLoader().getResourceAsStream(langFilePath)) {
            if (stream == null) {
                Engine.getLOGGER().warn("Localization file not found: {}", langFilePath);
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                JsonObject jsonObject = new Gson().fromJson(bufferedReader, JsonObject.class);
                if (jsonObject == null) {
                    Engine.getLOGGER().warn("Localization file is empty: {}", langFilePath);
                    return;
                }
                parseLocalization(jsonObject);
            }
        } catch (Exception ex) {
            Engine.getLOGGER().error("Failed to load localization from {}", langFilePath, ex);
        }
    }

    private void parseLocalization(JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> categoryEntry : jsonObject.entrySet()) {
            String section = categoryEntry.getKey();
            sectionsSet.add(section);

            JsonObject categoryData = categoryEntry.getValue().getAsJsonObject();
            Map<String, String> categoryMap = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> localizedData : categoryData.entrySet()) {
                String localizedKey = localizedData.getKey();
                JsonObject localizedValues = localizedData.getValue().getAsJsonObject();
                registerLocales(localizedValues);
                categoryMap.put(localizedKey, resolveLocalizedValue(localizedValues));
            }
            localizationData.put(section, categoryMap);
        }
    }

    private void registerLocales(JsonObject localizedValues) {
        for (Map.Entry<String, JsonElement> langSet : localizedValues.entrySet()) {
            if (!locales.contains(langSet.getKey())) {
                locales.add(langSet.getKey());
            }
        }
    }

    private String resolveLocalizedValue(JsonObject localizedValues) {
        String localeKey = getLocaleKeyByIndex(localeIndex);
        if (localizedValues.has(localeKey)) {
            return localizedValues.get(localeKey).getAsString();
        }
        if (localizedValues.has(DEFAULT_LOCALE)) {
            return localizedValues.get(DEFAULT_LOCALE).getAsString();
        }
        return localizedValues.entrySet().stream()
                .findFirst()
                .map(entry -> entry.getValue().getAsString())
                .orElse("");
    }

    private String getLocaleKeyByIndex(int index) {
        if (locales.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        int safeIndex = Math.max(0, Math.min(index, locales.size() - 1));
        return locales.get(safeIndex);
    }

    public void setLocaleIndex(int index) {
        if (index >= 0 && index < locales.size()) {
            this.localeIndex = index;
            reloadLocalizationData();
            return;
        }
        throw new IndexOutOfBoundsException("Invalid locale index: " + index);
    }

    private void reloadLocalizationData() {
        localizationData.clear();
        sectionsSet.clear();
        locales.clear();
        loadLocalizationData(this.engine, this.langFilePath);
    }

    public String getString(String key) {
        if (key != null && key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            String category = parts[0];
            String localizedKey = parts[1];
            Map<String, String> categoryMap = localizationData.get(category);
            if (categoryMap != null) {
                return categoryMap.getOrDefault(localizedKey, key);
            }
        }
        return key;
    }

    public String getStringWithKey(String langKey, String[] replaceKeys, String[] replaceValues) {
        String langLine = getString(langKey);
        if (replaceKeys != null && replaceValues != null && replaceKeys.length == replaceValues.length) {
            for (int i = 0; i < replaceKeys.length; i++) {
                langLine = langLine.replace("{" + replaceKeys[i] + "}", replaceValues[i]);
            }
        }
        return langLine;
    }

    public String[] getSectionsSet() {
        return sectionsSet.toArray(new String[0]);
    }

    public String[] getLocalesSet() {
        return locales.toArray(new String[0]);
    }

    public List<String> getLocales() {
        return Collections.unmodifiableList(locales);
    }

    public int getLocaleIndex() {
        return localeIndex;
    }
}
