package org.takesome.kaylasEngine.utils;

import java.util.Map;
import java.util.Objects;

/** Resolves simple {@code ${name}} placeholders without regex allocation or replacement escaping. */
public final class StringTemplateResolver {
    private StringTemplateResolver() {
    }

    public static String resolve(String template, Map<String, ?> variables) {
        if (template == null || template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }

        int marker = template.indexOf("${");
        if (marker < 0) {
            return template;
        }

        StringBuilder result = new StringBuilder(template.length() + 16);
        int cursor = 0;
        while (marker >= 0) {
            int closingBrace = template.indexOf('}', marker + 2);
            if (closingBrace < 0) {
                break;
            }

            result.append(template, cursor, marker);
            String variableName = template.substring(marker + 2, closingBrace);
            Object replacement = variables.get(variableName);
            if (replacement == null) {
                result.append(template, marker, closingBrace + 1);
            } else {
                result.append(replacement);
            }

            cursor = closingBrace + 1;
            marker = template.indexOf("${", cursor);
        }
        result.append(template, cursor, template.length());
        return result.toString();
    }

    public static Map<String, String> resolveValues(Map<String, String> templates, Map<String, ?> variables) {
        Objects.requireNonNull(templates, "templates");
        return templates.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> resolve(entry.getKey(), variables),
                entry -> resolve(entry.getValue(), variables)
        ));
    }
}
