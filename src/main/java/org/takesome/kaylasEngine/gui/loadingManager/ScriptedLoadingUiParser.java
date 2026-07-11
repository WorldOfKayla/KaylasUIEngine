package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigScript;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigValues;
import org.takesome.kaylasEngine.gui.scripting.UiScriptContext;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Interprets Lua loading UI configuration into immutable {@link ScriptedLoadingUi} policy objects. */
final class ScriptedLoadingUiParser {
    private ScriptedLoadingUiParser() {
    }

    static ScriptedLoadingUi load(UiScriptContext context,
                                  String scriptPath,
                                  int profileIndex,
                                  ScriptedLoadingUi fallback) {
        try {
            Map<String, Object> root = LuaConfigScript.load(context, scriptPath);
            if (root.isEmpty()) {
                Engine.getLOGGER().warn(
                        "Loading UI script returned no configuration: {}",
                        scriptPath
                );
                return fallback;
            }
            return parse(root, profileIndex, fallback);
        } catch (Exception error) {
            Engine.getLOGGER().warn(
                    "Unable to interpret loading UI script '{}'. Using fallback.",
                    scriptPath,
                    error
            );
            return fallback;
        }
    }


    static ScriptedLoadingUi parse(Map<String, Object> root,
                                   int profileIndex,
                                   ScriptedLoadingUi fallback) {
        Map<String, Object> safeRoot = root == null ? Map.of() : root;
        ScriptedLoadingUi safeFallback = fallback == null
                ? ScriptedLoadingUiDefaults.create()
                : fallback;
        return new ScriptedLoadingUi(
                overlay(LuaConfigValues.map(safeRoot, "overlay"), safeFallback.overlay()),
                window(LuaConfigValues.map(safeRoot, "window"), safeFallback.window()),
                selectLoader(
                        LuaConfigValues.map(safeRoot, "loader"),
                        profileIndex,
                        safeFallback.loader()
                ),
                typography(
                        LuaConfigValues.map(safeRoot, "typography"),
                        safeFallback.typography()
                ),
                progress(
                        LuaConfigValues.map(safeRoot, "progress"),
                        safeFallback.progress()
                ),
                transition(
                        LuaConfigValues.map(safeRoot, "transition"),
                        safeFallback.transition()
                )
        );
    }

    private static ScriptedLoadingUi.Overlay overlay(
            Map<String, Object> table,
            ScriptedLoadingUi.Overlay fallback
    ) {
        Map<String, Object> boundsTable = LuaConfigValues.map(table, "bounds");
        ScriptedLoadingUi.Fade fadeIn = fade(
                LuaConfigValues.map(table, "fadeIn"),
                LuaConfigValues.integer(table, "fadeInMs", fallback.fadeIn.durationMs),
                LuaConfigValues.integer(table, "frameDelayMs", fallback.fadeIn.frameDelayMs),
                fallback.fadeIn
        );
        ScriptedLoadingUi.Fade fadeOut = fade(
                LuaConfigValues.map(table, "fadeOut"),
                LuaConfigValues.integer(table, "fadeOutMs", fallback.fadeOut.durationMs),
                LuaConfigValues.integer(table, "frameDelayMs", fallback.fadeOut.frameDelayMs),
                fallback.fadeOut
        );
        ScriptedLoadingUi.Bounds bounds = bounds(
                boundsTable.isEmpty() ? table : boundsTable,
                fallback.bounds
        );
        return new ScriptedLoadingUi.Overlay(
                LuaConfigValues.bool(table, "enabled", fallback.enabled),
                LuaConfigValues.string(table, "name", fallback.name),
                LuaConfigValues.color(table, "color", fallback.color),
                LuaConfigValues.alpha(table, fallback.targetAlpha),
                fadeIn,
                fadeOut,
                bounds
        );
    }

    private static ScriptedLoadingUi.Fade fade(
            Map<String, Object> table,
            int legacyDurationMs,
            int legacyFrameDelayMs,
            ScriptedLoadingUi.Fade fallback
    ) {
        if (table == null || table.isEmpty()) {
            return new ScriptedLoadingUi.Fade(
                    legacyDurationMs,
                    legacyFrameDelayMs,
                    fallback.curve
            );
        }
        return new ScriptedLoadingUi.Fade(
                LuaConfigValues.integer(table, "durationMs", legacyDurationMs),
                LuaConfigValues.integer(table, "frameDelayMs", legacyFrameDelayMs),
                LoadingUiConfigSupport.curve(table, "easing", fallback.curve)
        );
    }

    private static ScriptedLoadingUi.Window window(
            Map<String, Object> table,
            ScriptedLoadingUi.Window fallback
    ) {
        return new ScriptedLoadingUi.Window(
                LuaConfigValues.integer(table, "width", fallback.width),
                LuaConfigValues.integer(table, "height", fallback.height),
                LuaConfigValues.bool(table, "alwaysOnTop", fallback.alwaysOnTop),
                LuaConfigValues.integer(table, "cornerRadius", fallback.cornerRadius)
        );
    }

    private static ScriptedLoadingUi.Loader selectLoader(
            Map<String, Object> table,
            int profileIndex,
            ScriptedLoadingUi.Loader fallback
    ) {
        List<Map<String, Object>> profiles = LoadingUiConfigSupport.mapList(
                table == null ? null : table.get("profiles")
        );
        Map<String, Object> selected = table;
        if (!profiles.isEmpty()) {
            int boundedIndex = Math.max(0, Math.min(profileIndex, profiles.size() - 1));
            selected = profiles.get(boundedIndex);
            Engine.getLOGGER().debug(
                    "[SCRIPTED-LOADING-UI] selected loader profile {}/{}",
                    boundedIndex,
                    profiles.size()
            );
        }
        return loader(selected, fallback);
    }

    private static ScriptedLoadingUi.Loader loader(
            Map<String, Object> table,
            ScriptedLoadingUi.Loader fallback
    ) {
        Map<String, Object> safeTable = table == null ? Map.of() : table;
        Map<String, Object> sprite = LuaConfigValues.map(safeTable, "sprite");
        if (sprite.isEmpty()) {
            sprite = safeTable;
        }
        Map<String, Object> boundsTable = LuaConfigValues.map(sprite, "bounds");
        Map<String, Object> backgroundTable = LuaConfigValues.map(safeTable, "background");
        return new ScriptedLoadingUi.Loader(
                LuaConfigValues.bool(safeTable, "enabled", fallback.enabled),
                LuaConfigValues.string(
                        sprite,
                        "path",
                        LuaConfigValues.string(sprite, "spritePath", fallback.spritePath)
                ),
                LuaConfigValues.integer(sprite, "rows", fallback.rows),
                LuaConfigValues.integer(
                        sprite,
                        "columns",
                        LuaConfigValues.integer(sprite, "cols", fallback.columns)
                ),
                LuaConfigValues.integer(
                        sprite,
                        "frameDelayMs",
                        LuaConfigValues.integer(sprite, "delay", fallback.frameDelayMs)
                ),
                bounds(boundsTable, fallback.bounds),
                background(backgroundTable, fallback.background),
                LuaConfigValues.string(safeTable, "titleColor", fallback.titleColor),
                LuaConfigValues.string(
                        safeTable,
                        "messageColor",
                        LuaConfigValues.string(safeTable, "descColor", fallback.messageColor)
                )
        );
    }

    private static ScriptedLoadingUi.Background background(
            Map<String, Object> table,
            ScriptedLoadingUi.Background fallback
    ) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        return new ScriptedLoadingUi.Background(
                LuaConfigValues.string(
                        table,
                        "image",
                        LuaConfigValues.string(table, "path", fallback.image)
                ),
                LuaConfigValues.string(table, "color", fallback.color)
        );
    }

    private static ScriptedLoadingUi.Bounds bounds(
            Map<String, Object> table,
            ScriptedLoadingUi.Bounds fallback
    ) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        Map<String, Object> size = LuaConfigValues.map(table, "size");
        return new ScriptedLoadingUi.Bounds(
                LuaConfigValues.integer(table, "x", fallback.x),
                LuaConfigValues.integer(table, "y", fallback.y),
                LuaConfigValues.integer(
                        table,
                        "width",
                        LuaConfigValues.integer(size, "width", fallback.width)
                ),
                LuaConfigValues.integer(
                        table,
                        "height",
                        LuaConfigValues.integer(size, "height", fallback.height)
                )
        );
    }

    private static ScriptedLoadingUi.Typography typography(
            Map<String, Object> table,
            ScriptedLoadingUi.Typography fallback
    ) {
        return new ScriptedLoadingUi.Typography(
                textStyle(LuaConfigValues.map(table, "title"), fallback.title),
                textStyle(LuaConfigValues.map(table, "message"), fallback.message)
        );
    }

    private static ScriptedLoadingUi.TextStyle textStyle(
            Map<String, Object> table,
            ScriptedLoadingUi.TextStyle fallback
    ) {
        return new ScriptedLoadingUi.TextStyle(
                LuaConfigValues.string(table, "style", fallback.styleName),
                LuaConfigValues.string(table, "font", fallback.fontName),
                LuaConfigValues.integer(table, "fontSize", fallback.fontSize),
                LuaConfigValues.string(table, "fontStyle", fallback.fontStyle),
                LuaConfigValues.string(table, "color", fallback.color)
        );
    }

    private static ScriptedLoadingUi.Progress progress(
            Map<String, Object> table,
            ScriptedLoadingUi.Progress fallback
    ) {
        return new ScriptedLoadingUi.Progress(
                LuaConfigValues.bool(table, "enabled", fallback.enabled),
                LuaConfigValues.integer(
                        table,
                        "updateMs",
                        LuaConfigValues.integer(table, "progressUpdateMs", fallback.updateMs)
                ),
                LuaConfigValues.integer(
                        table,
                        "step",
                        LuaConfigValues.integer(table, "progressStep", fallback.step)
                ),
                LuaConfigValues.integer(table, "initialDelayMs", fallback.initialDelayMs),
                LuaConfigValues.integer(table, "cycleDelayMs", fallback.cycleDelayMs),
                LuaConfigValues.integer(
                        table,
                        "timelineDurationMs",
                        fallback.timelineDurationMs
                ),
                LuaConfigValues.integer(
                        table,
                        "timelineFrameDelayMs",
                        fallback.timelineFrameDelayMs
                ),
                LuaConfigValues.integer(table, "maxValue", fallback.maxValue),
                LuaConfigValues.bool(table, "loop", fallback.loop),
                LuaConfigValues.bool(table, "randomMessages", fallback.randomMessages),
                LuaConfigValues.bool(table, "showText", fallback.showText),
                LuaConfigValues.bool(table, "showPercent", fallback.showPercent),
                LuaConfigValues.bool(table, "resetOnStop", fallback.resetOnStop),
                LuaConfigValues.bool(table, "hideOnStop", fallback.hideOnStop),
                LuaConfigValues.bool(table, "animateEntrance", fallback.animateEntrance),
                LuaConfigValues.bool(table, "animateExit", fallback.animateExit),
                LuaConfigValues.string(table, "messagesSection", fallback.messagesSection),
                LuaConfigValues.string(table, "messagesResource", fallback.messagesResource),
                LuaConfigValues.string(
                        table,
                        "animationConfigResource",
                        fallback.animationConfigResource
                ),
                LoadingUiConfigSupport.stringList(table, "messageKeys", fallback.messageKeys),
                LuaConfigValues.string(table, "style", fallback.styleName),
                LuaConfigValues.string(table, "font", fallback.fontName),
                LuaConfigValues.integer(table, "fontSize", fallback.fontSize),
                LuaConfigValues.string(table, "fontStyle", fallback.fontStyle),
                LuaConfigValues.string(table, "textColor", fallback.textColor)
        );
    }

    private static ScriptedLoadingUi.Transition transition(
            Map<String, Object> table,
            ScriptedLoadingUi.Transition fallback
    ) {
        return new ScriptedLoadingUi.Transition(
                LuaConfigValues.bool(table, "enabled", fallback.enabled),
                phase(LuaConfigValues.map(table, "entry"), fallback.entry),
                phase(LuaConfigValues.map(table, "exit"), fallback.exit)
        );
    }

    private static ScriptedLoadingUi.Phase phase(
            Map<String, Object> table,
            ScriptedLoadingUi.Phase fallback
    ) {
        return new ScriptedLoadingUi.Phase(
                motion(LuaConfigValues.map(table, "motion"), fallback.motion),
                opacity(LuaConfigValues.map(table, "opacity"), fallback.opacity)
        );
    }

    private static ScriptedLoadingUi.Motion motion(
            Map<String, Object> table,
            ScriptedLoadingUi.Motion fallback
    ) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        return new ScriptedLoadingUi.Motion(
                LuaConfigValues.bool(table, "enabled", fallback.enabled),
                LuaConfigValues.integer(table, "delayMs", fallback.delayMs),
                LuaConfigValues.integer(table, "durationMs", fallback.durationMs),
                LuaConfigValues.integer(table, "frameDelayMs", fallback.frameDelayMs),
                LoadingUiConfigSupport.curve(table, "easing", fallback.curve),
                position(LuaConfigValues.map(table, "from"), fallback.from),
                position(LuaConfigValues.map(table, "to"), fallback.to)
        );
    }

    private static ScriptedLoadingUi.Opacity opacity(
            Map<String, Object> table,
            ScriptedLoadingUi.Opacity fallback
    ) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        return new ScriptedLoadingUi.Opacity(
                LuaConfigValues.bool(table, "enabled", fallback.enabled),
                LuaConfigValues.integer(table, "delayMs", fallback.delayMs),
                LuaConfigValues.integer(table, "durationMs", fallback.durationMs),
                LuaConfigValues.integer(table, "frameDelayMs", fallback.frameDelayMs),
                LoadingUiConfigSupport.curve(table, "easing", fallback.curve),
                (float) LoadingUiConfigSupport.number(table, "from", fallback.from),
                (float) LoadingUiConfigSupport.number(table, "to", fallback.to)
        );
    }

    private static ScriptedLoadingUi.Position position(
            Map<String, Object> table,
            ScriptedLoadingUi.Position fallback
    ) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        ScriptedLoadingUi.Position anchorFallback = namedAnchor(
                LuaConfigValues.string(table, "anchor", ""),
                fallback
        );
        return new ScriptedLoadingUi.Position(
                LuaConfigValues.string(table, "reference", anchorFallback.reference),
                LoadingUiConfigSupport.number(
                        table,
                        "referenceX",
                        LoadingUiConfigSupport.number(
                                table,
                                "frameX",
                                anchorFallback.referenceX
                        )
                ),
                LoadingUiConfigSupport.number(
                        table,
                        "referenceY",
                        LoadingUiConfigSupport.number(
                                table,
                                "frameY",
                                anchorFallback.referenceY
                        )
                ),
                LoadingUiConfigSupport.number(table, "windowX", anchorFallback.windowX),
                LoadingUiConfigSupport.number(table, "windowY", anchorFallback.windowY),
                LuaConfigValues.integer(table, "offsetX", anchorFallback.offsetX),
                LuaConfigValues.integer(table, "offsetY", anchorFallback.offsetY)
        );
    }

    private static ScriptedLoadingUi.Position namedAnchor(
            String anchor,
            ScriptedLoadingUi.Position fallback
    ) {
        String normalized = anchor == null
                ? ""
                : anchor.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        return switch (normalized) {
            case "framecenter", "center" ->
                    ScriptedLoadingUi.Position.frame(0.5, 0.5, 0.5, 0.5, 0, 0);
            case "frametopoutside", "topoutside" ->
                    ScriptedLoadingUi.Position.frame(0.5, 0.0, 0.5, 1.0, 0, 0);
            case "framebottomoutside", "bottomoutside" ->
                    ScriptedLoadingUi.Position.frame(0.5, 1.0, 0.5, 0.0, 0, 0);
            case "frameleftoutside", "leftoutside" ->
                    ScriptedLoadingUi.Position.frame(0.0, 0.5, 1.0, 0.5, 0, 0);
            case "framerightoutside", "rightoutside" ->
                    ScriptedLoadingUi.Position.frame(1.0, 0.5, 0.0, 0.5, 0, 0);
            case "frametop", "top" ->
                    ScriptedLoadingUi.Position.frame(0.5, 0.0, 0.5, 0.0, 0, 0);
            case "framebottom", "bottom" ->
                    ScriptedLoadingUi.Position.frame(0.5, 1.0, 0.5, 1.0, 0, 0);
            case "frameleft", "left" ->
                    ScriptedLoadingUi.Position.frame(0.0, 0.5, 0.0, 0.5, 0, 0);
            case "frameright", "right" ->
                    ScriptedLoadingUi.Position.frame(1.0, 0.5, 1.0, 0.5, 0, 0);
            case "current" -> ScriptedLoadingUi.Position.current(0, 0);
            default -> fallback;
        };
    }
}
