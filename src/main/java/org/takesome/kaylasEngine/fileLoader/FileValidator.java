package org.takesome.kaylasEngine.fileLoader;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * Default file validator that verifies the integrity of a local file.
 * This class implements {@link IFileValidator}, allowing applications to replace it with another implementation when required.
 */
public class FileValidator implements IFileValidator {

    /**
     * Determines whether a local file is invalid relative to the expected metadata.
     *
     * @param file         local file to validate
     * @param expectedHash expected MD5 hash
     * @param expectedSize expected file size in bytes
     * @return {@code true} when the file is missing, has an unexpected size, or has a mismatched hash; otherwise {@code false}
     */
    @Override
    public boolean isInvalidFile(File file, String expectedHash, long expectedSize) {
        if (!file.exists() || file.length() != expectedSize) {
            return true;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dataBytes = new byte[1024];
            int bytesRead;

            try (FileInputStream fis = new FileInputStream(file)) {
                while ((bytesRead = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, bytesRead);
                }
            }

            byte[] digestBytes = md.digest();
            StringBuilder hexString = new StringBuilder();

            for (byte digestByte : digestBytes) {
                hexString.append(String.format("%02x", digestByte));
            }

            return !hexString.toString().equals(expectedHash);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}
