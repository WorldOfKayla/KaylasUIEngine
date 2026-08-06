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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Expected directory format: jdk-full-version-platform, for example
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
        String version = runtimeVersionToken(directory, platform);
        return version != null && major.equals(majorFromVersion(version));
    }

    private static List<Integer> runtimeVersion(Path directory, String platform) {
        String version = runtimeVersionToken(directory, platform);
        if (version == null) {
            return List.of();
        }

        List<Integer> parts = new ArrayList<>();
        if (version.matches("(?i)^1\\.8\\.0[_-][0-9]+.*$")) {
            parts.add(8);
            parts.add(0);
            Matcher update = Pattern.compile("(?i)^1\\.8\\.0[_-]([0-9]+)").matcher(version);
            if (update.find()) {
                parts.add(parseVersionPart(update.group(1)));
            }
            return List.copyOf(parts);
        }
        if (version.matches("(?i)^8u[0-9]+.*$")) {
            parts.add(8);
            parts.add(0);
            Matcher update = Pattern.compile("(?i)^8u([0-9]+)").matcher(version);
            if (update.find()) {
                parts.add(parseVersionPart(update.group(1)));
            }
            return List.copyOf(parts);
        }

        Matcher numbers = Pattern.compile("[0-9]+").matcher(version);
        while (numbers.find()) {
            parts.add(parseVersionPart(numbers.group()));
        }
        return List.copyOf(parts);
    }

    private static String runtimeVersionToken(Path directory, String platform) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        String suffix = '-' + platform;
        if (!name.startsWith("jdk-") || !name.endsWith(suffix)) {
            return null;
        }
        String version = name.substring("jdk-".length(), name.length() - suffix.length());
        return version.isBlank() ? null : version;
    }

    private static String majorFromVersion(String version) {
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("^1\\.8(?:\\.0)?(?:[_+.-].*)?$")) {
            return "8";
        }
        if (normalized.matches("^8u[0-9]+(?:[_+.-].*)?$")) {
            return "8";
        }
        Matcher leading = Pattern.compile("^([0-9]{1,3})(?:$|[._+u-])").matcher(normalized);
        return leading.find() ? leading.group(1) : "";
    }

    private static int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
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
        String version = requiredMajor.trim()
                .replaceFirst("(?i)^(?:java|jdk|jre)\\s*[-_]?\\s*", "");
        String major = majorFromVersion(version);
        if (major.isEmpty()) {
            throw new IllegalArgumentException("Invalid runtime Java major: " + requiredMajor);
        }
        return major;
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
