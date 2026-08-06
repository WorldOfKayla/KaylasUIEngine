package org.takesome.kaylasEngine.fileLoader.hash;

import org.takesome.kaylasEngine.fileLoader.FileValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileHasherVerification {
    private FileHasherVerification() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("kaylas-hasher-");
        try {
            Path file = directory.resolve("payload.bin");
            Files.writeString(file, "abc", StandardCharsets.UTF_8);

            assertEquals(
                    "900150983cd24fb0d6963f7d28e17f72",
                    FileHasher.hash(file, FileHashAlgorithm.MD5),
                    "MD5 digest"
            );
            assertEquals(
                    "a9993e364706816aba3e25717850c26c9cd0d89d",
                    FileHasher.hash(file, FileHashAlgorithm.SHA1),
                    "SHA-1 digest"
            );
            assertEquals(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    FileHasher.hash(file, FileHashAlgorithm.SHA256),
                    "SHA-256 digest"
            );

            assertTrue(
                    FileHasher.matches(file, "900150983CD24FB0D6963F7D28E17F72"),
                    "bare uppercase MD5 must be accepted"
            );
            assertTrue(
                    FileHasher.matches(file, "sha1:a9993e364706816aba3e25717850c26c9cd0d89d"),
                    "prefixed SHA-1 must be accepted"
            );
            assertTrue(
                    FileHasher.matches(
                            file,
                            "sha-256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                    ),
                    "prefixed SHA-256 must be accepted"
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FileHasher.matches(file, "sha256:not-hex"),
                    "malformed metadata must be rejected"
            );

            FileValidator validator = new FileValidator();
            assertTrue(
                    validator.isValidFile(file.toFile(), "md5:900150983cd24fb0d6963f7d28e17f72", 3),
                    "validator must accept matching metadata"
            );
            assertTrue(
                    validator.isInvalidFile(file.toFile(), "md5:900150983cd24fb0d6963f7d28e17f72", 4),
                    "validator must reject a size mismatch"
            );
            assertTrue(
                    validator.isInvalidFile(file.toFile(), "md5:00000000000000000000000000000000", 3),
                    "validator must reject a hash mismatch"
            );

            verifySymbolicLinkRejection(directory, file);
            System.out.println("FileHasherVerification: OK");
        } finally {
            deleteTree(directory);
        }
    }

    private static void verifySymbolicLinkRejection(Path directory, Path target) throws Exception {
        Path link = directory.resolve("payload-link.bin");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }

        FileValidator validator = new FileValidator();
        assertTrue(
                validator.isInvalidFile(link.toFile(), "900150983cd24fb0d6963f7d28e17f72", 3),
                "validator must reject symbolic links"
        );
        assertThrows(
                IOException.class,
                () -> FileHasher.hash(link, FileHashAlgorithm.MD5),
                "hasher must not follow symbolic links"
        );
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes
            ) throws IOException {
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

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
