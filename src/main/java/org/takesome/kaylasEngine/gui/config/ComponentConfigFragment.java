package org.takesome.kaylasEngine.gui.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One ordered component configuration fragment. Blank selectors act as wildcards. */
public record ComponentConfigFragment(
        String group,
        String componentType,
        String componentId,
        int priority,
        long sequence,
        String source,
        Map<String, Object> values
) {
    public ComponentConfigFragment {
        group = normalize(group);
        componentType = normalize(componentType);
        componentId = normalize(componentId);
        source = source == null || source.isBlank() ? "runtime" : source.trim();
        values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean matches(String groupName, String type, String id) {
        return selectorMatches(group, groupName)
                && selectorMatches(componentType, type)
                && selectorMatches(componentId, id);
    }

    public int specificity() {
        int score = 0;
        if (!group.isBlank()) score += 1;
        if (!componentType.isBlank()) score += 2;
        if (!componentId.isBlank()) score += 4;
        return score;
    }

    private static boolean selectorMatches(String selector, String value) {
        return selector.isBlank() || selector.equalsIgnoreCase(normalize(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
