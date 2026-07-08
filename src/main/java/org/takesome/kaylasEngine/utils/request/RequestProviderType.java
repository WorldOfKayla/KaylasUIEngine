package org.takesome.kaylasEngine.utils.request;

import java.net.URI;
import java.util.Locale;

/**
 * Transport/provider selector for engine requests.
 */
public enum RequestProviderType {
    AUTO,
    HTTP,
    WS,
    WSS;

    public static RequestProviderType fromUri(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return HTTP;
        }
        return switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case "ws" -> WS;
            case "wss" -> WSS;
            case "http", "https" -> HTTP;
            default -> HTTP;
        };
    }

    public boolean isWebSocket() {
        return this == WS || this == WSS;
    }
}
