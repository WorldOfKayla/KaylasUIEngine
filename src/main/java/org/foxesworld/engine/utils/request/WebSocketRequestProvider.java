package org.foxesworld.engine.utils.request;

import org.foxesworld.engine.Engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * One-shot WS/WSS request provider.
 *
 * <p>The provider sends the configured text payload and completes on the first text message by
 * default. This fits launcher-style RPC/request flows while keeping the API transport-neutral.</p>
 */
public final class WebSocketRequestProvider implements RequestProvider {
    private static final int NORMAL_CLOSE = 1000;

    private final RequestProviderType providerType;
    private final HttpClient httpClient;

    public WebSocketRequestProvider(RequestProviderType providerType, HttpClient httpClient) {
        if (!providerType.isWebSocket()) {
            throw new IllegalArgumentException("WebSocket provider type expected: " + providerType);
        }
        this.providerType = providerType;
        this.httpClient = httpClient;
    }

    @Override
    public RequestProviderType type() {
        return providerType;
    }

    @Override
    public boolean supports(RequestOptions options) {
        RequestProviderType type = options.getProviderType();
        return type == RequestProviderType.AUTO || type == providerType;
    }

    @Override
    public CompletableFuture<RequestResponse> send(Engine engine, RequestOptions options) {
        URI uri = options.resolveUri(engine);
        Duration timeout = options.getTimeout() == null ? Duration.ofSeconds(30) : options.getTimeout();
        CompletableFuture<RequestResponse> responseFuture = new CompletableFuture<>();
        String payload = options.getBody() != null
                ? options.getBody()
                : RequestEncoding.formEncode(options.getParameters());

        WebSocket.Builder builder = httpClient.newWebSocketBuilder().connectTimeout(timeout);
        options.getHeaders().forEach(builder::header);

        builder.buildAsync(uri, new ResponseListener(responseFuture, options, uri))
                .thenCompose(webSocket -> webSocket.sendText(payload, true)
                        .thenApply(ignored -> webSocket))
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    responseFuture.completeExceptionally(throwable);
                    return null;
                });

        return responseFuture;
    }

    private final class ResponseListener implements WebSocket.Listener {
        private final CompletableFuture<RequestResponse> responseFuture;
        private final RequestOptions options;
        private final URI uri;
        private final StringBuilder messageBuffer = new StringBuilder();

        private ResponseListener(CompletableFuture<RequestResponse> responseFuture, RequestOptions options, URI uri) {
            this.responseFuture = responseFuture;
            this.options = options;
            this.uri = uri;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            webSocket.request(1);
            if (last && !responseFuture.isDone()) {
                responseFuture.complete(new RequestResponse(
                        providerType,
                        uri,
                        101,
                        messageBuffer.toString(),
                        Collections.emptyMap()
                ));
                if (options.isCloseWebSocketAfterFirstMessage()) {
                    return webSocket.sendClose(NORMAL_CLOSE, "request completed");
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            responseFuture.completeExceptionally(error);
        }
    }
}
