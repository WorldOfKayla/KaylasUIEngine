package org.takesome.kaylasEngine.resources;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.service.ExecutorServiceProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * Engine-wide asynchronous image cache with memory, disk, and in-flight request deduplication.
 *
 * <p>Images are keyed by the complete source URI and transformation variant rather than by the
 * remote filename. This prevents collisions between different servers and avoids downloading or
 * transforming the same image more than once while a request is already running.</p>
 */
public final class AsyncImageCache {
    private static final int DEFAULT_MEMORY_ENTRIES = 128;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    private static final AtomicBoolean IMAGE_IO_PLUGINS_SCANNED = new AtomicBoolean();

    private final ExecutorServiceProvider executorServiceProvider;
    private final ConcurrentMap<String, CompletableFuture<BufferedImage>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> memoryCache;

    public AsyncImageCache(ExecutorServiceProvider executorServiceProvider) {
        this(executorServiceProvider, DEFAULT_MEMORY_ENTRIES);
    }

    public AsyncImageCache(ExecutorServiceProvider executorServiceProvider, int maxMemoryEntries) {
        this.executorServiceProvider = Objects.requireNonNull(executorServiceProvider, "executorServiceProvider");
        int capacity = Math.max(8, maxMemoryEntries);
        this.memoryCache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                return size() > capacity;
            }
        });
    }

    public CompletableFuture<BufferedImage> load(
            URI source,
            Path cacheDirectory,
            String variant,
            UnaryOperator<BufferedImage> transformer
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        UnaryOperator<BufferedImage> safeTransformer = transformer == null ? UnaryOperator.identity() : transformer;
        String normalizedVariant = variant == null || variant.isBlank() ? "original" : variant.trim();
        Path normalizedCacheDirectory = cacheDirectory.toAbsolutePath().normalize();
        String diskKey = sha256(source.normalize() + "|" + normalizedVariant);
        String memoryKey = normalizedCacheDirectory + "|" + diskKey;

        BufferedImage memoryImage = memoryCache.get(memoryKey);
        if (memoryImage != null) {
            return CompletableFuture.completedFuture(memoryImage);
        }

        CompletableFuture<BufferedImage> pending = new CompletableFuture<>();
        CompletableFuture<BufferedImage> existing = inFlight.putIfAbsent(memoryKey, pending);
        if (existing != null) {
            return existing;
        }

        executorServiceProvider.supplyAsyncQuietly(
                () -> loadOrDownload(
                        source,
                        normalizedCacheDirectory,
                        diskKey,
                        memoryKey,
                        safeTransformer
                ),
                "image-cache-" + diskKey.substring(0, 12)
        ).whenComplete((image, error) -> {
            try {
                if (error == null) {
                    pending.complete(image);
                } else {
                    pending.completeExceptionally(error);
                }
            } finally {
                inFlight.remove(memoryKey, pending);
            }
        });
        return pending;
    }

    public void clearMemory() {
        memoryCache.clear();
    }

    public int memoryEntryCount() {
        return memoryCache.size();
    }

    public int inFlightRequestCount() {
        return inFlight.size();
    }

    private BufferedImage loadOrDownload(
            URI source,
            Path cacheDirectory,
            String diskKey,
            String memoryKey,
            UnaryOperator<BufferedImage> transformer
    ) throws IOException {
        Path cacheFile = cacheDirectory.resolve(diskKey + ".png");
        BufferedImage cached = readCachedImage(cacheFile);
        if (cached != null) {
            memoryCache.put(memoryKey, cached);
            return cached;
        }

        BufferedImage downloaded = download(source);
        BufferedImage transformed = Objects.requireNonNull(
                transformer.apply(downloaded),
                "Image transformer returned null for " + source
        );
        writeAtomically(cacheFile, transformed);
        memoryCache.put(memoryKey, transformed);
        return transformed;
    }

    private BufferedImage readCachedImage(Path cacheFile) {
        if (!Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(cacheFile.toFile());
            if (image != null) {
                return image;
            }
            Files.deleteIfExists(cacheFile);
        } catch (IOException error) {
            Engine.LOGGER.warn("Unable to read cached image {}: {}", cacheFile, error.getMessage());
            try {
                Files.deleteIfExists(cacheFile);
            } catch (IOException ignored) {
                Engine.LOGGER.debug("Unable to remove invalid cached image {}", cacheFile);
            }
        }
        return null;
    }

    private BufferedImage download(URI source) throws IOException {
        ensureImageIoPlugins();
        URLConnection connection = source.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(true);
        connection.setRequestProperty("User-Agent", "KaylasUIEngine/AsyncImageCache");
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8,*/*;q=0.1");

        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
            int status = http.getResponseCode();
            if (status < 200 || status >= 300) {
                http.disconnect();
                throw new IOException("Image request returned HTTP " + status + " for " + source);
            }
        }

        String contentType = normalizeContentType(connection.getContentType());
        long declaredLength = connection.getContentLengthLong();
        if (declaredLength > MAX_IMAGE_BYTES) {
            disconnect(connection);
            throw new IOException("Image response is too large (" + declaredLength + " bytes) for " + source);
        }

        byte[] payload;
        try (InputStream input = connection.getInputStream()) {
            payload = readBounded(input, MAX_IMAGE_BYTES);
        } finally {
            disconnect(connection);
        }
        if (payload.length == 0) {
            throw new IOException("Empty image response from " + source);
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(payload)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException(
                        "Unsupported image response from " + source
                                + " (contentType=" + contentType + ", bytes=" + payload.length + ")"
                );
            }
            Engine.LOGGER.debug(
                    "Decoded remote image {} as {}x{} (contentType={}, bytes={})",
                    source,
                    image.getWidth(),
                    image.getHeight(),
                    contentType,
                    payload.length
            );
            return image;
        }
    }

    private static void ensureImageIoPlugins() {
        if (IMAGE_IO_PLUGINS_SCANNED.compareAndSet(false, true)) {
            ImageIO.scanForPlugins();
            boolean webpAvailable = ImageIO.getImageReadersByFormatName("webp").hasNext();
            if (Engine.LOGGER != null) {
                if (webpAvailable) {
                    Engine.LOGGER.debug("ImageIO WebP reader registered.");
                } else {
                    Engine.LOGGER.warn("ImageIO WebP reader is unavailable; WebP resources will use caller fallbacks.");
                }
            }
        }
    }

    private static byte[] readBounded(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(64 * 1024, maximumBytes));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maximumBytes) {
                throw new IOException("Image response exceeds " + maximumBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "unknown";
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static void disconnect(URLConnection connection) {
        if (connection instanceof HttpURLConnection http) {
            http.disconnect();
        }
    }

    private void writeAtomically(Path cacheFile, BufferedImage image) throws IOException {
        Files.createDirectories(cacheFile.getParent());
        Path tempFile = Files.createTempFile(cacheFile.getParent(), cacheFile.getFileName().toString(), ".tmp");
        try {
            if (!ImageIO.write(image, "PNG", tempFile.toFile())) {
                throw new IOException("No PNG writer is available.");
            }
            try {
                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                result.append(Character.forDigit((element >>> 4) & 0x0F, 16));
                result.append(Character.forDigit(element & 0x0F, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
