package org.takesome.kaylasEngine.locale;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.takesome.kaylasEngine.Engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class LanguageProvider {
    private static final String DEFAULT_LOCALE = "en_US";
    private static final List<String> WELL_KNOWN_LOCALE_FILES = List.of("en_US.json", "it_IT.json", "pl_PL.json", "ru_RU.json", "uk_UA.json");

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

        String normalizedPath = normalizePath(langFilePath);
        try {
            if (normalizedPath.endsWith("/")) {
                loadLocalizationDirectory(normalizedPath);
            } else {
                loadSingleLocalizationFile(normalizedPath);
            }
        } catch (Exception ex) {
            Engine.getLOGGER().error("Failed to load localization from {}", langFilePath, ex);
        }
    }

    private void loadSingleLocalizationFile(String resourcePath) throws Exception {
        JsonObject jsonObject = readJsonResource(resourcePath);
        if (jsonObject == null) {
            Engine.getLOGGER().warn("Localization file not found or empty: {}", resourcePath);
            return;
        }
        parseLocalization(jsonObject, localeNameFromPath(resourcePath));
    }

    private void loadLocalizationDirectory(String directoryPath) throws Exception {
        List<String> localeResourcePaths = discoverLocaleResourcePaths(directoryPath);
        if (localeResourcePaths.isEmpty()) {
            Engine.getLOGGER().warn("Localization directory is empty or not discoverable: {}", directoryPath);
            return;
        }

        Map<String, JsonObject> localeObjects = new LinkedHashMap<>();
        for (String resourcePath : localeResourcePaths) {
            JsonObject root = readJsonResource(resourcePath);
            if (root == null) {
                continue;
            }
            String localeName = localeNameFromPath(resourcePath);
            if (!locales.contains(localeName)) {
                locales.add(localeName);
            }
            localeObjects.put(localeName, root);
        }

        if (localeObjects.isEmpty()) {
            Engine.getLOGGER().warn("No valid localization JSON files found in {}", directoryPath);
            return;
        }

        String selectedLocale = getLocaleKeyByIndex(localeIndex);
        JsonObject selectedLocalization = Optional.ofNullable(localeObjects.get(selectedLocale))
                .or(() -> Optional.ofNullable(localeObjects.get(DEFAULT_LOCALE)))
                .orElseGet(() -> localeObjects.values().iterator().next());

        parseFlatLocaleLocalization(selectedLocalization);
        Engine.getLOGGER().info("Loaded localization locale '{}' from {}", selectedLocale, directoryPath);
    }

    private List<String> discoverLocaleResourcePaths(String directoryPath) {
        List<String> discovered = new ArrayList<>();
        ClassLoader classLoader = getResourceClassLoader();
        try {
            URL directoryUrl = classLoader.getResource(directoryPath);
            if (directoryUrl != null && "file".equalsIgnoreCase(directoryUrl.getProtocol())) {
                URI uri = directoryUrl.toURI();
                try (Stream<Path> files = Files.list(Path.of(uri))) {
                    files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                            .map(path -> directoryPath + path.getFileName())
                            .sorted()
                            .forEach(discovered::add);
                }
            } else if (directoryUrl != null && "jar".equalsIgnoreCase(directoryUrl.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) directoryUrl.openConnection();
                try (JarFile jarFile = connection.getJarFile()) {
                    jarFile.stream()
                            .map(JarEntry::getName)
                            .filter(name -> name.startsWith(directoryPath))
                            .filter(name -> name.endsWith(".json"))
                            .filter(name -> name.indexOf('/', directoryPath.length()) < 0)
                            .sorted()
                            .forEach(discovered::add);
                }
            }
        } catch (Exception ex) {
            Engine.getLOGGER().warn("Could not discover localization directory {}; trying known locale files", directoryPath, ex);
        }

        if (discovered.isEmpty()) {
            for (String localeFile : WELL_KNOWN_LOCALE_FILES) {
                String resourcePath = directoryPath + localeFile;
                if (resourceExists(resourcePath)) {
                    discovered.add(resourcePath);
                }
            }
        }
        return discovered;
    }

    private JsonObject readJsonResource(String resourcePath) throws Exception {
        try (InputStream stream = getResourceClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                JsonElement root = JsonParser.parseReader(bufferedReader);
                if (root == null || root.isJsonNull()) {
                    return null;
                }
                if (!root.isJsonObject()) {
                    Engine.getLOGGER().warn("Localization resource must be a JSON object and was ignored: {}", resourcePath);
                    return null;
                }
                return root.getAsJsonObject();
            }
        }
    }

    private boolean resourceExists(String resourcePath) {
        try (InputStream stream = getResourceClassLoader().getResourceAsStream(resourcePath)) {
            return stream != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private ClassLoader getResourceClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = engine.getClass().getClassLoader();
        }
        return classLoader;
    }

    private void parseLocalization(JsonObject jsonObject, String resourceLocale) {
        if (isFlatLocaleFile(jsonObject)) {
            if (resourceLocale != null && !resourceLocale.isBlank() && !locales.contains(resourceLocale)) {
                locales.add(resourceLocale);
            }
            parseFlatLocaleLocalization(jsonObject);
            return;
        }
        parseMultiLocaleLocalization(jsonObject);
    }

    private boolean isFlatLocaleFile(JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> categoryEntry : jsonObject.entrySet()) {
            if (!categoryEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject categoryData = categoryEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> localizedData : categoryData.entrySet()) {
                return localizedData.getValue().isJsonPrimitive();
            }
        }
        return false;
    }

    private void parseFlatLocaleLocalization(JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> categoryEntry : jsonObject.entrySet()) {
            String section = categoryEntry.getKey();
            if (!categoryEntry.getValue().isJsonObject()) {
                continue;
            }
            sectionsSet.add(section);
            JsonObject categoryData = categoryEntry.getValue().getAsJsonObject();
            Map<String, String> categoryMap = localizationData.computeIfAbsent(section, key -> new LinkedHashMap<>());
            for (Map.Entry<String, JsonElement> localizedData : categoryData.entrySet()) {
                JsonElement value = localizedData.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    categoryMap.put(localizedData.getKey(), value.getAsString());
                }
            }
        }
    }

    private void parseMultiLocaleLocalization(JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> categoryEntry : jsonObject.entrySet()) {
            String section = categoryEntry.getKey();
            if (!categoryEntry.getValue().isJsonObject()) {
                continue;
            }
            sectionsSet.add(section);

            JsonObject categoryData = categoryEntry.getValue().getAsJsonObject();
            Map<String, String> categoryMap = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> localizedData : categoryData.entrySet()) {
                String localizedKey = localizedData.getKey();
                if (!localizedData.getValue().isJsonObject()) {
                    categoryMap.put(localizedKey, localizedData.getValue().getAsString());
                    continue;
                }
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
        if (localizedValues.has("en")) {
            return localizedValues.get("en").getAsString();
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

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private String localeNameFromPath(String resourcePath) {
        String normalized = normalizePath(resourcePath);
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (fileName.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - ".json".length());
        }
        return fileName;
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
