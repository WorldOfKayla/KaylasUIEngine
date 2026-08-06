package org.takesome.kaylasEngine.fileLoader.fileGuard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class FileGuardVerification {
    private FileGuardVerification() {
    }

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("kaylas-guard-");
        Path gameRoot = Files.createDirectories(base.resolve("game"));
        Path clientRoot = Files.createDirectories(gameRoot.resolve("clients/demo"));
        Path ignoredRoot = Files.createDirectories(clientRoot.resolve("config"));
        Path keptFile = clientRoot.resolve("kept.jar");
        Path extraFile = clientRoot.resolve("injected.jar");
        Path ignoredFile = ignoredRoot.resolve("user-options.json");
        Path outsideFile = base.resolve("outside.txt");

        Files.writeString(keptFile, "trusted");
        Files.writeString(extraFile, "unlisted");
        Files.writeString(ignoredFile, "user-owned");
        Files.writeString(outsideFile, "outside");

        try {
            AtomicInteger completedFiles = new AtomicInteger();
            GuardedFileTree guard = new GuardedFileTree(
                    gameRoot,
                    List.of(clientRoot, clientRoot.resolve("natives")),
                    Set.of(ignoredRoot),
                    new CountingListener(completedFiles)
            );

            boolean linkCreated = createLink(clientRoot.resolve("outside-link"), outsideFile);
            FileGuardReport report = guard.scan(Set.of("clients/demo/kept.jar"));

            assertTrue(report.successful(), "guard report must be successful");
            assertTrue(Files.isRegularFile(keptFile, LinkOption.NOFOLLOW_LINKS), "listed file must remain");
            assertTrue(!Files.exists(extraFile, LinkOption.NOFOLLOW_LINKS), "unlisted file must be deleted");
            assertTrue(Files.isRegularFile(ignoredFile, LinkOption.NOFOLLOW_LINKS), "ignored user data must remain");
            assertTrue(Files.isRegularFile(outsideFile, LinkOption.NOFOLLOW_LINKS), "outside target must remain");
            if (linkCreated) {
                assertTrue(
                        !Files.exists(clientRoot.resolve("outside-link"), LinkOption.NOFOLLOW_LINKS),
                        "unlisted symbolic link must be deleted without following it"
                );
                assertTrue(report.symbolicLinksDeleted() == 1, "deleted symbolic link must be reported");
            }
            assertTrue(completedFiles.get() >= 2, "listener must receive file progress");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> GuardedFileTree.normalizeManifest(gameRoot, Set.of("../escape.jar")),
                    "parent traversal in manifest must be rejected"
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GuardedFileTree(
                            gameRoot,
                            List.of(base),
                            Set.of(),
                            null
                    ),
                    "guard root outside game root must be rejected"
            );
            assertThrows(
                    FileGuardScanException.class,
                    () -> guard.scan(Set.of("clients/demo/kept.jar", "clients/demo/missing.jar")),
                    "missing manifest file must fail closed"
            );

            verifyListedSymbolicLinkFails(gameRoot, clientRoot, ignoredRoot, outsideFile);
            verifyIgnoreDirectoryParsing();
            System.out.println("FileGuardVerification: OK");
        } finally {
            deleteTree(base);
        }
    }

    private static void verifyIgnoreDirectoryParsing() throws Exception {
        assertTrue(
                FileGuard.parseIgnoreDirectories("[\"config\",\"saves\"]")
                        .equals(List.of("config", "saves")),
                "JSON ignoreDirs array was not parsed"
        );
        assertTrue(
                FileGuard.parseIgnoreDirectories("config,saves,xaero")
                        .equals(List.of("config", "saves", "xaero")),
                "legacy comma-separated ignoreDirs was not parsed"
        );
        assertTrue(
                FileGuard.parseIgnoreDirectories("\"config\",'saves'")
                        .equals(List.of("config", "saves")),
                "quoted legacy ignoreDirs was not normalized"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FileGuard.parseIgnoreDirectories("[\"config\",") ,
                "malformed ignoreDirs JSON must fail clearly"
        );
    }

    private static void verifyListedSymbolicLinkFails(
            Path gameRoot,
            Path clientRoot,
            Path ignoredRoot,
            Path outsideFile
    ) throws Exception {
        Path listedLink = clientRoot.resolve("listed-link.jar");
        if (!createLink(listedLink, outsideFile)) {
            return;
        }

        GuardedFileTree guard = new GuardedFileTree(
                gameRoot,
                List.of(clientRoot),
                Set.of(ignoredRoot),
                null
        );
        assertThrows(
                FileGuardScanException.class,
                () -> guard.scan(Set.of("clients/demo/kept.jar", "clients/demo/listed-link.jar")),
                "listed symbolic link must fail closed"
        );
        assertTrue(Files.isRegularFile(outsideFile), "listed link failure must not alter its outside target");
        Files.deleteIfExists(listedLink);
    }

    private static boolean createLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.deleteIfExists(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void assertThrows(
            Class<? extends Throwable> expectedType,
            ThrowingRunnable action,
            String message
    ) throws Exception {
        try {
            action.run();
        } catch (Throwable error) {
            if (expectedType.isInstance(error)) {
                return;
            }
            throw new AssertionError(message + ": unexpected exception " + error, error);
        }
        throw new AssertionError(message + ": no exception was thrown");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CountingListener implements FileGuardListener {
        private final AtomicInteger checkedFiles;

        private CountingListener(AtomicInteger checkedFiles) {
            this.checkedFiles = checkedFiles;
        }

        @Override
        public void onFilesChecked(int filesDeleted) {
        }

        @Override
        public void onDirCheck(String dir) {
        }

        @Override
        public void onFileCheck(File file) {
            checkedFiles.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
