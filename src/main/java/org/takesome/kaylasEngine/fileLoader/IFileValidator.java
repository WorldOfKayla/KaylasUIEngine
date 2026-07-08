package org.takesome.kaylasEngine.fileLoader;

import java.io.File;

/**
 * Interface for validating local files.
 */
public interface IFileValidator {
    /**
     * Checks whether a file does not match the expected parameters.
     *
     * @param localFile    the local file to validate
     * @param expectedHash the expected file hash (e.g. MD5)
     * @param expectedSize the expected file size in bytes
     * @return {@code true} if the file does not match expectations (is invalid), otherwise {@code false}
     */
    boolean isInvalidFile(File localFile, String expectedHash, long expectedSize);

    /**
     * Checks whether a file is valid.
     *
     * @param localFile    the local file to validate
     * @param expectedHash the expected MD5 hash of the file
     * @param expectedSize the expected file size in bytes
     * @return {@code true} if the file is valid; {@code false} otherwise
     */
    default boolean isValidFile(File localFile, String expectedHash, long expectedSize) {
        return !isInvalidFile(localFile, expectedHash, expectedSize);
    }
}
