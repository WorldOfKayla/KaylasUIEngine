package org.takesome.kaylasEngine.utils.Download;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.utils.HTTP.HTTPrequest;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("unused")
public class DownloadUtils extends HTTPrequest {
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final long UI_UPDATE_INTERVAL_MS = 100L;

    private final Engine engine;
    private JLabel progressLabel;
    private IntConsumer progressValueSetter;
    private Button cancelButton;
    private final AtomicLong downloaded = new AtomicLong(0L);
    private final AtomicLong lastUiUpdateMillis = new AtomicLong(0L);
    private final AtomicInteger lastPercent = new AtomicInteger(-1);
    private volatile long totalSize;
    private volatile long startedAtMillis;

    public DownloadUtils(Engine engine) {
        super(engine, "GET");
        this.engine = engine;
    }

    public void downloader(String downloadFile, String savePath) {
        downloader(downloadFile, savePath, -1L, null);
    }

    /**
     * Downloads into a temporary sibling file and publishes the target only after HTTP, size and
     * optional MD5 validation have succeeded.
     */
    public void downloader(String downloadFile, String savePath, long expectedSize, String expectedHash) {
        Path target = Path.of(savePath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path temporary = null;
        HttpURLConnection httpConnection = null;
        try {
            if (parent == null) {
                throw new IOException("Download target has no parent directory: " + target);
            }
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, target.getFileName() + ".", ".part");

            URL url = resolveDownloadUrl(downloadFile);
            httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setDoOutput(false);
            httpConnection.setUseCaches(false);
            httpConnection.setInstanceFollowRedirects(true);
            httpConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            httpConnection.setReadTimeout(READ_TIMEOUT_MS);
            httpConnection.setRequestMethod("GET");
            httpConnection.setRequestProperty("Accept-Encoding", "identity");
            this.setRequestProperties(httpConnection, engine.getEngineData().getHttPconf().getRequestProperties());

            int status = httpConnection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status " + status + " for " + url);
            }
            String contentType = httpConnection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                throw new IOException("Unexpected HTML response for " + url);
            }

            long contentLength = httpConnection.getContentLengthLong();
            if (expectedSize > 0L && contentLength >= 0L && contentLength != expectedSize) {
                throw new IOException("Content-Length mismatch for " + url
                        + ": expected=" + expectedSize + ", actual=" + contentLength);
            }

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            long written = 0L;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = new BufferedInputStream(httpConnection.getInputStream(), BUFFER_SIZE);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary), BUFFER_SIZE)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    md5.update(buffer, 0, read);
                    written += read;
                    updateProgress(downloaded.addAndGet(read), false);
                }
                out.flush();
            }

            if (contentLength >= 0L && written != contentLength) {
                throw new IOException("Incomplete HTTP response for " + url
                        + ": declared=" + contentLength + ", received=" + written);
            }
            if (expectedSize > 0L && written != expectedSize) {
                throw new IOException("Downloaded size mismatch for " + url
                        + ": expected=" + expectedSize + ", received=" + written);
            }

            String actualHash = HexFormat.of().formatHex(md5.digest());
            if (expectedHash != null && !expectedHash.isBlank()
                    && !actualHash.equalsIgnoreCase(expectedHash.trim())) {
                throw new IOException("Downloaded MD5 mismatch for " + url
                        + ": expected=" + expectedHash.trim() + ", actual=" + actualHash);
            }

            moveIntoPlace(temporary, target);
            temporary = null;
            updateProgress(downloaded.get(), false);
            Engine.LOGGER.debug("Download completed: target={} bytes={} md5={}", target, written, actualHash);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to download " + downloadFile + " to " + target + ": " + e.getMessage(), e);
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupError) {
                    Engine.LOGGER.warn("Unable to remove incomplete download {}: {}", temporary, cleanupError.getMessage());
                }
            }
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private URL resolveDownloadUrl(String downloadFile) throws IOException {
        if (downloadFile != null
                && (downloadFile.startsWith("http://") || downloadFile.startsWith("https://"))) {
            return new URL(downloadFile);
        }
        return new URL(engine.getEngineData().getBindUrl() + downloadFile);
    }

    private void updateProgress(long currentDownloaded, boolean force) {
        long total = totalSize;
        if (total <= 0L) {
            return;
        }

        int nextPercent = (int) Math.max(0L, Math.min(100L, currentDownloaded * 100L / total));
        long now = System.currentTimeMillis();
        int previousPercent = lastPercent.get();
        long previousUiUpdate = lastUiUpdateMillis.get();

        if (!force && nextPercent == previousPercent && now - previousUiUpdate < UI_UPDATE_INTERVAL_MS) {
            return;
        }
        if (!force && !lastUiUpdateMillis.compareAndSet(previousUiUpdate, now)) {
            return;
        }
        lastPercent.set(nextPercent);

        String progressText = formatFileSize(currentDownloaded) + " / " + formatFileSize(total);
        SwingUtilities.invokeLater(() -> {
            if (progressValueSetter != null) {
                progressValueSetter.accept(nextPercent);
            }
            if (progressLabel != null) {
                progressLabel.setText(progressText);
            }
        });
    }

    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " bytes";
        } else if (sizeInBytes < 1024 * 1024) {
            double sizeInKb = sizeInBytes / 1024.0;
            return String.format("%.2f KB", sizeInKb);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            double sizeInMb = sizeInBytes / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeInMb);
        } else {
            double sizeInGb = sizeInBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.2f GB", sizeInGb);
        }
    }

    public String getCurrentSpeedText() {
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - startedAtMillis);
        double bytesPerSecond = downloaded.get() * 1000.0 / elapsedMillis;
        if (bytesPerSecond < 1024) {
            return String.format("%.2f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSecond / 1024.0);
        }
        return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

    public void unpack(String path, File destination) {
        Path archive = Path.of(path).toAbsolutePath().normalize();
        Path target = destination.toPath().toAbsolutePath().normalize();
        try {
            Files.createDirectories(target);
            extractZip(archive, target, false);
            Files.deleteIfExists(archive);
        } catch (IOException error) {
            throw new RuntimeException("Unable to unpack archive " + archive + " into " + target, error);
        }
    }

    /**
     * Extracts a runtime into one flat, fully-qualified installation directory, stripping the
     * vendor archive's single top-level directory. The target is published atomically.
     */
    public void unpackFlatRuntime(String path, File targetDirectory) {
        Path archive = Path.of(path).toAbsolutePath().normalize();
        Path target = targetDirectory.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path temporary = null;
        try {
            if (parent == null) {
                throw new IOException("Runtime target has no parent directory: " + target);
            }
            Files.createDirectories(parent);
            temporary = Files.createTempDirectory(parent, target.getFileName() + ".extract-");
            extractZip(archive, temporary, true);

            Path javaExecutable = temporary.resolve("bin").resolve(runtimeJavaExecutableName());
            if (!Files.isRegularFile(javaExecutable)) {
                throw new IOException("Runtime archive does not contain " + javaExecutable);
            }

            deleteRecursively(target);
            moveAtomically(temporary, target);
            temporary = null;
            Files.deleteIfExists(archive);
        } catch (IOException error) {
            if (temporary != null) {
                try {
                    deleteRecursively(temporary);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw new RuntimeException("Unable to install runtime " + archive + " into " + target, error);
        }
    }

    private void extractZip(Path archive, Path destination, boolean stripSingleRoot) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            String rootPrefix = stripSingleRoot ? commonArchiveRoot(zip) : "";
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = normalizeZipEntryName(entry.getName());
                if (!rootPrefix.isEmpty() && entryName.startsWith(rootPrefix)) {
                    entryName = entryName.substring(rootPrefix.length());
                }
                if (entryName.isBlank()) {
                    continue;
                }

                Path output = destination.resolve(entryName).normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException("ZIP entry escapes extraction directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Path outputParent = output.getParent();
                if (outputParent != null) {
                    Files.createDirectories(outputParent);
                }
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE);
                     OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(output), BUFFER_SIZE)) {
                    input.transferTo(outputStream);
                }
            }
        }
    }

    private String commonArchiveRoot(ZipFile zip) throws IOException {
        String common = null;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = normalizeZipEntryName(entry.getName());
            int separator = name.indexOf('/');
            if (separator <= 0) {
                return "";
            }
            String first = name.substring(0, separator);
            if (common == null) {
                common = first;
            } else if (!common.equals(first)) {
                return "";
            }
        }
        return common == null ? "" : common + '/';
    }

    private String normalizeZipEntryName(String rawName) throws IOException {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.indexOf('\0') >= 0) {
            throw new IOException("ZIP entry contains a null byte.");
        }
        return name;
    }

    private String runtimeJavaExecutableName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }


    public void setTotalSize(long totalSize) {
        this.totalSize = Math.max(0L, totalSize);
        this.downloaded.set(0L);
        this.lastUiUpdateMillis.set(0L);
        this.lastPercent.set(-1);
        this.startedAtMillis = System.currentTimeMillis();
        updateProgress(0L, true);
    }

    public void setProgressLabel(JLabel progressLabel) {
        this.progressLabel = progressLabel;
    }

    public void setProgressBar(JProgressBar progressBar) {
        this.progressValueSetter = progressBar == null ? null : progressBar::setValue;
    }

    public void setProgressBar(ProgressBar progressBar) {
        this.progressValueSetter = progressBar == null ? null : progressBar::setValue;
    }

    public void setCancelButton(Button cancelButton) {
        this.cancelButton = cancelButton;
    }
}
