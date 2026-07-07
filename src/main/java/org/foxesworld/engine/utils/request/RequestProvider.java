package org.foxesworld.engine.utils.request;

import org.foxesworld.engine.Engine;

import java.util.concurrent.CompletableFuture;

/**
 * Common contract for every request transport provider.
 */
public interface RequestProvider {
    RequestProviderType type();

    boolean supports(RequestOptions options);

    CompletableFuture<RequestResponse> send(Engine engine, RequestOptions options);
}
