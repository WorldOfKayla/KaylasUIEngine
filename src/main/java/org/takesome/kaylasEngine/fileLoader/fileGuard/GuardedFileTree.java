package org.takesome.kaylasEngine.fileLoader.fileGuard;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Symlink-safe, root-confined implementation behind {@link FileGuard}.
 */
final class GuardedFileTree {
    private final Path gameRoot;
    private final List<Path> checkRoots;
    private final Set<Path> ignoredRoots;
    private final FileGuardListener listener;

    GuardedFileTree(
            Path gameRoot,
            Collection<Path> checkRoots,
            Collection<Path> ignoredRoots,
            FileGuardListener listener
    ) {
        this.gameRoot = normalizeAbsolute(Objects.requireNonNull(gameRoot, "gameRoot"));
        this.checkRoots = normalizeCheckRoots(checkRoots);
        this.ignoredRoots = normalizeIgnoredRoots(ignoredRoots);
        this.listener = listener;
    }

    FileGuardReport scan(Set<String> manifestEntries) throws IOException {
        long startedAt = System.nanoTime();
        MutableReport metrics = new MutableReport();
        try {
            requireDirectory(gameRoot, "Game root");
            ensureNoSymbolicLinks(gameRoot);
            Set<Path> manifest = normalizeManifest(gameRoot, manifestEntries);
            metrics.manifestFiles = countCoveredManifestFiles(manifest);

            for (Path checkRoot : checkRoots) {
                checkInterrupted();
                requireDirectory(checkRoot, "File guard root");
                ensureNoSymbolicLinks(checkRoot);
                if (isIgnoredDirectory(checkRoot)) {
                    continue;
                }
                metrics.rootsChecked++;
                if (listener != null) {
                    listener.onDirCheck(checkRoot.toString());
                }
                scanRoot(checkRoot, manifest, metrics);
            }

            verifyManifestFiles(manifest);
            return metrics.snapshot(true, startedAt);
        } catch (IOException | RuntimeException error) {
            throw new FileGuardScanException(
                    "File guard failed: " + safeMessage(error),
                    metrics.snapshot(false, startedAt),
                    error
            );
        }
    }

    void deleteTree(Path requestedTarget) throws IOException {
        Path target = containedPath(requestedTarget, "Delete target");
        if (target.equals(gameRoot)) {
            throw new IOException("Refusing to recursively delete the game root");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Delete target has no parent: " + target);
        }
        ensureNoSymbolicLinks(parent);
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                checkInterrupted();
                requireContainedCallback(directory);
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    throw new IOException("Refusing to traverse symbolic-link directory: " + directory);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                checkInterrupted();
                requireContainedCallback(file);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                throw new IOException("Unable to inspect delete target: " + file, error);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                requireContainedCallback(directory);
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void removeEmptyDirectories(Path requestedRoot) throws IOException {
        Path root = containedPath(requestedRoot, "Empty-directory cleanup root");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        ensureNoSymbolicLinks(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                checkInterrupted();
                requireContainedCallback(directory);
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                if (!directory.equals(root)) {
                    try {
                        Files.delete(directory);
                    } catch (DirectoryNotEmptyException ignored) {
                        // Expected for directories that still contain guarded files.
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static Set<Path> normalizeManifest(Path requestedGameRoot, Set<String> entries) {
        Path root = normalizeAbsolute(Objects.requireNonNull(requestedGameRoot, "gameRoot"));
        if (entries == null) {
            throw new IllegalArgumentException("File guard manifest is missing");
        }

        Set<Path> result = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("File guard manifest contains an empty path");
            }
            String portable = entry.trim().replace('\\', '/');
            Path relative = Path.of(portable);
            if (relative.isAbsolute() || relative.getRoot() != null || containsParentTraversal(relative)) {
                throw new IllegalArgumentException("Unsafe file guard manifest path: " + entry);
            }
            Path normalizedRelative = relative.normalize();
            if (normalizedRelative.getNameCount() == 0 || normalizedRelative.toString().equals(".")) {
                throw new IllegalArgumentException("Unsafe file guard manifest path: " + entry);
            }
            Path absolute = root.resolve(normalizedRelative).normalize();
            if (!absolute.startsWith(root) || absolute.equals(root)) {
                throw new IllegalArgumentException("File guard manifest path escaped the game root: " + entry);
            }
            result.add(absolute);
        }
        return Set.copyOf(result);
    }

    private void scanRoot(Path root, Set<Path> manifest, MutableReport metrics) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                checkInterrupted();
                requireContainedCallback(directory);
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    throw new IOException("Refusing to traverse symbolic-link directory: " + directory);
                }
                if (!directory.equals(root) && isIgnoredDirectory(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                checkInterrupted();
                Path normalizedFile = requireContainedCallback(file);
                if (ignoredRoots.contains(normalizedFile)) {
                    throw new IOException("Configured ignore path is not a directory: " + normalizedFile);
                }
                if (listener != null) {
                    listener.onFileCheck(normalizedFile.toFile());
                }
                metrics.filesChecked++;

                boolean symbolicLink = attributes.isSymbolicLink() || Files.isSymbolicLink(normalizedFile);
                boolean regularFile = attributes.isRegularFile() && !symbolicLink;
                boolean expected = manifest.contains(normalizedFile);

                if (!regularFile) {
                    if (expected) {
                        throw new IOException("Manifest entry is not a regular file: " + normalizedFile);
                    }
                    Files.delete(normalizedFile);
                    metrics.filesDeleted++;
                    if (symbolicLink) {
                        metrics.symbolicLinksDeleted++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                if (expected) {
                    metrics.filesKept++;
                } else {
                    Files.delete(normalizedFile);
                    metrics.filesDeleted++;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                throw new IOException("Unable to inspect guarded path: " + file, error);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                requireContainedCallback(directory);
                if (!directory.equals(root) && !isIgnoredDirectory(directory)) {
                    try {
                        Files.delete(directory);
                        metrics.directoriesDeleted++;
                    } catch (DirectoryNotEmptyException ignored) {
                        // The directory still contains listed or ignored data.
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void verifyManifestFiles(Set<Path> manifest) throws IOException {
        for (Path expectedFile : manifest) {
            if (!isCoveredByCheckRoot(expectedFile) || isInsideIgnoredRoot(expectedFile)) {
                continue;
            }
            Path parent = expectedFile.getParent();
            if (parent == null) {
                throw new IOException("Manifest path has no parent: " + expectedFile);
            }
            ensureNoSymbolicLinks(parent);
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(
                        expectedFile,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
            } catch (IOException error) {
                throw new IOException("Required manifest file is missing or unreadable: " + expectedFile, error);
            }
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw new IOException("Required manifest entry is not a regular file: " + expectedFile);
            }
        }
    }

    private int countCoveredManifestFiles(Set<Path> manifest) {
        int count = 0;
        for (Path entry : manifest) {
            if (isCoveredByCheckRoot(entry) && !isInsideIgnoredRoot(entry)) {
                count++;
            }
        }
        return count;
    }

    private boolean isCoveredByCheckRoot(Path path) {
        return checkRoots.stream().anyMatch(path::startsWith);
    }

    private boolean isIgnoredDirectory(Path directory) {
        return ignoredRoots.stream().anyMatch(directory::startsWith);
    }

    private boolean isInsideIgnoredRoot(Path path) {
        return ignoredRoots.stream().anyMatch(path::startsWith);
    }

    private List<Path> normalizeCheckRoots(Collection<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Path> normalized = new ArrayList<>();
        for (Path root : roots) {
            normalized.add(containedPath(root, "File guard root"));
        }
        normalized.sort(Comparator
                .comparingInt(Path::getNameCount)
                .thenComparing(Path::toString));

        List<Path> minimalRoots = new ArrayList<>();
        for (Path candidate : normalized) {
            if (minimalRoots.stream().noneMatch(candidate::startsWith)) {
                minimalRoots.add(candidate);
            }
        }
        return List.copyOf(minimalRoots);
    }

    private Set<Path> normalizeIgnoredRoots(Collection<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            return Set.of();
        }
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path root : roots) {
            Path safeRoot = containedPath(root, "Ignore root");
            if (safeRoot.equals(gameRoot)) {
                throw new IllegalArgumentException("The game root cannot be ignored");
            }
            normalized.add(safeRoot);
        }
        return Set.copyOf(normalized);
    }

    private Path containedPath(Path path, String description) {
        Path normalized = normalizeAbsolute(Objects.requireNonNull(path, description));
        if (!normalized.startsWith(gameRoot)) {
            throw new IllegalArgumentException(description + " escaped the game root: " + path);
        }
        return normalized;
    }

    private Path requireContainedCallback(Path path) throws IOException {
        Path normalized = normalizeAbsolute(path);
        if (!normalized.startsWith(gameRoot)) {
            throw new IOException("Filesystem traversal escaped the game root: " + path);
        }
        return normalized;
    }

    private void ensureNoSymbolicLinks(Path path) throws IOException {
        Path safePath;
        try {
            safePath = containedPath(path, "Path");
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }

        if (Files.isSymbolicLink(gameRoot)) {
            throw new IOException("Game root cannot be a symbolic link: " + gameRoot);
        }
        Path current = gameRoot;
        Path relative = gameRoot.relativize(safePath);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic-link path component is forbidden: " + current);
            }
        }
    }

    private static void requireDirectory(Path path, String description) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException(description + " is not a real directory: " + path);
        }
    }

    private static boolean containsParentTraversal(Path path) {
        for (Path part : path) {
            if (part.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static Path normalizeAbsolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("File guard scan was interrupted");
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    private static final class MutableReport {
        private int manifestFiles;
        private int rootsChecked;
        private int filesChecked;
        private int filesKept;
        private int filesDeleted;
        private int symbolicLinksDeleted;
        private int directoriesDeleted;

        private FileGuardReport snapshot(boolean successful, long startedAt) {
            return new FileGuardReport(
                    successful,
                    manifestFiles,
                    rootsChecked,
                    filesChecked,
                    filesKept,
                    filesDeleted,
                    symbolicLinksDeleted,
                    directoriesDeleted,
                    elapsedMillis(startedAt)
            );
        }
    }
}

final class FileGuardScanException extends IOException {
    private final FileGuardReport report;

    FileGuardScanException(String message, FileGuardReport report, Throwable cause) {
        super(message, cause);
        this.report = Objects.requireNonNull(report, "report");
    }

    FileGuardReport report() {
        return report;
    }
}
