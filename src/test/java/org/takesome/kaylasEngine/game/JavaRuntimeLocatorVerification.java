package org.takesome.kaylasEngine.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/** Regression verification for flat runtime directory discovery. */
public final class JavaRuntimeLocatorVerification {
    private JavaRuntimeLocatorVerification() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("java-runtime-locator-");
        try {
            verifyLegacyReleaseDirectory(root.resolve("legacy-release"));
            verifyLegacyUpdateDirectory(root.resolve("legacy-update"));
            verifyNewestLegacyUpdateWins(root.resolve("legacy-newest"));
            verifyModernRuntimeStillResolves(root.resolve("modern"));
            System.out.println("Java runtime locator verification passed.");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void verifyLegacyReleaseDirectory(Path runtimeRoot) throws IOException {
        String platform = platformId();
        Path expected = createRuntime(runtimeRoot, "jdk-1.8.0_502-" + platform);

        require(expected.equals(JavaRuntimeLocator.locateFlatRuntime(runtimeRoot, "8", platform)),
                "Java 8 major did not resolve jdk-1.8.0_502 directory");
        require(expected.equals(JavaRuntimeLocator.locateFlatRuntime(runtimeRoot, "1.8.0_502", platform)),
                "legacy 1.8.0_502 selector did not normalize to Java 8");
    }

    private static void verifyLegacyUpdateDirectory(Path runtimeRoot) throws IOException {
        String platform = platformId();
        Path expected = createRuntime(runtimeRoot, "jdk-8u502-" + platform);

        require(expected.equals(JavaRuntimeLocator.locateFlatRuntime(runtimeRoot, "jdk-8", platform)),
                "jdk-8 selector did not resolve jdk-8u502 directory");
    }

    private static void verifyNewestLegacyUpdateWins(Path runtimeRoot) throws IOException {
        String platform = platformId();
        createRuntime(runtimeRoot, "jdk-8u401-" + platform);
        Path expected = createRuntime(runtimeRoot, "jdk-1.8.0_502-" + platform);

        require(expected.equals(JavaRuntimeLocator.locateFlatRuntime(runtimeRoot, "8", platform)),
                "runtime locator did not choose the newest Java 8 update");
    }

    private static void verifyModernRuntimeStillResolves(Path runtimeRoot) throws IOException {
        String platform = platformId();
        createRuntime(runtimeRoot, "jdk-17.0.13-" + platform);
        Path expected = createRuntime(runtimeRoot, "jdk-17.0.20-" + platform);

        require(expected.equals(JavaRuntimeLocator.locateFlatRuntime(runtimeRoot, "17", platform)),
                "modern Java runtime resolution regressed");
    }

    private static Path createRuntime(Path runtimeRoot, String directoryName) throws IOException {
        Path executable = runtimeRoot.resolve(directoryName).resolve("bin").resolve(executableName());
        Files.createDirectories(executable.getParent());
        Files.write(executable, new byte[]{0});
        return executable.toAbsolutePath().normalize();
    }

    private static String platformId() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String operatingSystem = osName.contains("win") ? "windows" : osName.contains("mac") ? "macos" : "linux";
        String architectureName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String architecture = switch (architectureName) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> architectureName.replaceAll("[^a-z0-9_-]", "_");
        };
        return operatingSystem + '-' + architecture;
    }

    private static String executableName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
