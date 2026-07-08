package org.takesome.kaylasEngine.fileLoader.fileGuard;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.game.GameLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class FileGuard {
    private FileGuardListener fileGuardListener;
    private final List<Path> checkList;
    private final Set<Path> ignoreList;
    private final String[] basicIgnoreDirs = {"saves", "resourcepacks", "shaderpacks", "screenshots", "logs", "config"};
    private final GameLauncher gameLauncher;
    private final Logger logger;
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger checkedFiles = new AtomicInteger(0);
    private final AtomicInteger filesDeleted = new AtomicInteger(0);

    public FileGuard(GameLauncher gameLauncher, List<String> checkList) {
        this.gameLauncher = gameLauncher;
        this.logger = this.gameLauncher.getLogger();
        this.checkList = new CopyOnWriteArrayList<>(convertToPaths(checkList));
        this.ignoreList = new HashSet<>();
        buildBasicIgnoreList();
    }

    private List<Path> convertToPaths(List<String> paths) {
        List<Path> result = new ArrayList<>();
        for (String path : paths) {
            result.add(Paths.get(path).normalize());
        }
        return result;
    }

    public void scanAndDeleteFilesInSubdirectories(Set<String> filesToKeep) {
        this.gameLauncher.getEngine().getExecutorServiceProvider().submitTask(() -> {
            totalFiles.set(countTotalFiles());
            resetCounters();

            logger.info("Ignoring the following directories:");
            ignoreList.forEach(dir -> logger.info("  - {}", dir));

            for (Path dir : checkList) {
                logger.debug("Checking Directory: {}", dir);
                if (fileGuardListener != null) {
                    fileGuardListener.onDirCheck(dir.toString());
                }
                scanAndDeleteFilesRecursively(dir, convertToPaths(new ArrayList<>(filesToKeep)));
            }

            if (fileGuardListener != null) {
                fileGuardListener.onFilesChecked(filesDeleted.get());
            }
        }, "fileGuard");
    }

    private void resetCounters() {
        checkedFiles.set(0);
        filesDeleted.set(0);
    }

    private int countTotalFiles() {
        return checkList.stream()
                .filter(Files::exists)
                .mapToInt(this::countFilesInDirectory)
                .sum();
    }

    private int countFilesInDirectory(Path directory) {
        try {
            return (int) Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (Exception e) {
            logger.error("Error counting files in directory: {}", directory, e);
            return 0;
        }
    }

    public void removeEmptyFolders(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try {
            try (Stream<Path> subPaths = Files.list(dir)) {
                subPaths.filter(Files::isDirectory).forEach(this::removeEmptyFolders);
            }

            boolean isEmpty;
            try (Stream<Path> dirContents = Files.list(dir)) {
                isEmpty = dirContents.findAny().isEmpty();
            }

            if (isEmpty) {
                Files.delete(dir);
                logger.debug("Removed empty directory: {}", dir);
            }
        } catch (DirectoryNotEmptyException e) {
            logger.debug("Directory is not empty (concurrent modification?): {}", dir);
        } catch (IOException e) {
            logger.error("Error while processing directory: {}", dir, e);
        }
    }

    private void scanAndDeleteFilesRecursively(Path directory, List<Path> filesToKeep) {
        try {
            if (isInIgnoreList(directory)) {
                logger.info("Skipping directory (ignored): {}", directory);
                return;
            }

            logger.debug("Scanning directory: {}", directory);

            Files.list(directory).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    checkAndDeleteFile(path, filesToKeep);
                } else if (Files.isDirectory(path)) {
                    scanAndDeleteFilesRecursively(path, filesToKeep);
                    removeEmptyFolders(path);
                }
            });
        } catch (Exception e) {
            logger.error("Error scanning directory: {}", directory, e);
        }
    }


    private void checkAndDeleteFile(Path file, List<Path> filesToKeep) {
        if (fileGuardListener != null) {
            fileGuardListener.onFileCheck(file.toFile());
        }
        String relativePath = getRelativePath(file);
        if (!filesToKeep.contains(Paths.get(relativePath)) && !isUserConfig(file) && !isInIgnoreList(file)) {
            try {
                Files.delete(file);
                logger.debug("Deleted unlisted file: {}", relativePath);
                filesDeleted.incrementAndGet();
            } catch (Exception e) {
                logger.error("Failed to delete file: {}. Error: {}", relativePath, e.getMessage());
            }
        } else {
            logger.debug("{} is checked", relativePath);
        }
        checkedFiles.incrementAndGet();
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public void recursiveDelete(File file) {
        if (!file.exists()) return;

        if (file.isDirectory()) {
            Arrays.stream(Optional.ofNullable(file.listFiles()).orElse(new File[0]))
                    .forEach(this::recursiveDelete);
        }

        if (file.delete()) {
            logger.debug("Deleted file/directory: {}", file);
        } else {
            logger.error("Failed to delete: {}", file);
        }
    }
    private String getRelativePath(Path filePath) {
        Path relativePath = gameLauncher.getPathBuilders().buildGameDir().relativize(filePath);
        return relativePath.toString().replace("\\", "/");
    }

    private boolean isInIgnoreList(Path filePath) {
        Path absolutePath = filePath.normalize().toAbsolutePath();
        return ignoreList.stream()
                .map(Path::toAbsolutePath)
                .anyMatch(absolutePath::startsWith);
    }


    private void buildBasicIgnoreList() {
        Arrays.stream(basicIgnoreDirs).forEach(dir -> {
            try {
                Path ignoreDirPath = gameLauncher.getPathBuilders()
                        .buildClientDir()
                        .resolve(dir)
                        .normalize()
                        .toAbsolutePath();

                ignoreList.add(ignoreDirPath);
                logger.debug("Added to ignore list: {}", ignoreDirPath);
            } catch (Exception e) {
                logger.error("Error building ignore list for directory: {}", dir, e);
            }
        });
    }

    public void addIgnoreDirs(String dirs) {
        if (dirs != null) {
            Arrays.stream(dirs.split(","))
                    .map(dir -> {
                        try {
                            return gameLauncher.getPathBuilders()
                                    .buildClientDir()
                                    .resolve(dir)
                                    .normalize()
                                    .toAbsolutePath();
                        } catch (Exception e) {
                            logger.error("Error adding ignore directory: {}", dir, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(ignoreList::add);
        }
    }


    private boolean isUserConfig(Path file) {
        return file.getFileName().toString().endsWith(".txt");
    }

    public void setFileGuardListener(FileGuardListener fileGuardListener) {
        this.fileGuardListener = fileGuardListener;
    }
}
