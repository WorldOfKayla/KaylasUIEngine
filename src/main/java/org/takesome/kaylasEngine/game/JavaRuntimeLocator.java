package org.takesome.kaylasEngine.game;

import org.takesome.kaylasEngine.Engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Resolves the newest flat runtime installation matching the required Java major and platform.
     * Expected directory format: jdk-<full-version>-<platform>, for example
     * jdk-25.0.2-windows-x86_64.
     */
    public static Path locateFlatRuntime(Path runtimeDirectory, String requiredMajor, String platformId) {
        Path runtimeRoot = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")
                .toAbsolutePath()
                .normalize();
        String major = normalizeMajor(requiredMajor);
        String platform = normalizePlatform(platformId);
        String executable = executableName();

        if (Files.isDirectory(runtimeRoot)) {
            try (Stream<Path> entries = Files.list(runtimeRoot)) {
                Path selected = entries
                        .filter(Files::isDirectory)
                        .filter(path -> matchesFlatRuntime(path, major, platform))
                        .filter(path -> Files.isRegularFile(path.resolve("bin").resolve(executable)))
                        .max(Comparator.comparing(
                                path -> runtimeVersion(path, platform),
                                JavaRuntimeLocator::compareVersions
                        ))
                        .orElse(null);
                if (selected != null) {
                    return selected.resolve("bin").resolve(executable);
                }
            } catch (IOException error) {
                Engine.LOGGER.warn("Unable to inspect flat runtime directory {}: {}", runtimeRoot, error.getMessage());
            }
        }

        return runtimeRoot
                .resolve("jdk-" + major + '-' + platform)
                .resolve("bin")
                .resolve(executable);
    }

    private static boolean matchesFlatRuntime(Path directory, String major, String platform) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        String prefix = "jdk-" + major;
        return (name.startsWith(prefix + ".") || name.startsWith(prefix + "-"))
                && name.endsWith('-' + platform);
    }

    private static List<Integer> runtimeVersion(Path directory, String platform) {
        String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
        String suffix = '-' + platform;
        String version = name.substring("jdk-".length(), name.length() - suffix.length());
        List<Integer> parts = new ArrayList<>();
        for (String part : version.split("\\.")) {
            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return List.copyOf(parts);
    }

    private static int compareVersions(List<Integer> left, List<Integer> right) {
        int length = Math.max(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            int leftPart = index < left.size() ? left.get(index) : 0;
            int rightPart = index < right.size() ? right.get(index) : 0;
            int compared = Integer.compare(leftPart, rightPart);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static String normalizeMajor(String requiredMajor) {
        if (requiredMajor == null) {
            throw new IllegalArgumentException("Runtime Java major must not be null.");
        }
        String digits = requiredMajor.trim().replaceFirst("(?i)^java\\s*", "");
        int separator = digits.indexOf('.');
        if (separator > 0) {
            digits = digits.substring(0, separator);
        }
        if (!digits.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("Invalid runtime Java major: " + requiredMajor);
        }
        return digits;
    }

    private static String normalizePlatform(String platformId) {
        if (platformId == null || !platformId.trim().matches("[A-Za-z0-9_-]{3,64}")) {
            throw new IllegalArgumentException("Invalid runtime platform identifier: " + platformId);
        }
        return platformId.trim().toLowerCase(Locale.ROOT);
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
