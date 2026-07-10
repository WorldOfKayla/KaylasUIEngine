package org.takesome.kaylasEngine.game;

import org.takesome.kaylasEngine.Engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Resolves Java executables inside downloaded runtimes and caches successful lookups.
 */
public final class JavaRuntimeLocator {
    private static final int SEARCH_DEPTH = 5;
    private static final ConcurrentMap<RuntimeKey, Path> CACHE = new ConcurrentHashMap<>();

    private JavaRuntimeLocator() {
    }

    public static Path locate(Path runtimeVersionDirectory, String distributionDirectory) {
        Path runtimeRoot = Objects.requireNonNull(runtimeVersionDirectory, "runtimeVersionDirectory")
                .toAbsolutePath()
                .normalize();
        String distribution = normalizeDistribution(distributionDirectory);
        RuntimeKey key = new RuntimeKey(runtimeRoot, distribution, executableName());

        Path cached = CACHE.get(key);
        if (cached != null && Files.isRegularFile(cached)) {
            return cached;
        }

        Path resolved = resolve(key);
        if (Files.isRegularFile(resolved)) {
            CACHE.put(key, resolved);
        } else {
            CACHE.remove(key);
        }
        return resolved;
    }

    public static void invalidate(Path runtimeVersionDirectory) {
        if (runtimeVersionDirectory == null) {
            return;
        }
        Path normalized = runtimeVersionDirectory.toAbsolutePath().normalize();
        CACHE.keySet().removeIf(key -> key.runtimeRoot().equals(normalized));
    }

    private static Path resolve(RuntimeKey key) {
        Path expected = key.runtimeRoot()
                .resolve(key.distributionDirectory())
                .resolve("bin")
                .resolve(key.executableName());
        if (Files.isRegularFile(expected)) {
            return expected;
        }
        if (!Files.isDirectory(key.runtimeRoot())) {
            return expected;
        }

        try (Stream<Path> paths = Files.find(
                key.runtimeRoot(),
                SEARCH_DEPTH,
                (path, attributes) -> attributes.isRegularFile()
                        && key.executableName().equalsIgnoreCase(path.getFileName().toString())
                        && path.getParent() != null
                        && "bin".equalsIgnoreCase(path.getParent().getFileName().toString())
        )) {
            return paths.findFirst().orElse(expected);
        } catch (IOException error) {
            Engine.LOGGER.warn("Unable to inspect runtime directory {}: {}", key.runtimeRoot(), error.getMessage());
            return expected;
        }
    }

    private static String normalizeDistribution(String distributionDirectory) {
        if (distributionDirectory == null || distributionDirectory.isBlank()) {
            throw new IllegalArgumentException("Runtime distribution directory must not be blank.");
        }
        return distributionDirectory.trim();
    }

    private static String executableName() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
    }

    private record RuntimeKey(Path runtimeRoot, String distributionDirectory, String executableName) {
    }
}
