package org.takesome.kaylasEngine.fileLoader;

/**
 * Interface for obtaining a list of file attributes.
 */
public interface IFileFetcher {
    /**
     * Fetches the list of file attributes to download.
     *
     * @param client       the client name
     * @param version      the client version
     * @param platformCode platform code (for example: 1 for Windows, 2 for Mac, etc.)
     * @return a {@link java.util.concurrent.CompletableFuture} containing an array of {@code FileAttributes}
     */
    java.util.concurrent.CompletableFuture<FileAttributes[]> fetchDownloadList(String client, String version, int platformCode);
}
