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
import java.util.Enumeration;
import java.util.LinkedList;
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

    @SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
    public void downloader(String downloadFile, String savePath) {
        File parentDir = new File(savePath).getParentFile();
        if (parentDir != null && !parentDir.isDirectory()) {
            parentDir.mkdirs();
        }

        HttpURLConnection httpConnection = null;
        try {
            URL url = resolveDownloadUrl(downloadFile);
            httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setDoOutput(false);
            httpConnection.setUseCaches(false);
            httpConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            httpConnection.setReadTimeout(READ_TIMEOUT_MS);
            httpConnection.setRequestMethod("GET");
            this.setRequestProperties(httpConnection, engine.getEngineData().getHttPconf().getRequestProperties());

            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = new BufferedInputStream(httpConnection.getInputStream(), BUFFER_SIZE);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(savePath), BUFFER_SIZE)) {
                int read;
                while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                    out.write(buffer, 0, read);
                    long current = downloaded.addAndGet(read);
                    updateProgress(current, false);
                }
            }
            updateProgress(downloaded.get(), false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
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

    @SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
    public void unpack(String path, File dir_to) {
        File fileZip = new File(path);
        try (ZipFile zip = new ZipFile(path, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            LinkedList<ZipEntry> zfiles = new LinkedList<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    new File(dir_to + File.separator + entry.getName()).mkdirs();
                } else {
                    zfiles.add(entry);
                }
            }
            for (ZipEntry entry : zfiles) {
                File outFile = new File(dir_to, entry.getName());
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = new FileOutputStream(outFile)) {
                    if (!outFile.getParentFile().exists()) {
                        outFile.getParentFile().mkdirs();
                    }
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        fileZip.delete();
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
