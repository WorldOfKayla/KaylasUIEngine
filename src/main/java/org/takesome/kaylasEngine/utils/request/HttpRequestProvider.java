package org.takesome.kaylasEngine.utils.request;

import org.takesome.kaylasEngine.Engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP/HTTPS request provider backed by JDK HttpClient.
 */
public final class HttpRequestProvider implements RequestProvider {
    private final HttpClient httpClient;

    public HttpRequestProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public RequestProviderType type() {
        return RequestProviderType.HTTP;
    }

    @Override
    public boolean supports(RequestOptions options) {
        RequestProviderType type = options.getProviderType();
        return type == RequestProviderType.AUTO || type == RequestProviderType.HTTP;
    }

    @Override
    public CompletableFuture<RequestResponse> send(Engine engine, RequestOptions options) {
        URI uri = options.resolveUri(engine);
        String method = options.getMethod().toUpperCase(Locale.ROOT);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(safeTimeout(options.getTimeout()));
        options.getHeaders().forEach(builder::header);

        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.uri(RequestEncoding.appendQuery(uri, options.getParameters()))
                    .method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String body = options.getBody() != null
                    ? options.getBody()
                    : RequestEncoding.formEncode(options.getParameters());
            if (!options.getHeaders().containsKey("Content-Type")) {
                builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            }
            builder.uri(uri).method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new RequestResponse(
                        RequestProviderType.HTTP,
                        response.uri(),
                        response.statusCode(),
                        response.body(),
                        response.headers().map()
                ));
    }

    private Duration safeTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(30)
                : timeout;
    }
}
