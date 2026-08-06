package org.takesome.kaylasEngine.fileLoader.fileGuard;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.game.GameLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Asynchronous, fail-closed guard for the downloaded game tree.
 *
 * <p>The guard never follows symbolic links or junctions, confines every operation to the game
 * root, removes unlisted entries, and verifies that every listed file covered by a check root is
 * still present as a regular file before reporting success.</p>
 */
@SuppressWarnings("unused")
public class FileGuard {
    private static final List<String> BASIC_IGNORE_DIRS = List.of(
            "saves",
            "resourcepacks",
            "shaderpacks",
            "screenshots",
            "logs",
            "config"
    );

    private final GameLauncher gameLauncher;
    private final Logger logger;
    private final Path gameRoot;
    private final Path clientRoot;
    private final List<Path> checkList;
    private final Set<Path> ignoreList = new CopyOnWriteArraySet<>();
    private volatile FileGuardListener fileGuardListener;

    public FileGuard(GameLauncher gameLauncher, List<String> checkList) {
        this.gameLauncher = Objects.requireNonNull(gameLauncher, "gameLauncher");
        this.logger = gameLauncher.getLogger();
        this.gameRoot = gameLauncher.getPathBuilders().buildGameDir().toAbsolutePath().normalize();
        this.clientRoot = gameLauncher.getPathBuilders().buildClientDir().toAbsolutePath().normalize();
        if (!clientRoot.startsWith(gameRoot) || clientRoot.equals(gameRoot)) {
            throw new IllegalArgumentException("Client directory escaped the game root: " + clientRoot);
        }
        this.checkList = convertToPaths(checkList);
        buildBasicIgnoreList();
    }

    /**
     * Starts a guard pass and preserves the legacy fire-and-forget API.
     */
    public void scanAndDeleteFilesInSubdirectories(Set<String> filesToKeep) {
        scanAsync(filesToKeep, List.of());
    }

    /**
     * Starts a guard pass with cleanup targets that must be removed safely before scanning.
     */
    public void scanAndDeleteFilesInSubdirectories(
            Set<String> filesToKeep,
            Collection<Path> cleanupTargets
    ) {
        scanAsync(filesToKeep, cleanupTargets);
    }

    public CompletableFuture<FileGuardReport> scanAsync(Set<String> filesToKeep) {
        return scanAsync(filesToKeep, List.of());
    }

    public CompletableFuture<FileGuardReport> scanAsync(
            Set<String> filesToKeep,
            Collection<Path> cleanupTargets
    ) {
        Set<String> manifestSnapshot = copyManifest(filesToKeep);
        List<Path> cleanupSnapshot = cleanupTargets == null
                ? List.of()
                : List.copyOf(cleanupTargets);
        Set<Path> ignoreSnapshot = Set.copyOf(ignoreList);
        FileGuardListener listenerSnapshot = fileGuardListener;
        long startedAt = System.nanoTime();

        CompletableFuture<FileGuardReport> future = gameLauncher
                .getEngine()
                .getExecutorServiceProvider()
                .supplyAsyncQuietly(() -> {
                    GuardedFileTree guardedTree = new GuardedFileTree(
                            gameRoot,
                            checkList,
                            ignoreSnapshot,
                            listenerSnapshot
                    );
                    for (Path cleanupTarget : cleanupSnapshot) {
                        if (cleanupTarget == null) {
                            throw new IllegalArgumentException("File guard cleanup target cannot be null");
                        }
                        guardedTree.deleteTree(cleanupTarget);
                    }
                    logger.info("Starting FileGuard: roots={}, manifestFiles={}, ignoredRoots={}",
                            checkList.size(), manifestSnapshot.size(), ignoreSnapshot.size());
                    return guardedTree.scan(manifestSnapshot);
                }, "fileGuard");

        future.whenComplete((report, failure) -> {
            if (failure == null) {
                logger.info(
                        "FileGuard completed in {} ms: checked={}, kept={}, deleted={}, symlinksDeleted={}, emptyDirsDeleted={}",
                        report.durationMillis(),
                        report.filesChecked(),
                        report.filesKept(),
                        report.filesDeleted(),
                        report.symbolicLinksDeleted(),
                        report.directoriesDeleted()
                );
                notifyCompleted(listenerSnapshot, report);
                return;
            }

            Throwable cause = unwrap(failure);
            FileGuardReport partialReport = cause instanceof FileGuardScanException scanFailure
                    ? scanFailure.report()
                    : FileGuardReport.failed(elapsedMillis(startedAt));
            logger.error(
                    "FileGuard failed closed after {} ms; Minecraft launch is blocked.",
                    partialReport.durationMillis(),
                    cause
            );
            notifyFailed(listenerSnapshot, partialReport, cause);
        });
        return future;
    }

    /**
     * Deletes a tree without following symbolic links. The game root itself is never accepted.
     */
    public void recursiveDelete(File file) {
        Objects.requireNonNull(file, "file");
        try {
            new GuardedFileTree(gameRoot, List.of(), Set.of(), fileGuardListener)
                    .deleteTree(file.toPath());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Unable to safely delete guarded path: " + file, error);
        }
    }

    public void removeEmptyFolders(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            new GuardedFileTree(gameRoot, List.of(), Set.of(), fileGuardListener)
                    .removeEmptyDirectories(directory);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Unable to safely remove empty directories below: " + directory, error);
        }
    }

    public void addIgnoreDirs(String dirs) {
        parseIgnoreDirectories(dirs).stream()
                .map(this::resolveClientRelativeDirectory)
                .forEach(ignoreList::add);
    }

    static List<String> parseIgnoreDirectories(String dirs) {
        if (dirs == null || dirs.isBlank()) {
            return List.of();
        }

        String source = dirs.trim();
        if (source.startsWith("[")) {
            try {
                JsonElement root = JsonParser.parseString(source);
                if (!root.isJsonArray()) {
                    throw new IllegalArgumentException("ignoreDirs JSON must be an array");
                }
                List<String> result = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("ignoreDirs JSON entries must be strings");
                    }
                    String value = element.getAsString().trim();
                    if (!value.isEmpty()) {
                        result.add(value);
                    }
                }
                return List.copyOf(result);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid FileGuard ignoreDirs JSON: " + source, error);
            }
        }

        return Arrays.stream(source.split(","))
                .map(String::trim)
                .map(FileGuard::stripOptionalQuotes)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String stripOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    public void setFileGuardListener(FileGuardListener fileGuardListener) {
        this.fileGuardListener = fileGuardListener;
    }

    private List<Path> convertToPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("File guard check roots are missing");
        }
        List<Path> result = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("File guard check root is empty");
            }
            Path normalized = Path.of(path).toAbsolutePath().normalize();
            if (!normalized.startsWith(gameRoot)) {
                throw new IllegalArgumentException("File guard check root escaped the game root: " + path);
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private void buildBasicIgnoreList() {
        BASIC_IGNORE_DIRS.stream()
                .map(this::resolveClientRelativeDirectory)
                .forEach(ignoreList::add);
    }

    private Path resolveClientRelativeDirectory(String rawPath) {
        String portable = rawPath.trim().replace('\\', '/');
        Path relative = Path.of(portable);
        if (relative.isAbsolute() || relative.getRoot() != null || containsParentTraversal(relative)) {
            throw new IllegalArgumentException("Unsafe FileGuard ignore directory: " + rawPath);
        }

        Path normalizedRelative = relative.normalize();
        Path resolved = clientRoot.resolve(normalizedRelative).normalize();
        if (normalizedRelative.getNameCount() == 0
                || normalizedRelative.toString().equals(".")
                || !resolved.startsWith(clientRoot)
                || resolved.equals(clientRoot)) {
            throw new IllegalArgumentException("Unsafe FileGuard ignore directory: " + rawPath);
        }
        return resolved;
    }

    private Set<String> copyManifest(Set<String> filesToKeep) {
        if (filesToKeep == null) {
            throw new IllegalArgumentException("File guard manifest is missing");
        }
        return Set.copyOf(new LinkedHashSet<>(filesToKeep));
    }

    private boolean containsParentTraversal(Path path) {
        for (Path part : path) {
            if (part.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }

    private void notifyCompleted(FileGuardListener listener, FileGuardReport report) {
        if (listener == null) {
            return;
        }
        try {
            listener.onGuardCompleted(report);
        } catch (RuntimeException error) {
            logger.error("FileGuard completion listener failed", error);
        }
    }

    private void notifyFailed(FileGuardListener listener, FileGuardReport report, Throwable error) {
        if (listener == null) {
            return;
        }
        try {
            listener.onGuardFailed(report, error);
        } catch (RuntimeException callbackError) {
            logger.error("FileGuard failure listener failed", callbackError);
        }
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}
