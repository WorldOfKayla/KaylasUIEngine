package org.takesome.kaylasEngine.fileLoader.hash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central streaming file hasher used by download validation and library integrity checks.
 *
 * <p>Symbolic links are never followed. A file that changes while it is being read is rejected,
 * and comparisons use constant-time byte-array equality.</p>
 */
public final class FileHasher {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private FileHasher() {
    }

    public static String hash(Path file, FileHashAlgorithm algorithm) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(algorithm, "algorithm");

        Path normalizedFile = file.toAbsolutePath().normalize();
        BasicFileAttributes before = readRegularFileAttributes(normalizedFile);
        MessageDigest digest = newDigest(algorithm);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        try (SeekableByteChannel channel = Files.newByteChannel(normalizedFile, options)) {
            while (channel.read(buffer) >= 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Interrupted while hashing file: " + normalizedFile);
                }
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }

        BasicFileAttributes after = readRegularFileAttributes(normalizedFile);
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("File changed while its hash was being calculated: " + normalizedFile);
        }

        return HEX_FORMAT.formatHex(digest.digest());
    }

    public static String hash(byte[] data, FileHashAlgorithm algorithm) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(algorithm, "algorithm");
        return HEX_FORMAT.formatHex(newDigest(algorithm).digest(data));
    }

    public static String hashUtf8(String value, FileHashAlgorithm algorithm) {
        Objects.requireNonNull(value, "value");
        return hash(value.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    public static boolean matches(Path file, String expectedHash) throws IOException {
        ExpectedHash expected = parseExpectedHash(expectedHash);
        String actualHex = hash(file, expected.algorithm());
        byte[] actual = HEX_FORMAT.parseHex(actualHex);
        byte[] expectedBytes = HEX_FORMAT.parseHex(expected.hex());
        return MessageDigest.isEqual(expectedBytes, actual);
    }

    public static FileHashAlgorithm algorithmFor(String expectedHash) {
        return parseExpectedHash(expectedHash).algorithm();
    }

    static ExpectedHash parseExpectedHash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Expected file hash is missing");
        }

        String normalized = value.trim();
        FileHashAlgorithm algorithm;
        String hex;
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            if (normalized.indexOf(':', separator + 1) >= 0) {
                throw new IllegalArgumentException("Expected file hash contains more than one algorithm separator");
            }
            algorithm = FileHashAlgorithm.fromPrefix(normalized.substring(0, separator));
            hex = normalized.substring(separator + 1).trim();
        } else {
            hex = normalized;
            algorithm = FileHashAlgorithm.fromHexLength(hex.length());
        }

        if (hex.length() != algorithm.hexLength() || !HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException(
                    "Expected " + algorithm + " hash must contain exactly "
                            + algorithm.hexLength() + " hexadecimal characters"
            );
        }
        return new ExpectedHash(algorithm, hex.toLowerCase(java.util.Locale.ROOT));
    }

    private static BasicFileAttributes readRegularFileAttributes(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("Hash target is not a regular file: " + file);
        }
        return attributes;
    }

    private static MessageDigest newDigest(FileHashAlgorithm algorithm) {
        try {
            return MessageDigest.getInstance(algorithm.jcaName());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Required digest algorithm is unavailable: " + algorithm.jcaName(), error);
        }
    }

    record ExpectedHash(FileHashAlgorithm algorithm, String hex) {
    }
}
