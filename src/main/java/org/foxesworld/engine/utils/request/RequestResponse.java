package org.foxesworld.engine.utils.request;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Immutable request result returned by all request providers.
 */
public record RequestResponse(
        RequestProviderType providerType,
        URI uri,
        int statusCode,
        String body,
        Map<String, List<String>> headers
) {
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
