package org.takesome.kaylasEngine.gui.components.progressBar;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;

import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Function;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/** Resolves a complete progress-bar configuration from style and component-level overrides. */
public final class ProgressBarStyle {
    private static final String COMPONENT_TYPE = "progressBar";
    private static final String DEFAULT_STYLE = "default";

    private final ComponentFactory componentFactory;
    private final ComponentAttributes attributes;
    private final StyleAttributes style;
    private final String styleName;

    public ProgressBarStyle(ComponentFactory componentFactory, ComponentAttributes attributes) {
        this(
                componentFactory,
                attributes,
                Objects.requireNonNull(componentFactory, "componentFactory").getStyle(),
                attributes == null ? DEFAULT_STYLE : valueOrStatic(attributes.getComponentStyle(), DEFAULT_STYLE)
        );
    }

    private ProgressBarStyle(ComponentFactory componentFactory,
                             ComponentAttributes attributes,
                             StyleAttributes style,
                             String styleName) {
        this.componentFactory = Objects.requireNonNull(componentFactory, "componentFactory");
        this.attributes = attributes;
        this.style = Objects.requireNonNullElseGet(style, () -> StyleAttributes.defaults(styleName));
        this.styleName = valueOrStatic(styleName, DEFAULT_STYLE);
    }

    /** Applies visual policy and initial model/text values while a component is being created. */
    public void apply(ProgressBar progressBar) {
        Objects.requireNonNull(progressBar, "progressBar");
        applyVisual(progressBar);

        progressBar.setRange(resolveMinimum(), resolveMaximum(), resolveInitialValue());
        progressBar.setIndeterminate(bool(attribute(ComponentAttributes::getIndeterminate), false));

        String progressText = attribute(ComponentAttributes::getProgressText);
        String localeKey = attribute(ComponentAttributes::getLocaleKey);
        if ((progressText == null || progressText.isBlank()) && localeKey != null && !localeKey.isBlank()) {
            String localized = componentFactory.getLangProvider().getString(localeKey);
            if (!localeKey.equals(localized)) {
                progressText = localized;
            }
        }
        if (progressText != null && !progressText.isBlank()) {
            progressBar.setString(progressText);
        }

        ProgressBar.Configuration configuration = progressBar.getConfiguration();
        Engine.getLOGGER().debug(
                "ProgressBar style applied: id={} style={} range={}..{} value={} orientation={} stringPainted={} showPercent={} font={} fontSize={} fontStyle={} textColor={} striped={} textureMode={}",
                componentId(),
                progressBar.getStyleName(),
                progressBar.getMinimum(),
                progressBar.getMaximum(),
                progressBar.getValue(),
                configuration.orientation() == SwingConstants.VERTICAL ? "vertical" : "horizontal",
                configuration.stringPainted(),
                configuration.showPercent(),
                configuration.font().getFamily(),
                configuration.font().getSize(),
                configuration.font().getStyle(),
                configuration.textColor(),
                configuration.striped(),
                configuration.textureMode()
        );
    }

    /** Applies only visual policy, preserving range, value, indeterminate state and custom text. */
    public void applyVisual(ProgressBar progressBar) {
        Objects.requireNonNull(progressBar, "progressBar");
        progressBar.configure(configuration());
        progressBar.setStyleName(styleName);
    }

    /**
     * Applies a named progressBar style to an existing component without resetting its model state.
     *
     * @return the actually applied style name; {@code default} when the requested style is missing.
     */
    public static String applyNamedStyle(ComponentFactory componentFactory,
                                         ProgressBar progressBar,
                                         String requestedStyleName) {
        Objects.requireNonNull(componentFactory, "componentFactory");
        Objects.requireNonNull(progressBar, "progressBar");

        String requested = valueOrStatic(requestedStyleName, DEFAULT_STYLE);
        boolean available = componentFactory.getEngine().getStyleProvider().hasStyle(COMPONENT_TYPE, requested);
        String resolved = available ? requested : DEFAULT_STYLE;
        if (!available) {
            Engine.getLOGGER().warn(
                    "ProgressBar style '{}' is not defined; applying '{}'.",
                    requested,
                    resolved
            );
        }

        StyleAttributes style = componentFactory.getEngine().getStyleProvider().getStyle(COMPONENT_TYPE, resolved);
        new ProgressBarStyle(componentFactory, null, style, resolved).applyVisual(progressBar);
        return resolved;
    }

    private ProgressBar.Configuration configuration() {
        return new ProgressBar.Configuration(
                color(valueOr(attribute(ComponentAttributes::getTrackColor),
                        valueOr(attribute(ComponentAttributes::getBackground), style.getTrackColor())), style.getTrackColor()),
                color(valueOr(attribute(ComponentAttributes::getFillColor),
                        valueOr(attribute(ComponentAttributes::getColor), style.getFillColor())), style.getFillColor()),
                color(borderColor(), "#00000000"),
                color(valueOr(attribute(ComponentAttributes::getTextColor), style.getTextColor()), style.getTextColor()),
                color(valueOr(attribute(ComponentAttributes::getTextShadowColor), style.getTextShadowColor()), style.getTextShadowColor()),
                image(valueOr(attribute(ComponentAttributes::getTrackImage), style.getTrackImage())),
                image(valueOr(attribute(ComponentAttributes::getFillImage), style.getTexture())),
                ProgressBar.TextureMode.from(valueOr(attribute(ComponentAttributes::getTextureMode), style.getTextureMode())),
                font(),
                integer(attribute(ComponentAttributes::getBorderWidth), style.getBorderWidth()),
                componentPositiveInt(ComponentAttributes::getBorderRadius, style.getBorderRadius()),
                integer(attribute(ComponentAttributes::getFillBorderRadius), style.getFillBorderRadius()),
                integer(attribute(ComponentAttributes::getTrackPadding), style.getTrackPadding()),
                bool(attribute(ComponentAttributes::getBorderPainted), style.isBorderPainted()),
                bool(attribute(ComponentAttributes::getStringPainted), style.isStringPainted()),
                bool(attribute(ComponentAttributes::getShowPercent), style.isShowPercent()),
                bool(attribute(ComponentAttributes::getTextShadow), style.isTextShadow()),
                integer(attribute(ComponentAttributes::getTextShadowOffsetX), style.getTextShadowOffsetX()),
                integer(attribute(ComponentAttributes::getTextShadowOffsetY), style.getTextShadowOffsetY()),
                valueOr(attribute(ComponentAttributes::getProgressTextFormat), style.getTextFormat()),
                textAlignment(valueOr(attribute(ComponentAttributes::getAlignment), style.getAlign())),
                orientation(valueOr(attribute(ComponentAttributes::getOrientation), style.getOrientation())),
                bool(attribute(ComponentAttributes::getInverted), style.isInverted()),
                bool(attribute(ComponentAttributes::getStriped), style.isStriped()),
                color(valueOr(attribute(ComponentAttributes::getStripeColor), style.getStripeColor()), style.getStripeColor()),
                integer(attribute(ComponentAttributes::getStripeWidth), style.getStripeWidth()),
                integer(attribute(ComponentAttributes::getStripeGap), style.getStripeGap()),
                integer(attribute(ComponentAttributes::getStripeSpeedMs), style.getStripeSpeedMs()),
                bool(attribute(ComponentAttributes::getAnimateValue), style.isAnimateValue()),
                integer(attribute(ComponentAttributes::getAnimationDurationMs), style.getAnimationDurationMs()),
                integer(attribute(ComponentAttributes::getAnimationFrameDelayMs), style.getAnimationFrameDelayMs()),
                integer(attribute(ComponentAttributes::getIndeterminateCycleMs), style.getIndeterminateCycleMs()),
                integer(attribute(ComponentAttributes::getIndeterminateSizePercent), style.getIndeterminateSizePercent()),
                bool(attribute(ComponentAttributes::getAntialias), style.isAntialias())
        );
    }

    private int resolveMinimum() {
        return attributes == null ? 0 : attributes.getMinValue();
    }

    private int resolveMaximum() {
        int minimum = resolveMinimum();
        int maximum = attributes == null ? minimum + 100 : attributes.getMaxValue();
        return maximum > minimum ? maximum : minimum + 100;
    }

    private int resolveInitialValue() {
        Object rawValue = attributes == null ? null : attributes.getInitialValue();
        if (rawValue == null) {
            return resolveMinimum();
        }
        try {
            return Integer.parseInt(String.valueOf(rawValue).trim());
        } catch (NumberFormatException error) {
            Engine.getLOGGER().warn(
                    "Invalid progress-bar initialValue '{}' for component {}",
                    rawValue,
                    componentId()
            );
            return resolveMinimum();
        }
    }

    private String borderColor() {
        String explicitBorderColor = attribute(ComponentAttributes::getBorderColor);
        if (explicitBorderColor != null && !explicitBorderColor.isBlank()) {
            return explicitBorderColor.trim();
        }
        String componentBorder = attribute(ComponentAttributes::getBorder);
        if (componentBorder != null
                && componentBorder.trim().matches("^#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            return componentBorder.trim();
        }
        return style.getBorderColor();
    }

    private Font font() {
        int componentFontSize = attributes == null ? 0 : attributes.getFontSize();
        int fontSize = componentFontSize > 0 ? componentFontSize : style.getFontSize();
        String fontName = valueOr(attribute(ComponentAttributes::getFont), style.getFont());
        String fontStyle = valueOr(attribute(ComponentAttributes::getFontStyle), style.getFontStyle());
        return componentFactory.getEngine().getFONTUTILS().getFont(fontName, fontSize, fontStyle);
    }

    private BufferedImage image(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return componentFactory.getEngine().getImageUtils().getLocalImage(path);
        } catch (RuntimeException error) {
            Engine.getLOGGER().warn("Unable to load progress-bar image '{}': {}", path, error.getMessage());
            return null;
        }
    }

    private Color color(String value, String fallback) {
        return hexToColor(valueOr(value, fallback));
    }

    private int orientation(String value) {
        return value != null && value.trim().equalsIgnoreCase("vertical")
                ? SwingConstants.VERTICAL
                : SwingConstants.HORIZONTAL;
    }

    private int textAlignment(String value) {
        if (value == null) {
            return SwingConstants.CENTER;
        }
        return switch (value.trim().toLowerCase()) {
            case "left", "start" -> SwingConstants.LEFT;
            case "right", "end" -> SwingConstants.RIGHT;
            default -> SwingConstants.CENTER;
        };
    }

    private <T> T attribute(Function<ComponentAttributes, T> reader) {
        return attributes == null ? null : reader.apply(attributes);
    }

    private int componentPositiveInt(Function<ComponentAttributes, Integer> reader, int fallback) {
        if (attributes == null) {
            return fallback;
        }
        Integer value = reader.apply(attributes);
        return value != null && value > 0 ? value : fallback;
    }

    private boolean bool(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private int integer(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String componentId() {
        return attributes == null ? "<runtime>" : valueOr(attributes.getComponentId(), "<unnamed>");
    }

    private String valueOr(String value, String fallback) {
        return valueOrStatic(value, fallback);
    }

    private static String valueOrStatic(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
