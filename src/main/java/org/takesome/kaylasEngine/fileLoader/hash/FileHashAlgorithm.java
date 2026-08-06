package org.takesome.kaylasEngine.fileLoader.hash;

import java.util.Locale;

/**
 * Digest algorithms accepted by launcher file metadata.
 */
public enum FileHashAlgorithm {
    MD5("MD5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64);

    private final String jcaName;
    private final int hexLength;

    FileHashAlgorithm(String jcaName, int hexLength) {
        this.jcaName = jcaName;
        this.hexLength = hexLength;
    }

    public String jcaName() {
        return jcaName;
    }

    public int hexLength() {
        return hexLength;
    }

    public static FileHashAlgorithm fromPrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Hash algorithm prefix is missing");
        }
        return switch (prefix.trim().toLowerCase(Locale.ROOT)) {
            case "md5" -> MD5;
            case "sha1", "sha-1" -> SHA1;
            case "sha256", "sha-256" -> SHA256;
            default -> throw new IllegalArgumentException("Unsupported file hash algorithm: " + prefix);
        };
    }

    public static FileHashAlgorithm fromHexLength(int length) {
        return switch (length) {
            case 32 -> MD5;
            case 40 -> SHA1;
            case 64 -> SHA256;
            default -> throw new IllegalArgumentException(
                    "Cannot infer file hash algorithm from " + length + " hexadecimal characters"
            );
        };
    }
}
