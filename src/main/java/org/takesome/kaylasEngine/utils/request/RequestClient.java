package org.takesome.kaylasEngine.utils.request;

import org.takesome.kaylasEngine.Engine;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Engine-level request dispatcher with pluggable HTTP/WS/WSS providers.
 */
public final class RequestClient {
    private final Engine engine;
    private final Map<RequestProviderType, RequestProvider> providers = new EnumMap<>(RequestProviderType.class);

    public RequestClient(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .executor(engine.getExecutorServiceProvider().getExecutorService())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        registerProvider(new HttpRequestProvider(httpClient));
        registerProvider(new WebSocketRequestProvider(RequestProviderType.WS, httpClient));
        registerProvider(new WebSocketRequestProvider(RequestProviderType.WSS, httpClient));
    }

    public void registerProvider(RequestProvider provider) {
        providers.put(provider.type(), provider);
    }

    public CompletableFuture<RequestResponse> send(RequestOptions options) {
        RequestOptions safeOptions = Objects.requireNonNull(options, "options");
        RequestProviderType selectedType = safeOptions.resolveProviderType(engine);
        RequestProvider provider = providers.get(selectedType);
        if (provider == null || !provider.supports(safeOptions)) {
            return CompletableFuture.failedFuture(new IllegalStateException("No request provider registered for " + selectedType));
        }
        Engine.LOGGER.debug("Dispatching {} request to {}", selectedType, safeOptions.resolveUri(engine));
        return provider.send(engine, safeOptions);
    }

    public CompletableFuture<RequestResponse> sendHttp(RequestOptions options) {
        return send(RequestOptions.builder()
                .providerType(RequestProviderType.HTTP)
                .uri(options.getUri())
                .endpoint(options.getEndpoint())
                .method(options.getMethod())
                .body(options.getBody())
                .parameters(options.getParameters())
                .headers(options.getHeaders())
                .timeout(options.getTimeout())
                .build());
    }
}
