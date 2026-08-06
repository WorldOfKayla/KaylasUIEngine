package org.takesome.kaylasEngine.fileLoader;

import org.takesome.kaylasEngine.fileLoader.hash.FileHasher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Default validator for files described by launcher metadata.
 *
 * <p>Bare MD5, SHA-1 and SHA-256 values are supported. Metadata may also use an explicit
 * {@code md5:}, {@code sha1:} or {@code sha256:} prefix. Symbolic links and files that change
 * during hashing are rejected.</p>
 */
public class FileValidator implements IFileValidator {
    @Override
    public boolean isInvalidFile(File file, String expectedHash, long expectedSize) {
        if (file == null || expectedSize < 0) {
            return true;
        }

        Path path = file.toPath().toAbsolutePath().normalize();
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (!attributes.isRegularFile()
                    || attributes.isSymbolicLink()
                    || attributes.size() != expectedSize) {
                return true;
            }
            return !FileHasher.matches(path, expectedHash);
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            return true;
        }
    }
}
