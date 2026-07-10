package org.takesome.kaylasEngine.resources;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.service.ExecutorServiceProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

        executorServiceProvider.supplyAsync(
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
        URLConnection connection = source.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(true);
        connection.setRequestProperty("User-Agent", "KaylasUIEngine/AsyncImageCache");
        try (InputStream input = connection.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unsupported or empty image response from " + source);
            }
            return image;
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
