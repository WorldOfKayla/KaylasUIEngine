package org.takesome.kaylasEngine.utils.request;

import org.takesome.kaylasEngine.Engine;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transport-agnostic request description.
 */
public final class RequestOptions {
    private final RequestProviderType providerType;
    private final URI uri;
    private final String endpoint;
    private final String method;
    private final String body;
    private final Map<String, Object> parameters;
    private final Map<String, String> headers;
    private final Duration timeout;
    private final boolean closeWebSocketAfterFirstMessage;

    private RequestOptions(Builder builder) {
        this.providerType = Objects.requireNonNull(builder.providerType, "providerType");
        this.uri = builder.uri;
        this.endpoint = builder.endpoint;
        this.method = builder.method == null ? "GET" : builder.method.toUpperCase(Locale.ROOT);
        this.body = builder.body;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.timeout = builder.timeout == null ? Duration.ofSeconds(30) : builder.timeout;
        this.closeWebSocketAfterFirstMessage = builder.closeWebSocketAfterFirstMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public RequestProviderType getProviderType() {
        return providerType;
    }

    public URI getUri() {
        return uri;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getMethod() {
        return method;
    }

    public String getBody() {
        return body;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isCloseWebSocketAfterFirstMessage() {
        return closeWebSocketAfterFirstMessage;
    }

    public RequestProviderType resolveProviderType(Engine engine) {
        if (providerType != RequestProviderType.AUTO) {
            return providerType;
        }
        return RequestProviderType.fromUri(resolveUri(engine));
    }

    public URI resolveUri(Engine engine) {
        URI resolved = uri;
        if (resolved == null) {
            String bindUrl = engine.getEngineData().getBindUrl();
            if (bindUrl == null || bindUrl.isBlank()) {
                throw new IllegalStateException("Request URI is not set and engine bindUrl is empty");
            }
            resolved = URI.create(bindUrl);
        }
        if (endpoint != null && !endpoint.isBlank()) {
            resolved = resolved.resolve(endpoint);
        }

        RequestProviderType selectedProvider = providerType == RequestProviderType.AUTO
                ? RequestProviderType.fromUri(resolved)
                : providerType;
        if (selectedProvider.isWebSocket()) {
            resolved = withWebSocketScheme(resolved, selectedProvider);
        }
        return resolved;
    }

    private URI withWebSocketScheme(URI source, RequestProviderType selectedProvider) {
        String targetScheme = selectedProvider == RequestProviderType.WSS ? "wss" : "ws";
        String scheme = source.getScheme();
        if (targetScheme.equalsIgnoreCase(scheme)) {
            return source;
        }
        if (scheme == null || scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
            return URI.create(targetScheme + ":" + source.getSchemeSpecificPart());
        }
        return source;
    }

    public static final class Builder {
        private RequestProviderType providerType = RequestProviderType.AUTO;
        private URI uri;
        private String endpoint;
        private String method = "GET";
        private String body;
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Duration timeout = Duration.ofSeconds(30);
        private boolean closeWebSocketAfterFirstMessage = true;

        private Builder() {
        }

        public Builder providerType(RequestProviderType providerType) {
            this.providerType = Objects.requireNonNull(providerType, "providerType");
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri == null || uri.isBlank() ? null : URI.create(uri);
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder parameter(String key, Object value) {
            if (key != null && value != null) {
                this.parameters.put(key, value);
            }
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            if (parameters != null) {
                parameters.forEach(this::parameter);
            }
            return this;
        }

        public Builder header(String key, String value) {
            if (key != null && value != null) {
                this.headers.put(key, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder closeWebSocketAfterFirstMessage(boolean closeWebSocketAfterFirstMessage) {
            this.closeWebSocketAfterFirstMessage = closeWebSocketAfterFirstMessage;
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }
}
