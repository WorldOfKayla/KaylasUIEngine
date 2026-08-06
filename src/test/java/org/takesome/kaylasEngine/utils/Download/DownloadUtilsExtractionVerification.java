package org.takesome.kaylasEngine.utils.Download;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Executable regression for extraction percentage callbacks. */
public final class DownloadUtilsExtractionVerification {
    private DownloadUtilsExtractionVerification() {
    }

    public static void main(String[] args) throws Exception {
        Engine.LOGGER = LogManager.getLogger(DownloadUtilsExtractionVerification.class);
        Path root = Files.createTempDirectory("download-extraction-progress-");
        try {
            Path archive = root.resolve("payload.zip");
            Path destination = root.resolve("output");
            createArchive(archive);

            List<Integer> progress = new ArrayList<>();
            DownloadUtils utils = new DownloadUtils(null);
            utils.unpack(
                    archive.toString(),
                    destination.toFile(),
                    (percent, entryName) -> progress.add(percent)
            );

            require(!progress.isEmpty(), "extraction did not publish progress");
            require(progress.get(0) == 0, "extraction did not start at 0: " + progress);
            require(progress.get(progress.size() - 1) == 100,
                    "extraction did not finish at 100: " + progress);
            for (int index = 1; index < progress.size(); index++) {
                require(progress.get(index) >= progress.get(index - 1),
                        "extraction progress regressed: " + progress);
            }
            require(Files.readString(destination.resolve("alpha.txt"), StandardCharsets.UTF_8)
                            .equals("alpha".repeat(4096)),
                    "alpha.txt content changed during extraction");
            require(Files.readString(destination.resolve("nested/beta.txt"), StandardCharsets.UTF_8)
                            .equals("beta".repeat(8192)),
                    "beta.txt content changed during extraction");
            require(!Files.exists(archive), "source archive was not removed after unpack");

            System.out.println("Download extraction progress verification passed: " + progress);
        } finally {
            deleteRecursively(root);
        }
    }

    private static void createArchive(Path archive) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "alpha.txt", "alpha".repeat(4096));
            write(output, "nested/beta.txt", "beta".repeat(8192));
        }
    }

    private static void write(ZipOutputStream output, String name, String value) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
