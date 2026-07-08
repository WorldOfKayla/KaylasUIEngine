package org.takesome.kaylasEngine.fileLoader;

import com.google.gson.Gson;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.utils.HTTP.HTTPrequest;
import org.takesome.kaylasEngine.utils.HTTP.HttpParam;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * FileFetcher is responsible for fetching a list of file attributes from the server
 * using an asynchronous HTTP POST request.
 * <p>
 * It leverages annotations to automatically include request parameters and utilizes
 * the modern CompletableFuture-based API for asynchronous operations.
 * </p>
 */
public class FileFetcher extends HTTPrequest {

    @HttpParam
    private final String sysRequest = "loadFiles";
    @HttpParam
    private String version, client, platform;

    /**
     * Constructs a new FileFetcher instance.
     *
     * @param engine the Engine instance used for configuration and logging.
     */
    public FileFetcher(Engine engine) {
        super(engine, "POST");
    }

    /**
     * Asynchronously fetches the download list.
     * <p>
     * The method sets the required parameters (version, client, platform) and sends an HTTP
     * request. The server response is parsed from JSON into an array of {@code FileAttributes}.
     * </p>
     *
     * @param client  the client identifier
     * @param version the version identifier
     * @param platform the platform identifier as an integer
     * @return a CompletableFuture that resolves to an array of FileAttributes on success,
     *         or completes exceptionally if an error occurs.
     */
    public CompletableFuture<FileAttributes[]> fetchDownloadList(String client, String version, int platform) {
        this.version = version;
        this.client = client;
        this.platform = String.valueOf(platform);

        return sendAsyncCF(Collections.emptyMap()).thenApply(response -> new Gson().fromJson(response, FileAttributes[].class));
    }
}
