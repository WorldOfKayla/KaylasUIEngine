package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.animation.AnimationCurve;
import org.takesome.kaylasEngine.gui.scripting.LuaConfigValues;
import org.takesome.kaylasEngine.gui.scripting.UiScriptContext;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;
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

    private static final ScriptedLoadingUi FALLBACK = ScriptedLoadingUiDefaults.create();

    private final Overlay overlay;
    private final Window window;
    private final Loader loader;
    private final Typography typography;
    private final Progress progress;
    private final Transition transition;

    ScriptedLoadingUi(Overlay overlay,
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

    public static ScriptedLoadingUi load(UiScriptContext context,
                                         String scriptPath,
                                         int profileIndex) {
        return ScriptedLoadingUiParser.load(context, scriptPath, profileIndex, FALLBACK);
    }

    public Overlay overlay() { return overlay; }
    public Window window() { return window; }
    public Loader loader() { return loader; }
    public Typography typography() { return typography; }
    public Progress progress() { return progress; }
    public Transition transition() { return transition; }

    public static final class Overlay {
        final boolean enabled;
        final String name;
        final Color color;
        final int targetAlpha;
        final Fade fadeIn;
        final Fade fadeOut;
        final Bounds bounds;

        Overlay(boolean enabled,
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
        final int durationMs;
        final int frameDelayMs;
        final AnimationCurve curve;

        Fade(int durationMs, int frameDelayMs, AnimationCurve curve) {
            this.durationMs = Math.max(0, durationMs);
            this.frameDelayMs = Math.max(1, frameDelayMs);
            this.curve = Objects.requireNonNull(curve, "curve");
        }

        public int durationMs() { return durationMs; }
        public int frameDelayMs() { return frameDelayMs; }
        public AnimationCurve curve() { return curve; }
    }

    public static final class Window {
        final int width;
        final int height;
        final boolean alwaysOnTop;
        final int cornerRadius;

        Window(int width, int height, boolean alwaysOnTop, int cornerRadius) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.alwaysOnTop = alwaysOnTop;
            this.cornerRadius = Math.max(0, cornerRadius);
        }

        public int width() { return width; }
        public int height() { return height; }
        public boolean alwaysOnTop() { return alwaysOnTop; }
        public int cornerRadius() { return cornerRadius; }
    }

    public static final class Loader {
        final boolean enabled;
        final String spritePath;
        final int rows;
        final int columns;
        final int frameDelayMs;
        final Bounds bounds;
        final Background background;
        final String titleColor;
        final String messageColor;

        Loader(boolean enabled,
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
            this.titleColor = LoadingUiConfigSupport.normalizeColorString(titleColor, "#ffffff");
            this.messageColor = LoadingUiConfigSupport.normalizeColorString(messageColor, "#ffffff");
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
        final String image;
        final String color;

        Background(String image, String color) {
            this.image = image == null ? "" : image.trim();
            this.color = LoadingUiConfigSupport.normalizeColorString(color, "#00000000");
        }

        public String image() { return image; }
        public String color() { return color; }
    }

    public static final class Bounds {
        final int x;
        final int y;
        final int width;
        final int height;

        Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
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
        final TextStyle title;
        final TextStyle message;

        Typography(TextStyle title, TextStyle message) {
            this.title = Objects.requireNonNull(title, "title");
            this.message = Objects.requireNonNull(message, "message");
        }

        public TextStyle title() { return title; }
        public TextStyle message() { return message; }
    }

    public static final class TextStyle {
        final String styleName;
        final String fontName;
        final int fontSize;
        final String fontStyle;
        final String color;

        TextStyle(String styleName,
                          String fontName,
                          int fontSize,
                          String fontStyle,
                          String color) {
            this.styleName = LoadingUiConfigSupport.normalize(styleName);
            this.fontName = LoadingUiConfigSupport.normalize(fontName);
            this.fontSize = Math.max(0, fontSize);
            this.fontStyle = LoadingUiConfigSupport.normalize(fontStyle);
            this.color = LoadingUiConfigSupport.normalize(color);
        }

        public String styleName() { return styleName; }
        public String fontName() { return fontName; }
        public int fontSize() { return fontSize; }
        public String fontStyle() { return fontStyle; }
        public String color() { return color; }
    }

    public static final class Progress {
        final boolean enabled;
        final int updateMs;
        final int step;
        final int initialDelayMs;
        final int cycleDelayMs;
        final int timelineDurationMs;
        final int timelineFrameDelayMs;
        final int maxValue;
        final boolean loop;
        final boolean randomMessages;
        final boolean showText;
        final boolean showPercent;
        final boolean resetOnStop;
        final boolean hideOnStop;
        final boolean animateEntrance;
        final boolean animateExit;
        final String messagesSection;
        final String messagesResource;
        final String animationConfigResource;
        final List<String> messageKeys;
        final String styleName;
        final String fontName;
        final int fontSize;
        final String fontStyle;
        final String textColor;

        Progress(boolean enabled,
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
            this.messagesResource = LoadingUiConfigSupport.normalize(messagesResource);
            this.animationConfigResource = LoadingUiConfigSupport.normalize(animationConfigResource);
            this.messageKeys = List.copyOf(messageKeys == null ? List.of() : messageKeys);
            this.styleName = styleName == null || styleName.isBlank() ? "progressMini" : styleName.trim();
            this.fontName = LoadingUiConfigSupport.normalize(fontName);
            this.fontSize = Math.max(0, fontSize);
            this.fontStyle = LoadingUiConfigSupport.normalize(fontStyle);
            this.textColor = LoadingUiConfigSupport.normalize(textColor);
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
            return LoadingUiProgressAdapter.toEngineOptions(this);
        }

    }

    public static final class Transition {
        final boolean enabled;
        final Phase entry;
        final Phase exit;

        Transition(boolean enabled, Phase entry, Phase exit) {
            this.enabled = enabled;
            this.entry = Objects.requireNonNull(entry, "entry");
            this.exit = Objects.requireNonNull(exit, "exit");
        }

        public boolean enabled() { return enabled; }
        public Phase entry() { return entry; }
        public Phase exit() { return exit; }
        public Phase phase(boolean entryPhase) { return entryPhase ? entry : exit; }
    }

    public static final class Phase {
        final Motion motion;
        final Opacity opacity;

        Phase(Motion motion, Opacity opacity) {
            this.motion = Objects.requireNonNull(motion, "motion");
            this.opacity = Objects.requireNonNull(opacity, "opacity");
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
        final boolean enabled;
        final int delayMs;
        final int durationMs;
        final int frameDelayMs;
        final AnimationCurve curve;
        final Position from;
        final Position to;

        Motion(boolean enabled,
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
        final boolean enabled;
        final int delayMs;
        final int durationMs;
        final int frameDelayMs;
        final AnimationCurve curve;
        final float from;
        final float to;

        Opacity(boolean enabled,
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
            this.from = LoadingUiConfigSupport.clamp01(from);
            this.to = LoadingUiConfigSupport.clamp01(to);
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
        final String reference;
        final double referenceX;
        final double referenceY;
        final double windowX;
        final double windowY;
        final int offsetX;
        final int offsetY;

        Position(String reference,
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

        public Point resolve(FloatingWindow window, Point currentPosition) {
            return LoadingUiPositionResolver.resolve(this, window, currentPosition);
        }

        public String reference() { return reference; }
        public double referenceX() { return referenceX; }
        public double referenceY() { return referenceY; }
        public double windowX() { return windowX; }
        public double windowY() { return windowY; }
        public int offsetX() { return offsetX; }
        public int offsetY() { return offsetY; }

    }

}
