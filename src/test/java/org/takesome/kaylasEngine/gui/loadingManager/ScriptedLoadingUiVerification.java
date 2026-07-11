package org.takesome.kaylasEngine.gui.loadingManager;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable verification for loading UI parsing, defaults and adapter boundaries. */
public final class ScriptedLoadingUiVerification {
    private ScriptedLoadingUiVerification() {
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir", "."));
        System.setProperty("log.level", "INFO");
        Engine.LOGGER = LogManager.getLogger(ScriptedLoadingUiVerification.class);

        verifyDefaults();
        verifyProfileParsing();
        verifyProgressAdapter();
        verifyConfigCoercion();

        System.out.println("Scripted loading UI verification passed.");
    }

    private static void verifyDefaults() {
        ScriptedLoadingUi fallback = ScriptedLoadingUiDefaults.create();
        require(fallback.overlay().enabled(), "fallback overlay must be enabled");
        require(fallback.window().width() == 500 && fallback.window().height() == 150,
                "fallback window dimensions changed");
        require("assets/ui/sprites/loaderFox.png".equals(fallback.loader().spritePath()),
                "fallback loader sprite changed");
        require(fallback.transition().entry().totalDurationMs() == 300,
                "fallback entry transition duration changed");
    }

    private static void verifyProfileParsing() {
        ScriptedLoadingUi fallback = ScriptedLoadingUiDefaults.create();

        Map<Integer, Object> profiles = new LinkedHashMap<>();
        profiles.put(1, Map.of(
                "sprite", Map.of(
                        "path", "assets/loader-a.png",
                        "rows", 2,
                        "columns", 4,
                        "bounds", Map.of("x", 1, "y", 2, "width", 32, "height", 48)
                )
        ));
        profiles.put(2, Map.of(
                "sprite", Map.of(
                        "path", "assets/loader-b.png",
                        "rows", 3,
                        "columns", 6,
                        "frameDelayMs", 25,
                        "bounds", Map.of("x", 7, "y", 9, "width", 96, "height", 80)
                ),
                "background", Map.of("image", "assets/bg.png", "color", "#11223344")
        ));

        Map<Integer, Object> messageKeys = new LinkedHashMap<>();
        messageKeys.put(2, "progress.second");
        messageKeys.put(1, "progress.first");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("window", Map.of(
                "width", 640,
                "height", 180,
                "alwaysOnTop", false,
                "cornerRadius", 22
        ));
        root.put("loader", Map.of("profiles", profiles));
        root.put("progress", Map.of(
                "updateMs", 75,
                "step", 3,
                "maxValue", 250,
                "messageKeys", messageKeys,
                "showPercent", true,
                "loop", false
        ));
        root.put("transition", Map.of(
                "entry", Map.of(
                        "motion", Map.of(
                                "durationMs", 420,
                                "from", Map.of("anchor", "frameCenter"),
                                "to", Map.of("anchor", "frameBottom")
                        )
                )
        ));

        ScriptedLoadingUi parsed = ScriptedLoadingUiParser.parse(root, 1, fallback);
        require(parsed.window().width() == 640 && parsed.window().height() == 180,
                "window configuration was not parsed");
        require(!parsed.window().alwaysOnTop() && parsed.window().cornerRadius() == 22,
                "window flags were not parsed");
        require("assets/loader-b.png".equals(parsed.loader().spritePath()),
                "loader profile selection is incorrect");
        require(parsed.loader().rows() == 3 && parsed.loader().columns() == 6,
                "loader sprite grid was not parsed");
        require(parsed.loader().bounds().width == 96 && parsed.loader().bounds().height == 80,
                "loader bounds were not parsed");
        require(parsed.progress().messageKeys().equals(List.of("progress.first", "progress.second")),
                "numeric Lua list ordering is incorrect");
        require(parsed.transition().entry().motion().durationMs() == 420,
                "transition motion duration was not parsed");
        require(parsed.transition().entry().motion().from().referenceX() == 0.5,
                "named position anchor was not resolved");
    }

    private static void verifyProgressAdapter() {
        ScriptedLoadingUi parsed = ScriptedLoadingUiParser.parse(
                Map.of("progress", Map.of(
                        "updateMs", 42,
                        "step", 4,
                        "initialDelayMs", 10,
                        "cycleDelayMs", 20,
                        "timelineDurationMs", 700,
                        "timelineFrameDelayMs", 14,
                        "maxValue", 300,
                        "loop", false,
                        "randomMessages", false
                )),
                0,
                ScriptedLoadingUiDefaults.create()
        );
        ProgressBarAnimator.Options options = parsed.progress().toEngineOptions();
        require(options.progressUpdateMs() == 42 && options.progressStep() == 4,
                "progress timing adapter is incorrect");
        require(options.initialDelayMs() == 10 && options.cycleDelayMs() == 20,
                "progress delay adapter is incorrect");
        require(options.timelineDurationMs() == 700 && options.timelineFrameDelayMs() == 14,
                "timeline adapter is incorrect");
        require(options.maxValue() == 300 && !options.loop() && !options.randomMessages(),
                "progress policy flags were not adapted");
    }

    private static void verifyConfigCoercion() {
        require(LoadingUiConfigSupport.number(Map.of("value", "2.75"), "value", 0.0) == 2.75,
                "numeric string coercion failed");
        require(LoadingUiConfigSupport.number(Map.of("value", "invalid"), "value", 9.0) == 9.0,
                "invalid numeric fallback failed");
        require("#ffffff".equals(LoadingUiConfigSupport.normalizeColorString("", "#ffffff")),
                "color fallback normalization failed");
        require(LoadingUiConfigSupport.clamp01(-1.0f) == 0.0f
                        && LoadingUiConfigSupport.clamp01(2.0f) == 1.0f,
                "opacity clamping failed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
