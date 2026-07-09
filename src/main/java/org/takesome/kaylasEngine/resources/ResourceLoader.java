package org.takesome.kaylasEngine.resources;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared classpath/file resource loader used by engine modules and applications built on the engine.
 *
 * <p>The loader first checks a real filesystem path, then falls back to the classpath. This keeps
 * development overrides cheap while preserving normal packaged-jar resource loading.</p>
 */
public final class ResourceLoader {
    private static final Gson DEFAULT_GSON = new Gson();

    private ResourceLoader() {
    }

    public static InputStream open(String location) throws IOException {
        return open(location, defaultClassLoader());
    }

    public static InputStream open(String location, ClassLoader classLoader) throws IOException {
        String normalized = normalize(location);
        Path filePath = Path.of(location);
        if (Files.isRegularFile(filePath)) {
            return Files.newInputStream(filePath);
        }

        ClassLoader loader = classLoader == null ? defaultClassLoader() : classLoader;
        InputStream input = loader.getResourceAsStream(normalized);
        if (input == null) {
            throw new FileNotFoundException("Resource was not found as file or classpath entry: " + location);
        }
        return input;
    }

    public static String readText(String location) throws IOException {
        return readText(location, defaultClassLoader());
    }

    public static String readText(String location, ClassLoader classLoader) throws IOException {
        try (InputStream input = open(location, classLoader)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static <T> T loadJson(String location, Class<T> type) throws IOException {
        return loadJson(location, type, DEFAULT_GSON, defaultClassLoader());
    }

    public static <T> T loadJson(String location, Class<T> type, Gson gson, ClassLoader classLoader) throws IOException {
        Objects.requireNonNull(type, "type");
        Gson json = gson == null ? DEFAULT_GSON : gson;
        try (InputStream input = open(location, classLoader);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return json.fromJson(reader, type);
        }
    }

    public static String normalize(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Resource location cannot be blank.");
        }
        String normalized = location.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? ResourceLoader.class.getClassLoader() : loader;
    }
}
