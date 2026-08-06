package org.takesome.kaylasEngine.utils;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.fileLoader.hash.FileHashAlgorithm;
import org.takesome.kaylasEngine.fileLoader.hash.FileHasher;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Compatibility adapters for older hashing call sites.
 *
 * <p>New code should use {@link FileHasher} directly.</p>
 */
@SuppressWarnings("unused")
public final class HashUtils {
    private HashUtils() {
    }

    /**
     * @deprecated use {@link FileHasher#hash(Path, FileHashAlgorithm)}.
     */
    @Deprecated
    public static String md5(String filename) {
        try {
            return FileHasher.hash(Path.of(filename), FileHashAlgorithm.MD5);
        } catch (IOException | RuntimeException error) {
            Engine.LOGGER.warn("Unable to calculate MD5 for {}", filename, error);
            return "0";
        }
    }

    public static String sha1String(String input) {
        return FileHasher.hashUtf8(input, FileHashAlgorithm.SHA1);
    }

    /**
     * @deprecated use {@link FileHasher#hash(Path, FileHashAlgorithm)}.
     */
    @Deprecated
    public static String calculateSHA1(String filePath) {
        try {
            return FileHasher.hash(Path.of(filePath), FileHashAlgorithm.SHA1);
        } catch (IOException | RuntimeException error) {
            Engine.LOGGER.error("Unable to calculate SHA-1 for {}", filePath, error);
            return "";
        }
    }
}
