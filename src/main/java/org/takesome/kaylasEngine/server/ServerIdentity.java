package org.takesome.kaylasEngine.server;

import java.util.Locale;
import java.util.Set;

/**
 * Shared server identity resolver used by the engine and launcher integration layer.
 *
 * <p>Backends may send either a dedicated {@code coreType} or older payloads where the
 * {@code client} field contains the core name. This class normalizes that ambiguity in one place
 * so launcher, downloader and path builders do not each carry their own branching rules.</p>
 */
public final class ServerIdentity {
    public static final String DEFAULT_CORE_TYPE = "Vanilla";
    public static final String DEFAULT_CLIENT = "Default";

    private static final Set<String> KNOWN_CORE_KEYS = Set.of(
            "vanilla",
            "forge",
            "fabric",
            "quilt",
            "neoforge",
            "runtime"
    );

    private ServerIdentity() {
    }

    public static String coreType(ServerAttributes server) {
        if (server == null) {
            return DEFAULT_CORE_TYPE;
        }

        String explicitCoreType = safe(server.getCoreType());
        if (!explicitCoreType.isBlank()) {
            return explicitCoreType;
        }

        String client = safe(server.getClient());
        if (isKnownCoreType(client)) {
            return client;
        }

        return DEFAULT_CORE_TYPE;
    }

    public static String clientName(ServerAttributes server) {
        if (server == null) {
            return DEFAULT_CLIENT;
        }

        String client = safe(server.getClient());
        if (!client.isBlank() && !isKnownCoreType(client)) {
            return client;
        }

        String serverName = safe(server.getServerName());
        return serverName.isBlank() ? DEFAULT_CLIENT : serverName;
    }

    public static String overlayClient(ServerAttributes server, String fallbackClient) {
        if (server != null) {
            String client = safe(server.getClient());
            if (!client.isBlank() && !isKnownCoreType(client)) {
                return client;
            }
        }

        String fallback = safe(fallbackClient);
        return fallback.isBlank() || isKnownCoreType(fallback) ? null : fallback;
    }

    public static boolean isKnownCoreType(String value) {
        return KNOWN_CORE_KEYS.contains(normalizeCoreKey(value));
    }

    public static String normalizeCoreKey(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String suffixAfterDash(String value) {
        String safeValue = safe(value);
        int dash = safeValue.lastIndexOf('-');
        if (dash < 0 || dash + 1 >= safeValue.length()) {
            return "";
        }
        return safeValue.substring(dash + 1).trim();
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static String safePathSegment(String value, String fallback) {
        String resolved = safe(value);
        if (resolved.isBlank()) {
            resolved = safe(fallback);
        }
        if (resolved.isBlank()) {
            resolved = DEFAULT_CLIENT;
        }

        String sanitized = resolved
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_")
                .replaceAll("\\s+", " ")
                .trim();
        while (sanitized.endsWith(".") || sanitized.endsWith(" ")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized.isBlank() ? DEFAULT_CLIENT : sanitized;
    }
}
