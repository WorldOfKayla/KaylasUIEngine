package org.takesome.kaylasEngine.crash;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable crash-report payload exposed to application-level send listeners. */
public record CrashReportSubmission(
        String reportText,
        Map<String, String> context,
        Path localReport,
        String application,
        String engineVersion,
        Instant generatedAt
) {
    public CrashReportSubmission {
        reportText = reportText == null ? "" : reportText;
        context = context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
        application = normalize(application, "Application");
        engineVersion = normalize(engineVersion, "unknown");
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
