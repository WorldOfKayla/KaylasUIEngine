package org.foxesworld.engine.utils.request;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

final class RequestEncoding {
    private RequestEncoding() {
    }

    static String formEncode(Map<String, Object> parameters) {
        StringJoiner joiner = new StringJoiner("&");
        if (parameters != null) {
            parameters.forEach((key, value) -> {
                if (key != null && value != null) {
                    joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                            + "="
                            + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
                }
            });
        }
        return joiner.toString();
    }

    static URI appendQuery(URI uri, Map<String, Object> parameters) {
        String encoded = formEncode(parameters);
        if (encoded.isEmpty()) {
            return uri;
        }
        String existingQuery = uri.getRawQuery();
        String mergedQuery = existingQuery == null || existingQuery.isEmpty()
                ? encoded
                : existingQuery + "&" + encoded;
        return URI.create(new StringBuilder()
                .append(uri.getScheme()).append("://")
                .append(uri.getRawAuthority())
                .append(uri.getRawPath() == null ? "" : uri.getRawPath())
                .append('?').append(mergedQuery)
                .append(uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment())
                .toString());
    }
}
