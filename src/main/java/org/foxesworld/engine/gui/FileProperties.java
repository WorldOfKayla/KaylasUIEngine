package org.foxesworld.engine.gui;

import org.foxesworld.engine.Engine;

import java.util.Collections;
import java.util.Map;

/**
 * Resolved engine resource paths used by GUI, locale and sound bootstrap.
 */
public class FileProperties {
    private static final String DEFAULT_FRAME_TEMPLATE = "assets/demo/test-frame.json";
    private static final String DEFAULT_MAIN_FRAME = "assets/demo/test-main.json";
    private static final String DEFAULT_LOCALE_FILE = "assets/demo/test-locale.json";
    private static final String DEFAULT_SOUNDS_FILE = "assets/sounds/sounds.json";

    private final String frameTpl;
    private final String mainFrame;
    private final String localeFile;
    private final String soundsFile;

    public FileProperties(Engine engine) {
        Map<String, Object> files = engine.getEngineData() != null && engine.getEngineData().getFiles() != null
                ? engine.getEngineData().getFiles()
                : Collections.emptyMap();

        this.frameTpl = readString(files, "frameTpl", DEFAULT_FRAME_TEMPLATE);
        this.mainFrame = readString(files, "mainFrame", DEFAULT_MAIN_FRAME);
        this.localeFile = readString(files, "localeFile", DEFAULT_LOCALE_FILE);
        this.soundsFile = readString(files, "soundsFile", DEFAULT_SOUNDS_FILE);
    }

    private String readString(Map<String, Object> files, String key, String fallback) {
        String override = System.getProperty("kaylasengine.ui." + key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        Object value = files.get(key);
        if (value == null) {
            return fallback;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isEmpty() ? fallback : resolved;
    }

    public String getFrameTpl() {
        return frameTpl;
    }

    public String getMainFrame() {
        return mainFrame;
    }

    public String getLocaleFile() {
        return localeFile;
    }

    public String getSoundsFile() {
        return soundsFile;
    }
}
