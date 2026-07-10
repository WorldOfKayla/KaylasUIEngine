package org.takesome.kaylasEngine.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Redacts credentials before process command lines are written to logs or crash reports. */
public final class CommandLineSanitizer {
    private static final Set<String> DEFAULT_SENSITIVE_OPTIONS = Set.of(
            "--accesstoken",
            "--access_token",
            "--auth_access_token",
            "--clienttoken",
            "--client_token"
    );

    private CommandLineSanitizer() {
    }

    public static List<String> sanitize(List<String> arguments, Collection<String> sensitiveValues) {
        return sanitize(arguments, sensitiveValues, DEFAULT_SENSITIVE_OPTIONS);
    }

    public static List<String> sanitize(
            List<String> arguments,
            Collection<String> sensitiveValues,
            Collection<String> sensitiveOptions
    ) {
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }

        Set<String> normalizedOptions = normalizeOptions(sensitiveOptions);
        List<String> secrets = sensitiveValues == null
                ? List.of()
                : sensitiveValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();

        List<String> sanitized = new ArrayList<>(arguments.size());
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            String inlineRedaction = redactInlineOption(argument, normalizedOptions);
            if (inlineRedaction != null) {
                sanitized.add(inlineRedaction);
                continue;
            }
            if (isSensitiveOption(argument, normalizedOptions)) {
                sanitized.add(argument);
                if (index + 1 < arguments.size()) {
                    sanitized.add("***");
                    index++;
                }
                continue;
            }
            sanitized.add(redactSecrets(argument, secrets));
        }
        return List.copyOf(sanitized);
    }

    private static Set<String> normalizeOptions(Collection<String> sensitiveOptions) {
        Set<String> normalizedOptions = new HashSet<>();
        if (sensitiveOptions == null) {
            return normalizedOptions;
        }
        for (String option : sensitiveOptions) {
            if (option != null && !option.isBlank()) {
                normalizedOptions.add(option.toLowerCase(Locale.ROOT));
            }
        }
        return normalizedOptions;
    }

    private static String redactInlineOption(String argument, Set<String> sensitiveOptions) {
        if (argument == null) {
            return null;
        }
        String normalized = argument.toLowerCase(Locale.ROOT);
        for (String option : sensitiveOptions) {
            if (normalized.startsWith(option + '=')) {
                int separator = argument.indexOf('=');
                return argument.substring(0, separator + 1) + "***";
            }
        }
        return null;
    }

    private static String redactSecrets(String argument, List<String> secrets) {
        String redacted = argument;
        if (redacted == null) {
            return null;
        }
        for (String secret : secrets) {
            redacted = redacted.replace(secret, "***");
        }
        return redacted;
    }

    private static boolean isSensitiveOption(String argument, Set<String> sensitiveOptions) {
        return argument != null && sensitiveOptions.contains(argument.toLowerCase(Locale.ROOT));
    }
}
