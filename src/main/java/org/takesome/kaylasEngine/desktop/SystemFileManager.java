package org.takesome.kaylasEngine.desktop;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.service.ExecutorServiceProvider;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Opens files and directories through the operating system's native desktop shell.
 *
 * <p>Desktop integration runs on the engine executor so a slow shell invocation never blocks the
 * Swing event-dispatch thread. Directory paths are normalized and created before the native file
 * manager is invoked.</p>
 */
public final class SystemFileManager {
    private final ExecutorServiceProvider executorServiceProvider;
    private final Logger logger;

    public SystemFileManager(ExecutorServiceProvider executorServiceProvider, Logger logger) {
        this.executorServiceProvider = Objects.requireNonNull(executorServiceProvider, "executorServiceProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Opens the supplied directory in Explorer, Finder, or the platform's configured file manager.
     *
     * @param directory directory to open; relative paths are resolved against the current process
     *                  working directory.
     * @return future completed with the normalized directory after the shell accepted the request.
     */
    public CompletableFuture<Path> openDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        return executorServiceProvider.supplyAsync(
                () -> openDirectoryBlocking(normalized),
                "open-directory-" + safeTaskName(normalized.getFileName())
        );
    }

    private Path openDirectoryBlocking(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory)) {
            throw new IOException("Path is not a directory: " + directory);
        }

        if (tryDesktopOpen(directory)) {
            logger.debug("Opened directory through java.awt.Desktop: {}", directory);
            return directory;
        }

        List<String> command = nativeFileManagerCommand(directory);
        new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        logger.debug("Opened directory through native file manager command: {}", command);
        return directory;
    }

    private boolean tryDesktopOpen(Path directory) throws IOException {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return false;
        }
        desktop.open(directory.toFile());
        return true;
    }

    private List<String> nativeFileManagerCommand(Path directory) throws IOException {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return List.of("explorer.exe", directory.toString());
        }
        if (osName.contains("mac")) {
            return List.of("open", directory.toString());
        }
        if (osName.contains("linux") || osName.contains("unix") || osName.contains("bsd")) {
            return List.of("xdg-open", directory.toString());
        }
        throw new IOException("No native file manager integration is configured for OS: " + osName);
    }

    private static String safeTaskName(Path fileName) {
        String value = fileName == null ? "directory" : fileName.toString();
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
