package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.animation.AnimationCurve;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigScript;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigValues;
import org.takesome.kaylasEngine.gui.scripting.UiScriptContext;

import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Engine-side interpreter for an application-owned loading UI Lua script.
 *
 * <p>The script is the only visual-policy source. This class merely validates and exposes the
 * resulting values to reusable engine primitives.</p>
 */
public final class ScriptedLoadingUi {
    private static final String DEFAULT_PROGRESS_MESSAGES_SECTION = "progressMessages";
    private static final List<String> DEFAULT_PROGRESS_MESSAGE_KEYS = List.of();

    private static final ScriptedLoadingUi FALLBACK = new ScriptedLoadingUi(
            new Overlay(
                    true,
                    "loadingOverlay",
                    Color.BLACK,
                    179,
                    new Fade(220, 16, AnimationCurve.named("easeOutQuad")),
                    new Fade(240, 16, AnimationCurve.named("easeOutQuad")),
                    new Bounds(0, 0, -1, -1)
            ),
            new Window(500, 150, true, 15),
            new Loader(
                    true,
                    "assets/ui/sprites/loaderFox.png",
                    3,
                    5,
                    55,
                    new Bounds(30, 40, 64, 64),
                    new Background("assets/ui/img/bg/season/summer.png", "#b3a8998a"),
                    "#252424",
                    "#7534d4"
            ),
            new Typography(
                    new TextStyle("titleBold", "", 16, "plain", ""),
                    new TextStyle("title", "", 11, "plain", "")
            ),
            new Progress(
                    true,
                    100,
                    1,
                    0,
                    0,
                    500,
                    16,
                    -1,
                    true,
                    true,
                    true,
                    false,
                    true,
                    false,
                    true,
                    true,
                    DEFAULT_PROGRESS_MESSAGES_SECTION,
                    "assets/messages.json",
                    "assets/animation_config.json",
                    DEFAULT_PROGRESS_MESSAGE_KEYS,
                    "progressMini",
                    "",
                    0,
                    "",
                    ""
            ),
            new Transition(
                    true,
                    new Phase(
                            new Motion(
                                    true,
                                    0,
                                    300,
                                    16,
                                    AnimationCurve.named("easeInOutSine"),
                                    Position.frame(0.5, 0.0, 0.5, 1.0, 0, 0),
                                    Position.frame(0.5, 0.5, 0.5, 0.5, 0, 0)
                            ),
                            new Opacity(
                                    true,
                                    0,
                                    300,
                                    16,
                                    AnimationCurve.named("easeInOutSine"),
                                    0.0f,
                                    1.0f
                            )
                    ),
                    new Phase(
                            new Motion(
                                    true,
                                    0,
                                    300,
                                    16,
                                    AnimationCurve.named("easeInOutSine"),
                                    Position.current(0, 0),
                                    Position.frame(1.0, 0.5, 0.0, 0.5, 0, 0)
                            ),
                            new Opacity(
                                    true,
                                    0,
                                    300,
                                    16,
                                    AnimationCurve.named("easeInOutSine"),
                                    1.0f,
                                    0.0f
                            )
                    )
            )
    );

    private final Overlay overlay;
    private final Window window;
    private final Loader loader;
    private final Typography typography;
    private final Progress progress;
    private final Transition transition;

    private ScriptedLoadingUi(Overlay overlay,
                              Window window,
                              Loader loader,
                              Typography typography,
                              Progress progress,
                              Transition transition) {
        this.overlay = Objects.requireNonNull(overlay, "overlay");
        this.window = Objects.requireNonNull(window, "window");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.typography = Objects.requireNonNull(typography, "typography");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    public static ScriptedLoadingUi load(UiScriptContext context, String scriptPath, int profileIndex) {
        try {
            Map<String, Object> root = LuaConfigScript.load(context, scriptPath);
            if (root.isEmpty()) {
                Engine.getLOGGER().warn("Loading UI script returned no configuration: {}", scriptPath);
                return FALLBACK;
            }
            return new ScriptedLoadingUi(
                    Overlay.from(LuaConfigValues.map(root, "overlay"), FALLBACK.overlay),
                    Window.from(LuaConfigValues.map(root, "window"), FALLBACK.window),
                    Loader.select(LuaConfigValues.map(root, "loader"), profileIndex, FALLBACK.loader),
                    Typography.from(LuaConfigValues.map(root, "typography"), FALLBACK.typography),
                    Progress.from(LuaConfigValues.map(root, "progress"), FALLBACK.progress),
                    Transition.from(LuaConfigValues.map(root, "transition"), FALLBACK.transition)
            );
        } catch (Exception error) {
            Engine.getLOGGER().warn("Unable to interpret loading UI script '{}'. Using fallback.", scriptPath, error);
            return FALLBACK;
        }
    }

    public Overlay overlay() { return overlay; }
    public Window window() { return window; }
    public Loader loader() { return loader; }
    public Typography typography() { return typography; }
    public Progress progress() { return progress; }
    public Transition transition() { return transition; }

    public static final class Overlay {
        private final boolean enabled;
        private final String name;
        private final Color color;
        private final int targetAlpha;
        private final Fade fadeIn;
        private final Fade fadeOut;
        private final Bounds bounds;

        private Overlay(boolean enabled,
                        String name,
                        Color color,
                        int targetAlpha,
                        Fade fadeIn,
                        Fade fadeOut,
                        Bounds bounds) {
            this.enabled = enabled;
            this.name = name == null || name.isBlank() ? "loadingOverlay" : name.trim();
            this.color = Objects.requireNonNull(color, "color");
            this.targetAlpha = LuaConfigValues.clamp(targetAlpha, 0, 255);
            this.fadeIn = Objects.requireNonNull(fadeIn, "fadeIn");
            this.fadeOut = Objects.requireNonNull(fadeOut, "fadeOut");
            this.bounds = Objects.requireNonNull(bounds, "bounds");
        }

        private static Overlay from(Map<String, Object> table, Overlay fallback) {
            Map<String, Object> boundsTable = LuaConfigValues.map(table, "bounds");
            Fade fadeIn = Fade.from(
                    LuaConfigValues.map(table, "fadeIn"),
                    LuaConfigValues.integer(table, "fadeInMs", fallback.fadeIn.durationMs),
                    LuaConfigValues.integer(table, "frameDelayMs", fallback.fadeIn.frameDelayMs),
                    fallback.fadeIn
            );
            Fade fadeOut = Fade.from(
                    LuaConfigValues.map(table, "fadeOut"),
                    LuaConfigValues.integer(table, "fadeOutMs", fallback.fadeOut.durationMs),
                    LuaConfigValues.integer(table, "frameDelayMs", fallback.fadeOut.frameDelayMs),
                    fallback.fadeOut
            );
            Bounds bounds = Bounds.from(
                    boundsTable.isEmpty() ? table : boundsTable,
                    fallback.bounds
            );
            return new Overlay(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    LuaConfigValues.string(table, "name", fallback.name),
                    LuaConfigValues.color(table, "color", fallback.color),
                    LuaConfigValues.alpha(table, fallback.targetAlpha),
                    fadeIn,
                    fadeOut,
                    bounds
            );
        }

        public boolean enabled() { return enabled; }
        public String name() { return name; }
        public Color color() { return color; }
        public int targetAlpha() { return targetAlpha; }
        public Fade fadeIn() { return fadeIn; }
        public Fade fadeOut() { return fadeOut; }

        public Rectangle bounds(int frameWidth, int frameHeight) {
            return bounds.resolve(frameWidth, frameHeight);
        }
    }

    public static final class Fade {
        private final int durationMs;
        private final int frameDelayMs;
        private final AnimationCurve curve;

        private Fade(int durationMs, int frameDelayMs, AnimationCurve curve) {
            this.durationMs = Math.max(0, durationMs);
            this.frameDelayMs = Math.max(1, frameDelayMs);
            this.curve = Objects.requireNonNull(curve, "curve");
        }

        private static Fade from(Map<String, Object> table,
                                 int legacyDurationMs,
                                 int legacyFrameDelayMs,
                                 Fade fallback) {
            if (table == null || table.isEmpty()) {
                return new Fade(legacyDurationMs, legacyFrameDelayMs, fallback.curve);
            }
            return new Fade(
                    LuaConfigValues.integer(table, "durationMs", legacyDurationMs),
                    LuaConfigValues.integer(table, "frameDelayMs", legacyFrameDelayMs),
                    ScriptedLoadingUi.curve(table, "easing", fallback.curve)
            );
        }

        public int durationMs() { return durationMs; }
        public int frameDelayMs() { return frameDelayMs; }
        public AnimationCurve curve() { return curve; }
    }

    public static final class Window {
        private final int width;
        private final int height;
        private final boolean alwaysOnTop;
        private final int cornerRadius;

        private Window(int width, int height, boolean alwaysOnTop, int cornerRadius) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.alwaysOnTop = alwaysOnTop;
            this.cornerRadius = Math.max(0, cornerRadius);
        }

        private static Window from(Map<String, Object> table, Window fallback) {
            return new Window(
                    LuaConfigValues.integer(table, "width", fallback.width),
                    LuaConfigValues.integer(table, "height", fallback.height),
                    LuaConfigValues.bool(table, "alwaysOnTop", fallback.alwaysOnTop),
                    LuaConfigValues.integer(table, "cornerRadius", fallback.cornerRadius)
            );
        }

        public int width() { return width; }
        public int height() { return height; }
        public boolean alwaysOnTop() { return alwaysOnTop; }
        public int cornerRadius() { return cornerRadius; }
    }

    public static final class Loader {
        private final boolean enabled;
        private final String spritePath;
        private final int rows;
        private final int columns;
        private final int frameDelayMs;
        private final Bounds bounds;
        private final Background background;
        private final String titleColor;
        private final String messageColor;

        private Loader(boolean enabled,
                       String spritePath,
                       int rows,
                       int columns,
                       int frameDelayMs,
                       Bounds bounds,
                       Background background,
                       String titleColor,
                       String messageColor) {
            this.enabled = enabled;
            this.spritePath = spritePath == null ? "" : spritePath.trim();
            this.rows = Math.max(1, rows);
            this.columns = Math.max(1, columns);
            this.frameDelayMs = Math.max(1, frameDelayMs);
            this.bounds = Objects.requireNonNull(bounds, "bounds");
            this.background = Objects.requireNonNull(background, "background");
            this.titleColor = normalizeColorString(titleColor, "#ffffff");
            this.messageColor = normalizeColorString(messageColor, "#ffffff");
        }

        private static Loader select(Map<String, Object> table, int profileIndex, Loader fallback) {
            List<Map<String, Object>> profiles = mapList(table == null ? null : table.get("profiles"));
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
            return from(selected, fallback);
        }

        private static Loader from(Map<String, Object> table, Loader fallback) {
            Map<String, Object> sprite = LuaConfigValues.map(table, "sprite");
            if (sprite.isEmpty()) {
                sprite = table;
            }
            Map<String, Object> boundsTable = LuaConfigValues.map(sprite, "bounds");
            Map<String, Object> backgroundTable = LuaConfigValues.map(table, "background");
            return new Loader(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    LuaConfigValues.string(sprite, "path",
                            LuaConfigValues.string(sprite, "spritePath", fallback.spritePath)),
                    LuaConfigValues.integer(sprite, "rows", fallback.rows),
                    LuaConfigValues.integer(sprite, "columns",
                            LuaConfigValues.integer(sprite, "cols", fallback.columns)),
                    LuaConfigValues.integer(sprite, "frameDelayMs",
                            LuaConfigValues.integer(sprite, "delay", fallback.frameDelayMs)),
                    Bounds.from(boundsTable, fallback.bounds),
                    Background.from(backgroundTable, fallback.background),
                    LuaConfigValues.string(table, "titleColor", fallback.titleColor),
                    LuaConfigValues.string(table, "messageColor",
                            LuaConfigValues.string(table, "descColor", fallback.messageColor))
            );
        }

        public boolean enabled() { return enabled; }
        public String spritePath() { return spritePath; }
        public int rows() { return rows; }
        public int columns() { return columns; }
        public int frameDelayMs() { return frameDelayMs; }
        public Rectangle bounds() { return bounds.resolveFixed(); }
        public Background background() { return background; }
        public String titleColor() { return titleColor; }
        public String messageColor() { return messageColor; }
    }

    public static final class Background {
        private final String image;
        private final String color;

        private Background(String image, String color) {
            this.image = image == null ? "" : image.trim();
            this.color = normalizeColorString(color, "#00000000");
        }

        private static Background from(Map<String, Object> table, Background fallback) {
            if (table == null || table.isEmpty()) {
                return fallback;
            }
            return new Background(
                    LuaConfigValues.string(table, "image",
                            LuaConfigValues.string(table, "path", fallback.image)),
                    LuaConfigValues.string(table, "color", fallback.color)
            );
        }

        public String image() { return image; }
        public String color() { return color; }
    }

    public static final class Bounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private static Bounds from(Map<String, Object> table, Bounds fallback) {
            if (table == null || table.isEmpty()) {
                return fallback;
            }
            Map<String, Object> size = LuaConfigValues.map(table, "size");
            return new Bounds(
                    LuaConfigValues.integer(table, "x", fallback.x),
                    LuaConfigValues.integer(table, "y", fallback.y),
                    LuaConfigValues.integer(table, "width",
                            LuaConfigValues.integer(size, "width", fallback.width)),
                    LuaConfigValues.integer(table, "height",
                            LuaConfigValues.integer(size, "height", fallback.height))
            );
        }

        private Rectangle resolve(int fallbackWidth, int fallbackHeight) {
            int resolvedWidth = width < 0 ? fallbackWidth : width;
            int resolvedHeight = height < 0 ? fallbackHeight : height;
            return new Rectangle(x, y, Math.max(0, resolvedWidth), Math.max(0, resolvedHeight));
        }

        private Rectangle resolveFixed() {
            return new Rectangle(x, y, Math.max(0, width), Math.max(0, height));
        }
    }

    public static final class Typography {
        private final TextStyle title;
        private final TextStyle message;

        private Typography(TextStyle title, TextStyle message) {
            this.title = Objects.requireNonNull(title, "title");
            this.message = Objects.requireNonNull(message, "message");
        }

        private static Typography from(Map<String, Object> table, Typography fallback) {
            return new Typography(
                    TextStyle.from(LuaConfigValues.map(table, "title"), fallback.title),
                    TextStyle.from(LuaConfigValues.map(table, "message"), fallback.message)
            );
        }

        public TextStyle title() { return title; }
        public TextStyle message() { return message; }
    }

    public static final class TextStyle {
        private final String styleName;
        private final String fontName;
        private final int fontSize;
        private final String fontStyle;
        private final String color;

        private TextStyle(String styleName,
                          String fontName,
                          int fontSize,
                          String fontStyle,
                          String color) {
            this.styleName = normalize(styleName);
            this.fontName = normalize(fontName);
            this.fontSize = Math.max(0, fontSize);
            this.fontStyle = normalize(fontStyle);
            this.color = normalize(color);
        }

        private static TextStyle from(Map<String, Object> table, TextStyle fallback) {
            return new TextStyle(
                    LuaConfigValues.string(table, "style", fallback.styleName),
                    LuaConfigValues.string(table, "font", fallback.fontName),
                    LuaConfigValues.integer(table, "fontSize", fallback.fontSize),
                    LuaConfigValues.string(table, "fontStyle", fallback.fontStyle),
                    LuaConfigValues.string(table, "color", fallback.color)
            );
        }

        public String styleName() { return styleName; }
        public String fontName() { return fontName; }
        public int fontSize() { return fontSize; }
        public String fontStyle() { return fontStyle; }
        public String color() { return color; }
    }

    public static final class Progress {
        private final boolean enabled;
        private final int updateMs;
        private final int step;
        private final int initialDelayMs;
        private final int cycleDelayMs;
        private final int timelineDurationMs;
        private final int timelineFrameDelayMs;
        private final int maxValue;
        private final boolean loop;
        private final boolean randomMessages;
        private final boolean showText;
        private final boolean showPercent;
        private final boolean resetOnStop;
        private final boolean hideOnStop;
        private final boolean animateEntrance;
        private final boolean animateExit;
        private final String messagesSection;
        private final String messagesResource;
        private final String animationConfigResource;
        private final List<String> messageKeys;
        private final String styleName;
        private final String fontName;
        private final int fontSize;
        private final String fontStyle;
        private final String textColor;

        private Progress(boolean enabled,
                         int updateMs,
                         int step,
                         int initialDelayMs,
                         int cycleDelayMs,
                         int timelineDurationMs,
                         int timelineFrameDelayMs,
                         int maxValue,
                         boolean loop,
                         boolean randomMessages,
                         boolean showText,
                         boolean showPercent,
                         boolean resetOnStop,
                         boolean hideOnStop,
                         boolean animateEntrance,
                         boolean animateExit,
                         String messagesSection,
                         String messagesResource,
                         String animationConfigResource,
                         List<String> messageKeys,
                         String styleName,
                         String fontName,
                         int fontSize,
                         String fontStyle,
                         String textColor) {
            this.enabled = enabled;
            this.updateMs = Math.max(1, updateMs);
            this.step = Math.max(1, step);
            this.initialDelayMs = Math.max(0, initialDelayMs);
            this.cycleDelayMs = Math.max(0, cycleDelayMs);
            this.timelineDurationMs = Math.max(1, timelineDurationMs);
            this.timelineFrameDelayMs = Math.max(1, timelineFrameDelayMs);
            this.maxValue = maxValue;
            this.loop = loop;
            this.randomMessages = randomMessages;
            this.showText = showText;
            this.showPercent = showPercent;
            this.resetOnStop = resetOnStop;
            this.hideOnStop = hideOnStop;
            this.animateEntrance = animateEntrance;
            this.animateExit = animateExit;
            this.messagesSection = messagesSection == null || messagesSection.isBlank()
                    ? DEFAULT_PROGRESS_MESSAGES_SECTION
                    : messagesSection.trim();
            this.messagesResource = normalize(messagesResource);
            this.animationConfigResource = normalize(animationConfigResource);
            this.messageKeys = List.copyOf(messageKeys == null ? List.of() : messageKeys);
            this.styleName = styleName == null || styleName.isBlank() ? "progressMini" : styleName.trim();
            this.fontName = normalize(fontName);
            this.fontSize = Math.max(0, fontSize);
            this.fontStyle = normalize(fontStyle);
            this.textColor = normalize(textColor);
        }

        private static Progress from(Map<String, Object> table, Progress fallback) {
            return new Progress(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    LuaConfigValues.integer(table, "updateMs",
                            LuaConfigValues.integer(table, "progressUpdateMs", fallback.updateMs)),
                    LuaConfigValues.integer(table, "step",
                            LuaConfigValues.integer(table, "progressStep", fallback.step)),
                    LuaConfigValues.integer(table, "initialDelayMs", fallback.initialDelayMs),
                    LuaConfigValues.integer(table, "cycleDelayMs", fallback.cycleDelayMs),
                    LuaConfigValues.integer(table, "timelineDurationMs", fallback.timelineDurationMs),
                    LuaConfigValues.integer(table, "timelineFrameDelayMs", fallback.timelineFrameDelayMs),
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
                    LuaConfigValues.string(table, "animationConfigResource", fallback.animationConfigResource),
                    stringList(table, "messageKeys", fallback.messageKeys),
                    LuaConfigValues.string(table, "style", fallback.styleName),
                    LuaConfigValues.string(table, "font", fallback.fontName),
                    LuaConfigValues.integer(table, "fontSize", fallback.fontSize),
                    LuaConfigValues.string(table, "fontStyle", fallback.fontStyle),
                    LuaConfigValues.string(table, "textColor", fallback.textColor)
            );
        }

        public boolean enabled() { return enabled; }
        public int updateMs() { return updateMs; }
        public int step() { return step; }
        public int timelineDurationMs() { return timelineDurationMs; }
        public int timelineFrameDelayMs() { return timelineFrameDelayMs; }
        public boolean loop() { return loop; }
        public boolean randomMessages() { return randomMessages; }
        public boolean showText() { return showText; }
        public boolean showPercent() { return showPercent; }
        public String messagesSection() { return messagesSection; }
        public String messagesResource() { return messagesResource; }
        public String animationConfigResource() { return animationConfigResource; }
        public List<String> messageKeys() { return messageKeys; }
        public String styleName() { return styleName; }
        public String fontName() { return fontName; }
        public int fontSize() { return fontSize; }
        public String fontStyle() { return fontStyle; }
        public String textColor() { return textColor; }

        public org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.Options toEngineOptions() {
            return new org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.Options()
                    .setProgressUpdateMs(updateMs)
                    .setProgressStep(step)
                    .setInitialDelayMs(initialDelayMs)
                    .setCycleDelayMs(cycleDelayMs)
                    .setTimelineDurationMs(timelineDurationMs)
                    .setTimelineFrameDelayMs(timelineFrameDelayMs)
                    .setMaxValue(maxValue)
                    .setLoop(loop)
                    .setRandomMessages(randomMessages)
                    .setShowText(showText)
                    .setShowPercent(showPercent)
                    .setResetOnStop(resetOnStop)
                    .setHideOnStop(hideOnStop)
                    .setAnimateEntrance(animateEntrance)
                    .setAnimateExit(animateExit);
        }
    }

    public static final class Transition {
        private final boolean enabled;
        private final Phase entry;
        private final Phase exit;

        private Transition(boolean enabled, Phase entry, Phase exit) {
            this.enabled = enabled;
            this.entry = Objects.requireNonNull(entry, "entry");
            this.exit = Objects.requireNonNull(exit, "exit");
        }

        private static Transition from(Map<String, Object> table, Transition fallback) {
            return new Transition(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    Phase.from(LuaConfigValues.map(table, "entry"), fallback.entry),
                    Phase.from(LuaConfigValues.map(table, "exit"), fallback.exit)
            );
        }

        public boolean enabled() { return enabled; }
        public Phase entry() { return entry; }
        public Phase exit() { return exit; }
        public Phase phase(boolean entryPhase) { return entryPhase ? entry : exit; }
    }

    public static final class Phase {
        private final Motion motion;
        private final Opacity opacity;

        private Phase(Motion motion, Opacity opacity) {
            this.motion = Objects.requireNonNull(motion, "motion");
            this.opacity = Objects.requireNonNull(opacity, "opacity");
        }

        private static Phase from(Map<String, Object> table, Phase fallback) {
            return new Phase(
                    Motion.from(LuaConfigValues.map(table, "motion"), fallback.motion),
                    Opacity.from(LuaConfigValues.map(table, "opacity"), fallback.opacity)
            );
        }

        public Motion motion() { return motion; }
        public Opacity opacity() { return opacity; }

        public int frameDelayMs() {
            int result = Integer.MAX_VALUE;
            if (motion.enabled) {
                result = Math.min(result, motion.frameDelayMs);
            }
            if (opacity.enabled) {
                result = Math.min(result, opacity.frameDelayMs);
            }
            return result == Integer.MAX_VALUE ? 16 : Math.max(1, result);
        }

        public int totalDurationMs() {
            return Math.max(motion.totalDurationMs(), opacity.totalDurationMs());
        }
    }

    public static final class Motion {
        private final boolean enabled;
        private final int delayMs;
        private final int durationMs;
        private final int frameDelayMs;
        private final AnimationCurve curve;
        private final Position from;
        private final Position to;

        private Motion(boolean enabled,
                       int delayMs,
                       int durationMs,
                       int frameDelayMs,
                       AnimationCurve curve,
                       Position from,
                       Position to) {
            this.enabled = enabled;
            this.delayMs = Math.max(0, delayMs);
            this.durationMs = Math.max(0, durationMs);
            this.frameDelayMs = Math.max(1, frameDelayMs);
            this.curve = Objects.requireNonNull(curve, "curve");
            this.from = Objects.requireNonNull(from, "from");
            this.to = Objects.requireNonNull(to, "to");
        }

        private static Motion from(Map<String, Object> table, Motion fallback) {
            if (table == null || table.isEmpty()) {
                return fallback;
            }
            return new Motion(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    LuaConfigValues.integer(table, "delayMs", fallback.delayMs),
                    LuaConfigValues.integer(table, "durationMs", fallback.durationMs),
                    LuaConfigValues.integer(table, "frameDelayMs", fallback.frameDelayMs),
                    ScriptedLoadingUi.curve(table, "easing", fallback.curve),
                    Position.from(LuaConfigValues.map(table, "from"), fallback.from),
                    Position.from(LuaConfigValues.map(table, "to"), fallback.to)
            );
        }

        public boolean enabled() { return enabled; }
        public int delayMs() { return delayMs; }
        public int durationMs() { return durationMs; }
        public int frameDelayMs() { return frameDelayMs; }
        public AnimationCurve curve() { return curve; }
        public Position from() { return from; }
        public Position to() { return to; }
        public int totalDurationMs() { return enabled ? delayMs + durationMs : 0; }
    }

    public static final class Opacity {
        private final boolean enabled;
        private final int delayMs;
        private final int durationMs;
        private final int frameDelayMs;
        private final AnimationCurve curve;
        private final float from;
        private final float to;

        private Opacity(boolean enabled,
                        int delayMs,
                        int durationMs,
                        int frameDelayMs,
                        AnimationCurve curve,
                        float from,
                        float to) {
            this.enabled = enabled;
            this.delayMs = Math.max(0, delayMs);
            this.durationMs = Math.max(0, durationMs);
            this.frameDelayMs = Math.max(1, frameDelayMs);
            this.curve = Objects.requireNonNull(curve, "curve");
            this.from = clamp01(from);
            this.to = clamp01(to);
        }

        private static Opacity from(Map<String, Object> table, Opacity fallback) {
            if (table == null || table.isEmpty()) {
                return fallback;
            }
            return new Opacity(
                    LuaConfigValues.bool(table, "enabled", fallback.enabled),
                    LuaConfigValues.integer(table, "delayMs", fallback.delayMs),
                    LuaConfigValues.integer(table, "durationMs", fallback.durationMs),
                    LuaConfigValues.integer(table, "frameDelayMs", fallback.frameDelayMs),
                    ScriptedLoadingUi.curve(table, "easing", fallback.curve),
                    (float) number(table, "from", fallback.from),
                    (float) number(table, "to", fallback.to)
            );
        }

        public boolean enabled() { return enabled; }
        public int delayMs() { return delayMs; }
        public int durationMs() { return durationMs; }
        public int frameDelayMs() { return frameDelayMs; }
        public AnimationCurve curve() { return curve; }
        public float from() { return from; }
        public float to() { return to; }
        public int totalDurationMs() { return enabled ? delayMs + durationMs : 0; }
    }

    public static final class Position {
        private final String reference;
        private final double referenceX;
        private final double referenceY;
        private final double windowX;
        private final double windowY;
        private final int offsetX;
        private final int offsetY;

        private Position(String reference,
                         double referenceX,
                         double referenceY,
                         double windowX,
                         double windowY,
                         int offsetX,
                         int offsetY) {
            this.reference = reference == null || reference.isBlank()
                    ? "frame"
                    : reference.trim().toLowerCase(Locale.ROOT);
            this.referenceX = referenceX;
            this.referenceY = referenceY;
            this.windowX = windowX;
            this.windowY = windowY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public static Position frame(double frameX,
                                     double frameY,
                                     double windowX,
                                     double windowY,
                                     int offsetX,
                                     int offsetY) {
            return new Position("frame", frameX, frameY, windowX, windowY, offsetX, offsetY);
        }

        public static Position current(int offsetX, int offsetY) {
            return new Position("current", 0, 0, 0, 0, offsetX, offsetY);
        }

        private static Position from(Map<String, Object> table, Position fallback) {
            if (table == null || table.isEmpty()) {
                return fallback;
            }
            Position anchorFallback = namedAnchor(
                    LuaConfigValues.string(table, "anchor", ""),
                    fallback
            );
            return new Position(
                    LuaConfigValues.string(table, "reference", anchorFallback.reference),
                    number(table, "referenceX",
                            number(table, "frameX", anchorFallback.referenceX)),
                    number(table, "referenceY",
                            number(table, "frameY", anchorFallback.referenceY)),
                    number(table, "windowX", anchorFallback.windowX),
                    number(table, "windowY", anchorFallback.windowY),
                    LuaConfigValues.integer(table, "offsetX", anchorFallback.offsetX),
                    LuaConfigValues.integer(table, "offsetY", anchorFallback.offsetY)
            );
        }

        public Point resolve(FloatingWindow window, Point currentPosition) {
            Objects.requireNonNull(window, "window");
            Rectangle referenceBounds;
            switch (reference) {
                case "current" -> {
                    Point current = currentPosition == null ? window.getLocation() : currentPosition;
                    return new Point(current.x + offsetX, current.y + offsetY);
                }
                case "absolute" -> {
                    return new Point(offsetX, offsetY);
                }
                case "screen" -> referenceBounds = screenBounds(window);
                default -> referenceBounds = window.getEngine().getFrame().getBounds();
            }

            double referencePointX = referenceBounds.x + referenceBounds.width * referenceX;
            double referencePointY = referenceBounds.y + referenceBounds.height * referenceY;
            int x = (int) Math.round(referencePointX - window.getWidth() * windowX + offsetX);
            int y = (int) Math.round(referencePointY - window.getHeight() * windowY + offsetY);
            return new Point(x, y);
        }

        public String reference() { return reference; }
        public double referenceX() { return referenceX; }
        public double referenceY() { return referenceY; }
        public double windowX() { return windowX; }
        public double windowY() { return windowY; }
        public int offsetX() { return offsetX; }
        public int offsetY() { return offsetY; }

        private static Position namedAnchor(String anchor, Position fallback) {
            String normalized = anchor == null
                    ? ""
                    : anchor.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            return switch (normalized) {
                case "framecenter", "center" -> frame(0.5, 0.5, 0.5, 0.5, 0, 0);
                case "frametopoutside", "topoutside" -> frame(0.5, 0.0, 0.5, 1.0, 0, 0);
                case "framebottomoutside", "bottomoutside" -> frame(0.5, 1.0, 0.5, 0.0, 0, 0);
                case "frameleftoutside", "leftoutside" -> frame(0.0, 0.5, 1.0, 0.5, 0, 0);
                case "framerightoutside", "rightoutside" -> frame(1.0, 0.5, 0.0, 0.5, 0, 0);
                case "frametop", "top" -> frame(0.5, 0.0, 0.5, 0.0, 0, 0);
                case "framebottom", "bottom" -> frame(0.5, 1.0, 0.5, 1.0, 0, 0);
                case "frameleft", "left" -> frame(0.0, 0.5, 0.0, 0.5, 0, 0);
                case "frameright", "right" -> frame(1.0, 0.5, 1.0, 0.5, 0, 0);
                case "current" -> current(0, 0);
                default -> fallback;
            };
        }

        private static Rectangle screenBounds(FloatingWindow window) {
            try {
                GraphicsConfiguration configuration = window.getGraphicsConfiguration();
                if (configuration != null) {
                    return configuration.getBounds();
                }
                if (!GraphicsEnvironment.isHeadless()) {
                    return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                }
            } catch (Exception ignored) {
                // Fall back to the owner frame below.
            }
            return window.getEngine().getFrame().getBounds();
        }
    }

    private static AnimationCurve curve(Map<String, Object> table,
                                        String key,
                                        AnimationCurve fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof String text) {
            return AnimationCurve.named(text);
        }
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> easing = (Map<String, Object>) rawMap;
            String type = LuaConfigValues.string(easing, "type",
                    LuaConfigValues.string(easing, "name", fallback.name()));
            String normalized = type.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            if (normalized.equals("cubicbezier") || normalized.equals("bezier")) {
                return AnimationCurve.cubicBezier(
                        number(easing, "x1", 0.25),
                        number(easing, "y1", 0.1),
                        number(easing, "x2", 0.25),
                        number(easing, "y2", 1.0)
                );
            }
            return AnimationCurve.named(type);
        }
        return fallback;
    }

    private static List<String> stringList(Map<String, Object> table,
                                           String key,
                                           List<String> fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? fallback : List.of(trimmed);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return fallback;
        }

        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(entry -> numericKey(entry.getKey())));
        List<String> values = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries) {
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                continue;
            }
            String stringValue = String.valueOf(rawValue).trim();
            if (!stringValue.isEmpty()) {
                values.add(stringValue);
            }
        }
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(entry -> numericKey(entry.getKey())));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries) {
            if (entry.getValue() instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) rawMap;
                result.add(typedMap);
            }
        }
        return List.copyOf(result);
    }

    private static double number(Map<String, Object> table, String key, double fallback) {
        Object value = table == null ? null : table.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int numericKey(Object key) {
        if (key instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(key));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeColorString(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
