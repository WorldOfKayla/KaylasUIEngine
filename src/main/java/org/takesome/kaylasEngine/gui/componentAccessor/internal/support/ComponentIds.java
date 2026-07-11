package org.takesome.kaylasEngine.gui.componentAccessor.internal.support;

/**
 * Shared normalization rules for global and composite-local component identifiers.
 *
 * <p>This type is public only so sibling internal packages can share one canonical implementation;
 * it is not part of the supported component-accessor API.</p>
 */
public final class ComponentIds {
    private ComponentIds() {
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String qualify(String scopeId, String localId) {
        String scope = normalize(scopeId);
        String local = normalize(localId);
        if (scope == null || local == null) {
            throw new IllegalArgumentException("scopeId and localId must not be blank");
        }
        if (local.equals(scope) || local.startsWith(scope + ".")) {
            return local;
        }
        return scope + "." + local;
    }
}
