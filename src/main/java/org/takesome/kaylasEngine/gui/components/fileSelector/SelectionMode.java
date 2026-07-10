package org.takesome.kaylasEngine.gui.components.fileSelector;

import java.util.Locale;
import java.util.Set;

public enum SelectionMode {
    FILES_ONLY,
    DIRECTORIES_ONLY;

    private static final Set<String> FILE_ALIASES = Set.of(
            "FILE",
            "FILES",
            "FILE_ONLY",
            "FILES_ONLY"
    );
    private static final Set<String> DIRECTORY_ALIASES = Set.of(
            "DIRECTORY",
            "DIRECTORIES",
            "DIRECTORY_ONLY",
            "DIRECTORIES_ONLY",
            "FOLDER",
            "FOLDERS",
            "FOLDER_ONLY",
            "FOLDERS_ONLY"
    );

    public static SelectionMode from(String value) {
        String normalized = normalize(value);
        if (DIRECTORY_ALIASES.contains(normalized)) {
            return DIRECTORIES_ONLY;
        }
        return FILES_ONLY;
    }

    public static boolean isFileAlias(String value) {
        return value == null || value.isBlank() || FILE_ALIASES.contains(normalize(value));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
