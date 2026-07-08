package org.takesome.kaylasEngine.utils.request;

import org.takesome.kaylasEngine.Engine;

import java.util.concurrent.CompletableFuture;

/**
 * Common contract for every request transport provider.
 */
public interface RequestProvider {
    RequestProviderType type();

    boolean supports(RequestOptions options);

    CompletableFuture<RequestResponse> send(Engine engine, RequestOptions options);
}
