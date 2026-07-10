package org.takesome.kaylasEngine.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FontAwesomeIconRegistry {
    private static final String DEFAULT_ICON = "circle-question";

    private static final Map<String, Integer> ICONS = Map.ofEntries(
            Map.entry("arrow-down", 0xf063),
            Map.entry("arrow-left", 0xf060),
            Map.entry("arrow-right", 0xf061),
            Map.entry("arrow-up", 0xf062),
            Map.entry("bars", 0xf0c9),
            Map.entry("bell", 0xf0f3),
            Map.entry("bolt", 0xf0e7),
            Map.entry("book", 0xf02d),
            Map.entry("box", 0xf466),
            Map.entry("boxes-stacked", 0xf468),
            Map.entry("bug", 0xf188),
            Map.entry("calendar", 0xf133),
            Map.entry("check", 0xf00c),
            Map.entry("chevron-down", 0xf078),
            Map.entry("chevron-left", 0xf053),
            Map.entry("chevron-right", 0xf054),
            Map.entry("chevron-up", 0xf077),
            Map.entry("circle-check", 0xf058),
            Map.entry("circle-exclamation", 0xf06a),
            Map.entry("circle-info", 0xf05a),
            Map.entry("circle-question", 0xf059),
            Map.entry("circle-user", 0xf2bd),
            Map.entry("coins", 0xf51e),
            Map.entry("code", 0xf121),
            Map.entry("cube", 0xf1b2),
            Map.entry("cubes", 0xf1b3),
            Map.entry("database", 0xf1c0),
            Map.entry("download", 0xf019),
            Map.entry("file", 0xf15b),
            Map.entry("file-lines", 0xf15c),
            Map.entry("folder", 0xf07b),
            Map.entry("folder-open", 0xf07c),
            Map.entry("gauge-high", 0xf625),
            Map.entry("gear", 0xf013),
            Map.entry("gears", 0xf085),
            Map.entry("gem", 0xf3a5),
            Map.entry("globe", 0xf0ac),
            Map.entry("house", 0xf015),
            Map.entry("id-card", 0xf2c2),
            Map.entry("image", 0xf03e),
            Map.entry("info", 0xf129),
            Map.entry("key", 0xf084),
            Map.entry("link", 0xf0c1),
            Map.entry("list", 0xf03a),
            Map.entry("lock", 0xf023),
            Map.entry("magnifying-glass", 0xf002),
            Map.entry("minus", 0xf068),
            Map.entry("music", 0xf001),
            Map.entry("pause", 0xf04c),
            Map.entry("play", 0xf04b),
            Map.entry("plus", 0xf067),
            Map.entry("power-off", 0xf011),
            Map.entry("right-from-bracket", 0xf2f5),
            Map.entry("right-to-bracket", 0xf2f6),
            Map.entry("rocket", 0xf135),
            Map.entry("rotate-right", 0xf2f9),
            Map.entry("server", 0xf233),
            Map.entry("shield-halved", 0xf3ed),
            Map.entry("sliders", 0xf1de),
            Map.entry("stop", 0xf04d),
            Map.entry("terminal", 0xf120),
            Map.entry("trash", 0xf1f8),
            Map.entry("triangle-exclamation", 0xf071),
            Map.entry("unlock", 0xf09c),
            Map.entry("upload", 0xf093),
            Map.entry("user", 0xf007),
            Map.entry("users", 0xf0c0),
            Map.entry("volume-high", 0xf028),
            Map.entry("wrench", 0xf0ad),
            Map.entry("xmark", 0xf00d)
    );

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("accept", "check"),
            Map.entry("back", "arrow-left"),
            Map.entry("cancel", "xmark"),
            Map.entry("close", "xmark"),
            Map.entry("cog", "gear"),
            Map.entry("crystal", "gem"),
            Map.entry("crystals", "gem"),
            Map.entry("directory", "folder"),
            Map.entry("error", "circle-exclamation"),
            Map.entry("exit", "right-from-bracket"),
            Map.entry("game", "play"),
            Map.entry("home", "house"),
            Map.entry("launch", "play"),
            Map.entry("log-out", "right-from-bracket"),
            Map.entry("login", "right-to-bracket"),
            Map.entry("logout", "right-from-bracket"),
            Map.entry("menu", "bars"),
            Map.entry("ok", "check"),
            Map.entry("optional-mods", "sliders"),
            Map.entry("options", "gear"),
            Map.entry("question", "circle-question"),
            Map.entry("refresh", "rotate-right"),
            Map.entry("reload", "rotate-right"),
            Map.entry("settings", "gear"),
            Map.entry("sign-in", "right-to-bracket"),
            Map.entry("sign-out", "right-from-bracket"),
            Map.entry("site", "globe"),
            Map.entry("speed", "gauge-high"),
            Map.entry("success", "circle-check"),
            Map.entry("sword", "shield-halved"),
            Map.entry("tachometer", "gauge-high"),
            Map.entry("times", "xmark"),
            Map.entry("to-game", "play"),
            Map.entry("units", "cube"),
            Map.entry("upload-skin", "upload"),
            Map.entry("user-pane", "circle-user"),
            Map.entry("warning", "triangle-exclamation"),
            Map.entry("web", "globe"),
            Map.entry("website", "globe")
    );

    private FontAwesomeIconRegistry() {
    }

    public static Optional<FontAwesomeIconSpec> resolve(String reference) {
        String requestedName = extractIconName(reference);
        if (requestedName == null) {
            return Optional.empty();
        }

        String canonicalName = canonicalName(requestedName);
        Integer codePoint = ICONS.get(canonicalName);
        boolean known = codePoint != null;
        if (!known) {
            canonicalName = DEFAULT_ICON;
            codePoint = ICONS.get(DEFAULT_ICON);
        }

        return Optional.of(new FontAwesomeIconSpec(canonicalName, requestedName, codePoint, known));
    }

    private static String extractIconName(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String value = reference.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.startsWith("fa:")) {
            return value.substring(value.indexOf(':') + 1);
        }
        if (lower.startsWith("fas:")) {
            return value.substring(value.indexOf(':') + 1);
        }
        if (lower.startsWith("fa-solid:")) {
            return value.substring(value.indexOf(':') + 1);
        }
        if (lower.startsWith("fontawesome:")) {
            return value.substring(value.indexOf(':') + 1);
        }

        if (lower.contains(" ")) {
            String[] tokens = lower.split("\\s+");
            boolean hasFontAwesomeClass = false;
            String iconToken = null;
            for (String token : tokens) {
                if (isFontAwesomeStyleToken(token)) {
                    hasFontAwesomeClass = true;
                    continue;
                }
                if (token.startsWith("fa-") && token.length() > 3) {
                    iconToken = token.substring(3);
                }
            }
            if (hasFontAwesomeClass && iconToken != null) {
                return iconToken;
            }
        }

        if (lower.startsWith("fa-") && !lower.contains("/") && !lower.contains("\\") && !lower.contains(".")) {
            return value.substring(3);
        }

        return null;
    }

    private static boolean isFontAwesomeStyleToken(String token) {
        return "fa".equals(token)
                || "fas".equals(token)
                || "fa-solid".equals(token)
                || "far".equals(token)
                || "fa-regular".equals(token)
                || "fab".equals(token)
                || "fa-brands".equals(token);
    }

    private static String canonicalName(String name) {
        String normalized = name.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        if (normalized.startsWith("fa-")) {
            normalized = normalized.substring(3);
        }
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    public static final class FontAwesomeIconSpec {
        private final String name;
        private final String requestedName;
        private final int codePoint;
        private final boolean known;

        private FontAwesomeIconSpec(String name, String requestedName, int codePoint, boolean known) {
            this.name = name;
            this.requestedName = requestedName;
            this.codePoint = codePoint;
            this.known = known;
        }

        public String getName() {
            return name;
        }

        public String getRequestedName() {
            return requestedName;
        }

        public int getCodePoint() {
            return codePoint;
        }

        public boolean isKnown() {
            return known;
        }

        public String getGlyph() {
            return glyph(codePoint);
        }
    }
}
