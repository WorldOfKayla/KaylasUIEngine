package org.takesome.kaylasEngine.gui.components.progressBar;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleValue;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Composite progress component with independently styled track, fill and text layers.
 *
 * <p>The component deliberately does not extend {@code JProgressBar}. It owns a
 * {@link DefaultBoundedRangeModel} and exposes the familiar progress API while keeping visual layers
 * independent and script/style friendly.</p>
 */
public final class ProgressBar extends CompositeComponent implements SwingConstants {
    public enum TextureMode {
        STRETCH,
        TILE,
        COVER,
        CONTAIN;

        public static TextureMode from(String value) {
            if (value == null || value.isBlank()) {
                return STRETCH;
            }
            try {
                return TextureMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return STRETCH;
            }
        }
    }

    /** Immutable visual policy resolved from style plus component-level overrides. */
    public record Configuration(
            Color trackColor,
            Color fillColor,
            Color borderColor,
            Color textColor,
            Color textShadowColor,
            BufferedImage trackTexture,
            BufferedImage fillTexture,
            TextureMode textureMode,
            Font font,
            int borderWidth,
            int borderRadius,
            int fillBorderRadius,
            int trackPadding,
            boolean borderPainted,
            boolean stringPainted,
            boolean showPercent,
            boolean textShadow,
            int textShadowOffsetX,
            int textShadowOffsetY,
            String textFormat,
            int textAlignment,
            int orientation,
            boolean inverted,
            boolean striped,
            Color stripeColor,
            int stripeWidth,
            int stripeGap,
            int stripeSpeedMs,
            boolean animateValue,
            int animationDurationMs,
            int animationFrameDelayMs,
            int indeterminateCycleMs,
            int indeterminateSizePercent,
            boolean antialias
    ) {
        public Configuration {
            trackColor = trackColor == null ? new Color(0, 0, 0, 0) : trackColor;
            fillColor = fillColor == null ? Color.WHITE : fillColor;
            borderColor = borderColor == null ? new Color(0, 0, 0, 0) : borderColor;
            textColor = textColor == null ? Color.WHITE : textColor;
            textShadowColor = textShadowColor == null ? new Color(0, 0, 0, 150) : textShadowColor;
            textureMode = textureMode == null ? TextureMode.STRETCH : textureMode;
            font = font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : font;
            borderWidth = Math.max(0, borderWidth);
            borderRadius = Math.max(0, borderRadius);
            fillBorderRadius = Math.max(0, fillBorderRadius);
            trackPadding = Math.max(0, trackPadding);
            textFormat = textFormat == null || textFormat.isBlank() ? "{percent}%" : textFormat;
            textAlignment = normalizeAlignment(textAlignment);
            orientation = orientation == VERTICAL ? VERTICAL : HORIZONTAL;
            stripeColor = stripeColor == null ? new Color(255, 255, 255, 38) : stripeColor;
            stripeWidth = Math.max(1, stripeWidth);
            stripeGap = Math.max(0, stripeGap);
            stripeSpeedMs = Math.max(0, stripeSpeedMs);
            animationDurationMs = Math.max(1, animationDurationMs);
            animationFrameDelayMs = Math.max(1, animationFrameDelayMs);
            indeterminateCycleMs = Math.max(100, indeterminateCycleMs);
            indeterminateSizePercent = Math.max(5, Math.min(100, indeterminateSizePercent));
        }

        public static Configuration defaults() {
            return new Configuration(
                    new Color(24, 28, 30, 255),
                    new Color(176, 151, 107, 255),
                    new Color(24, 56, 36, 255),
                    Color.WHITE,
                    new Color(0, 0, 0, 160),
                    null,
                    null,
                    TextureMode.STRETCH,
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12),
                    1,
                    8,
                    6,
                    2,
                    true,
                    false,
                    true,
                    true,
                    1,
                    1,
                    "{percent}%",
                    CENTER,
                    HORIZONTAL,
                    false,
                    false,
                    new Color(255, 255, 255, 35),
                    12,
                    8,
                    0,
                    true,
                    140,
                    16,
                    1200,
                    28,
                    true
            );
        }

        private static int normalizeAlignment(int alignment) {
            return alignment == LEFT || alignment == RIGHT ? alignment : CENTER;
        }
    }

    private final DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(0, 0, 0, 100);
    private final TrackLayer trackLayer = new TrackLayer();
    private final FillLayer fillLayer = new FillLayer();
    private final HearthstoneEffectLayer hearthstoneEffectLayer = new HearthstoneEffectLayer();
    private final ProgressTextLabel textLayer = new ProgressTextLabel();

    private Configuration configuration = Configuration.defaults();
    private AnimationEngine.Handle valueAnimation;
    private AnimationEngine.Handle visualAnimation;
    private int displayedValue;
    private int animationStartValue;
    private int animationTargetValue;
    private boolean immediateValueUpdate;
    private long animationStartedAt;
    private long visualStartedAt;
    private boolean indeterminate;
    private float animationOpacity = 1.0f;
    private float contentAnimationOpacity = 1.0f;
    private int contentAnimationOffsetY;
    private float contentAnimationScaleX = 1.0f;
    private float contentAnimationScaleY = 1.0f;
    private int textOffsetX;
    private float glowIntensity;
    private float shinePosition = -1.0f;
    private String progressString;
    private String styleName = "default";

    public ProgressBar() {
        super(LayoutMode.ABSOLUTE);
        setOpaque(false);
        setDoubleBuffered(true);

        trackLayer.setName("track");
        fillLayer.setName("fill");
        hearthstoneEffectLayer.setName("hearthstoneEffect");
        textLayer.setName("text");

        addSubComponent(trackLayer);
        addSubComponent(fillLayer);
        addSubComponent(hearthstoneEffectLayer);
        addSubComponent(textLayer);
        setComponentZOrder(textLayer, 0);
        setComponentZOrder(hearthstoneEffectLayer, 1);
        setComponentZOrder(fillLayer, 2);
        setComponentZOrder(trackLayer, 3);

        model.addChangeListener(event -> onModelChanged());
        configure(configuration);
    }

    public void configure(Configuration configuration) {
        Configuration resolved = Objects.requireNonNullElseGet(configuration, Configuration::defaults);
        runOnEdt(() -> applyConfiguration(resolved));
    }

    private void applyConfiguration(Configuration configuration) {
        this.configuration = configuration;
        textLayer.setFont(this.configuration.font());
        textLayer.setForeground(this.configuration.textColor());
        textLayer.setHorizontalAlignment(this.configuration.textAlignment());
        textLayer.setVisible(this.configuration.stringPainted());
        trackLayer.setOpaque(false);
        fillLayer.setOpaque(false);
        updateText();
        updateVisualTimer();
        revalidate();
        repaint();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void doLayout() {
        int width = Math.max(0, getWidth());
        int height = Math.max(0, getHeight());
        trackLayer.setBounds(0, 0, width, height);
        hearthstoneEffectLayer.setBounds(0, 0, width, height);
        updateContentLayerBounds();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updateVisualTimer();
    }

    @Override
    public void removeNotify() {
        stopAnimation(valueAnimation);
        valueAnimation = null;
        stopAnimation(visualAnimation);
        visualAnimation = null;
        super.removeNotify();
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleProgressBar();
        }
        return accessibleContext;
    }

    protected final class AccessibleProgressBar extends AccessibleJComponent implements AccessibleValue {
        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PROGRESS_BAR;
        }

        @Override
        public AccessibleValue getAccessibleValue() {
            return this;
        }

        @Override
        public Number getCurrentAccessibleValue() {
            return ProgressBar.this.getValue();
        }

        @Override
        public boolean setCurrentAccessibleValue(Number value) {
            if (value == null) {
                return false;
            }
            ProgressBar.this.setValue(value.intValue());
            return true;
        }

        @Override
        public Number getMinimumAccessibleValue() {
            return ProgressBar.this.getMinimum();
        }

        @Override
        public Number getMaximumAccessibleValue() {
            return ProgressBar.this.getMaximum();
        }
    }

    @Override
    public Integer getValue() {
        return model.getValue();
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Number number) {
            setValue(number.intValue());
            return;
        }
        if (value != null) {
            try {
                setValue(Integer.parseInt(String.valueOf(value).trim()));
                return;
            } catch (NumberFormatException ignored) {
                // Keep the current value for invalid script/config input.
            }
        }
        super.setValue(value);
    }

    public void setValue(int value) {
        runOnEdt(() -> model.setValue(clamp(value, getMinimum(), getMaximum())));
    }

    /** Replaces both model and painted value without carrying interpolation across lifecycle cycles. */
    public void setValueImmediately(int value) {
        runOnEdt(() -> {
            stopAnimation(valueAnimation);
            valueAnimation = null;
            immediateValueUpdate = true;
            try {
                model.setValue(clamp(value, getMinimum(), getMaximum()));
            } finally {
                immediateValueUpdate = false;
            }
            displayedValue = model.getValue();
            updateLayers();
        });
    }

    public int getMinimum() {
        return model.getMinimum();
    }

    public void setMinimum(int minimum) {
        runOnEdt(() -> {
            int maximum = Math.max(minimum + 1, getMaximum());
            model.setRangeProperties(clamp(getValue(), minimum, maximum), 0, minimum, maximum, false);
        });
    }

    public int getMaximum() {
        return model.getMaximum();
    }

    public void setMaximum(int maximum) {
        runOnEdt(() -> {
            int minimum = Math.min(getMinimum(), maximum - 1);
            model.setRangeProperties(clamp(getValue(), minimum, maximum), 0, minimum, maximum, false);
        });
    }

    public void setRange(int minimum, int maximum, int value) {
        runOnEdt(() -> {
            int resolvedMaximum = Math.max(minimum + 1, maximum);
            model.setRangeProperties(clamp(value, minimum, resolvedMaximum), 0, minimum, resolvedMaximum, false);
            displayedValue = model.getValue();
            updateLayers();
        });
    }

    public double getPercentComplete() {
        int span = getMaximum() - getMinimum();
        if (span <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (getValue() - getMinimum()) / (double) span));
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setIndeterminate(boolean indeterminate) {
        runOnEdt(() -> {
            this.indeterminate = indeterminate;
            visualStartedAt = System.nanoTime();
            updateVisualTimer();
            updateLayers();
        });
    }

    public boolean isStringPainted() {
        return configuration.stringPainted();
    }

    public void setStringPainted(boolean stringPainted) {
        configure(copyConfiguration(stringPainted, configuration.orientation(), configuration.inverted()));
    }

    public boolean isShowPercent() {
        return configuration.showPercent();
    }

    public void setShowPercent(boolean showPercent) {
        Configuration c = configuration;
        configure(new Configuration(
                c.trackColor(), c.fillColor(), c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), showPercent, c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public String getString() {
        return formattedString();
    }

    public void setString(String progressString) {
        runOnEdt(() -> {
            this.progressString = progressString;
            updateText();
        });
    }

    public int getOrientation() {
        return configuration.orientation();
    }

    public void setOrientation(int orientation) {
        configure(copyConfiguration(configuration.stringPainted(), orientation, configuration.inverted()));
    }

    public boolean isInverted() {
        return configuration.inverted();
    }

    public void setInverted(boolean inverted) {
        configure(copyConfiguration(configuration.stringPainted(), configuration.orientation(), inverted));
    }

    public JLabel getTextLabel() {
        return textLayer;
    }

    public String getStyleName() {
        return styleName;
    }

    public void setStyleName(String styleName) {
        String resolvedStyle = styleName == null || styleName.isBlank() ? "default" : styleName.trim();
        runOnEdt(() -> {
            this.styleName = resolvedStyle;
            visualStartedAt = System.nanoTime();
            putClientProperty("kaylas.ui.progress.style", this.styleName);
            updateVisualTimer();
            updateLayers();
        });
    }

    public Font getTextFont() {
        return configuration.font();
    }

    public void setTextFont(Font font) {
        if (font == null) {
            return;
        }
        Configuration c = configuration;
        configure(new Configuration(
                c.trackColor(), c.fillColor(), c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), font, c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public int getTextOffsetX() {
        return textOffsetX;
    }

    /** Applies a layout-owned horizontal offset to progress text. */
    public void setTextOffsetX(int textOffsetX) {
        runOnEdt(() -> {
            this.textOffsetX = textOffsetX;
            textLayer.repaint();
        });
    }

    public Color getTextColor() {
        return configuration.textColor();
    }

    public void setTextColor(Color color) {
        Configuration c = configuration;
        configure(new Configuration(
                c.trackColor(), c.fillColor(), c.borderColor(), color, c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public Color getTrackColor() {
        return configuration.trackColor();
    }

    public void setTrackColor(Color color) {
        Configuration c = configuration;
        configure(new Configuration(
                color, c.fillColor(), c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public Color getFillColor() {
        return configuration.fillColor();
    }

    public void setFillColor(Color color) {
        Configuration c = configuration;
        configure(new Configuration(
                c.trackColor(), color, c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public void setTextFormat(String textFormat) {
        Configuration c = configuration;
        configure(new Configuration(
                c.trackColor(), c.fillColor(), c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                c.stringPainted(), c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                textFormat, c.textAlignment(), c.orientation(), c.inverted(), c.striped(),
                c.stripeColor(), c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        ));
    }

    public JComponent getTrackLayer() {
        return trackLayer;
    }

    public JComponent getFillLayer() {
        return fillLayer;
    }

    public void setAnimationOpacity(double value) {
        runOnEdt(() -> {
            animationOpacity = (float) Math.max(0.0, Math.min(1.0, value));
            repaint();
        });
    }

    public float getAnimationOpacity() {
        return animationOpacity;
    }

    /** Returns whether this component currently uses the Hearthstone content-slide profile. */
    public boolean usesHearthstoneEffect() {
        return isHearthstoneStyle();
    }

    /** Moves only the fill and text vertically; the track/frame remains at its original bounds. */
    public void setContentAnimationOffsetY(int offsetY) {
        runOnEdt(() -> {
            contentAnimationOffsetY = offsetY;
            updateContentLayerBounds();
            hearthstoneEffectLayer.repaint();
        });
    }

    public int getContentAnimationOffsetY() {
        return contentAnimationOffsetY;
    }

    /** Scales only fill, leading edge and progress text around the fixed track center. */
    public void setContentAnimationScale(double scaleX, double scaleY) {
        runOnEdt(() -> {
            contentAnimationScaleX = (float) Math.max(0.01, Math.min(2.0, scaleX));
            contentAnimationScaleY = (float) Math.max(0.01, Math.min(2.0, scaleY));
            fillLayer.repaint();
            hearthstoneEffectLayer.repaint();
            textLayer.repaint();
        });
    }

    public float getContentAnimationScaleX() {
        return contentAnimationScaleX;
    }

    public float getContentAnimationScaleY() {
        return contentAnimationScaleY;
    }

    /** Applies opacity only to the fill, leading edge and text, not to the frame. */
    public void setContentAnimationOpacity(double value) {
        runOnEdt(() -> {
            contentAnimationOpacity = (float) Math.max(0.0, Math.min(1.0, value));
            fillLayer.repaint();
            hearthstoneEffectLayer.repaint();
            textLayer.repaint();
        });
    }

    public float getContentAnimationOpacity() {
        return contentAnimationOpacity;
    }

    public void resetContentAnimation() {
        runOnEdt(() -> {
            contentAnimationOffsetY = 0;
            contentAnimationOpacity = 1.0f;
            contentAnimationScaleX = 1.0f;
            contentAnimationScaleY = 1.0f;
            updateContentLayerBounds();
            repaint();
        });
    }

    public void setGlowIntensity(double value) {
        runOnEdt(() -> {
            glowIntensity = (float) Math.max(0.0, Math.min(1.0, value));
            fillLayer.repaint();
        });
    }

    public float getGlowIntensity() {
        return glowIntensity;
    }

    public void setShinePosition(double value) {
        runOnEdt(() -> {
            shinePosition = (float) Math.max(-1.0, Math.min(2.0, value));
            fillLayer.repaint();
        });
    }

    public float getShinePosition() {
        return shinePosition;
    }

    public void resetAnimationEffects() {
        runOnEdt(() -> {
            animationOpacity = 1.0f;
            contentAnimationOpacity = 1.0f;
            contentAnimationOffsetY = 0;
            contentAnimationScaleX = 1.0f;
            contentAnimationScaleY = 1.0f;
            glowIntensity = 0.0f;
            shinePosition = -1.0f;
            updateContentLayerBounds();
            repaint();
        });
    }

    public void addChangeListener(ChangeListener listener) {
        model.addChangeListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        model.removeChangeListener(listener);
    }

    private void onModelChanged() {
        int target = model.getValue();
        if (immediateValueUpdate || !configuration.animateValue() || !isShowing()) {
            displayedValue = target;
            updateLayers();
            return;
        }
        animateDisplayedValueTo(target);
    }

    private void animateDisplayedValueTo(int target) {
        stopAnimation(valueAnimation);
        animationStartValue = displayedValue;
        animationTargetValue = target;
        animationStartedAt = System.nanoTime();
        valueAnimation = AnimationEngine.shared().tween(
                "progress:value",
                configuration.animationDurationMs(),
                configuration.animationFrameDelayMs(),
                AnimationEngine.shared().curve("easeOutCubic"),
                eased -> {
                    displayedValue = (int) Math.round(animationStartValue
                            + (animationTargetValue - animationStartValue) * eased);
                    updateLayers();
                },
                () -> {
                    valueAnimation = null;
                    displayedValue = animationTargetValue;
                    updateLayers();
                }
        );
    }

    private void updateVisualTimer() {
        boolean animatedVisuals = indeterminate
                || isHearthstoneStyle()
                || (configuration.striped() && configuration.stripeSpeedMs() > 0);
        if (!animatedVisuals || !isDisplayable()) {
            stopAnimation(visualAnimation);
            visualAnimation = null;
            return;
        }
        if (visualAnimation != null && visualAnimation.isActive()) {
            return;
        }
        visualStartedAt = System.nanoTime();
        visualAnimation = AnimationEngine.shared().schedule(
                "progress:visual",
                configuration.animationFrameDelayMs(),
                (now, delta) -> {
                    if (!isDisplayable()) {
                        visualAnimation = null;
                        return false;
                    }
                    updateLayers();
                    return true;
                }
        );
    }

    private void updateLayers() {
        updateFillBounds();
        updateText();
        trackLayer.repaint();
        fillLayer.repaint();
        hearthstoneEffectLayer.repaint();
        textLayer.repaint();
    }

    private void updateFillBounds() {
        Rectangle area = contentArea();
        if (area.width <= 0 || area.height <= 0) {
            fillLayer.setBounds(0, 0, 0, 0);
            return;
        }

        if (indeterminate) {
            setIndeterminateBounds(area);
            return;
        }

        double percent = displayedPercent();
        if (configuration.orientation() == VERTICAL) {
            int fillHeight = (int) Math.round(area.height * percent);
            int y = configuration.inverted() ? area.y : area.y + area.height - fillHeight;
            setFillLayerBounds(area.x, y, area.width, Math.max(0, fillHeight));
        } else {
            int fillWidth = (int) Math.round(area.width * percent);
            int x = configuration.inverted() ? area.x + area.width - fillWidth : area.x;
            setFillLayerBounds(x, area.y, Math.max(0, fillWidth), area.height);
        }
    }

    private void setIndeterminateBounds(Rectangle area) {
        double cycleProgress = ((System.nanoTime() - visualStartedAt) / 1_000_000.0
                % configuration.indeterminateCycleMs()) / configuration.indeterminateCycleMs();
        double travel = cycleProgress * 2.0;
        double pingPong = travel <= 1.0 ? travel : 2.0 - travel;
        double segment = configuration.indeterminateSizePercent() / 100.0;

        if (configuration.orientation() == VERTICAL) {
            int segmentHeight = Math.max(1, (int) Math.round(area.height * segment));
            int travelHeight = Math.max(0, area.height - segmentHeight);
            int y = area.y + (int) Math.round(travelHeight * pingPong);
            if (!configuration.inverted()) {
                y = area.y + travelHeight - (y - area.y);
            }
            setFillLayerBounds(area.x, y, area.width, segmentHeight);
        } else {
            int segmentWidth = Math.max(1, (int) Math.round(area.width * segment));
            int travelWidth = Math.max(0, area.width - segmentWidth);
            int x = area.x + (int) Math.round(travelWidth * pingPong);
            if (configuration.inverted()) {
                x = area.x + travelWidth - (x - area.x);
            }
            setFillLayerBounds(x, area.y, segmentWidth, area.height);
        }
    }

    private void updateContentLayerBounds() {
        int width = Math.max(0, getWidth());
        int height = Math.max(0, getHeight());
        int offsetY = isHearthstoneStyle() ? contentAnimationOffsetY : 0;
        textLayer.setBounds(0, offsetY, width, height);
        updateFillBounds();
    }

    private void setFillLayerBounds(int x, int y, int width, int height) {
        int offsetY = isHearthstoneStyle() ? contentAnimationOffsetY : 0;
        fillLayer.setBounds(x, y + offsetY, width, height);
    }

    private Rectangle contentArea() {
        if (isHearthstoneStyle()) {
            return HearthstoneProgressEffect.contentBounds(getWidth(), getHeight());
        }
        int inset = configuration.trackPadding() + configuration.borderWidth();
        return new Rectangle(
                inset,
                inset,
                Math.max(0, getWidth() - inset * 2),
                Math.max(0, getHeight() - inset * 2)
        );
    }

    private double displayedPercent() {
        int span = getMaximum() - getMinimum();
        if (span <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (displayedValue - getMinimum()) / (double) span));
    }

    private void updateText() {
        textLayer.setVisible(configuration.stringPainted());
        if (configuration.stringPainted()) {
            textLayer.setText(getString());
        }
    }

    private String formattedString() {
        int percent = (int) Math.round(getPercentComplete() * 100.0);
        String customText = progressString == null ? "" : progressString.trim();
        String format = configuration.textFormat();

        if (!customText.isEmpty() && !format.contains("{text}")) {
            return configuration.showPercent()
                    ? customText + " " + percent + "%"
                    : customText;
        }

        if (!configuration.showPercent()) {
            format = format.replace("{percent}%", "").replace("{percent}", "");
        }

        String rendered = format
                .replace("{text}", customText)
                .replace("{value}", String.valueOf(getValue()))
                .replace("{min}", String.valueOf(getMinimum()))
                .replace("{max}", String.valueOf(getMaximum()))
                .replace("{percent}", String.valueOf(percent));
        return cleanFormattedText(rendered);
    }

    private String cleanFormattedText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replaceAll("\s+", " ").trim();
        while (!cleaned.isEmpty() && isTrailingSeparator(cleaned.charAt(cleaned.length() - 1))) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private boolean isTrailingSeparator(char character) {
        return character == '-'
                || character == '–'
                || character == '—'
                || character == '|'
                || character == '•'
                || character == '/'
                || character == ':';
    }

    private Configuration copyConfiguration(boolean stringPainted, int orientation, boolean inverted) {
        Configuration c = configuration;
        return new Configuration(
                c.trackColor(), c.fillColor(), c.borderColor(), c.textColor(), c.textShadowColor(),
                c.trackTexture(), c.fillTexture(), c.textureMode(), c.font(), c.borderWidth(),
                c.borderRadius(), c.fillBorderRadius(), c.trackPadding(), c.borderPainted(),
                stringPainted, c.showPercent(), c.textShadow(), c.textShadowOffsetX(), c.textShadowOffsetY(),
                c.textFormat(), c.textAlignment(), orientation, inverted, c.striped(), c.stripeColor(),
                c.stripeWidth(), c.stripeGap(), c.stripeSpeedMs(), c.animateValue(),
                c.animationDurationMs(), c.animationFrameDelayMs(), c.indeterminateCycleMs(),
                c.indeterminateSizePercent(), c.antialias()
        );
    }

    private abstract class Layer extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setComposite(AlphaComposite.SrcOver.derive(layerOpacity()));
                if (configuration.antialias()) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                if (contentLayer()) {
                    prepareHearthstoneContentGraphics(g2, this);
                }
                paintLayer(g2);
            } finally {
                g2.dispose();
            }
        }

        protected float layerOpacity() {
            return animationOpacity;
        }

        protected boolean contentLayer() {
            return false;
        }

        protected abstract void paintLayer(Graphics2D graphics);
    }

    private final class TrackLayer extends Layer {
        private final ScaledTextureCache textureCache = new ScaledTextureCache();
        @Override
        protected void paintLayer(Graphics2D graphics) {
            if (isHearthstoneStyle()) {
                HearthstoneProgressEffect.paintTrack(
                        graphics,
                        getWidth(),
                        getHeight(),
                        visualElapsedNanos()
                );
                return;
            }

            int radius = Math.min(configuration.borderRadius(), Math.min(getWidth(), getHeight()));
            Shape shape = roundedShape(getWidth(), getHeight(), radius);
            graphics.setColor(configuration.trackColor());
            graphics.fill(shape);
            drawTexture(graphics, configuration.trackTexture(), shape, configuration.textureMode(), getWidth(), getHeight(), textureCache);

            if (configuration.borderPainted() && configuration.borderWidth() > 0) {
                float strokeWidth = configuration.borderWidth();
                graphics.setColor(configuration.borderColor());
                graphics.setStroke(new java.awt.BasicStroke(strokeWidth));
                double inset = strokeWidth / 2.0;
                graphics.draw(new RoundRectangle2D.Double(
                        inset,
                        inset,
                        Math.max(0, getWidth() - strokeWidth),
                        Math.max(0, getHeight() - strokeWidth),
                        radius,
                        radius
                ));
            }
        }
    }

    private final class FillLayer extends Layer {
        private final ScaledTextureCache textureCache = new ScaledTextureCache();

        @Override
        protected float layerOpacity() {
            return isHearthstoneStyle() ? contentAnimationOpacity : animationOpacity;
        }

        @Override
        protected boolean contentLayer() {
            return isHearthstoneStyle();
        }

        @Override
        protected void paintLayer(Graphics2D graphics) {
            int radius = Math.min(configuration.fillBorderRadius(), Math.min(getWidth(), getHeight()));
            Shape shape = roundedShape(getWidth(), getHeight(), radius);
            if (isHearthstoneStyle()) {
                HearthstoneProgressEffect.paintFill(
                        graphics,
                        shape,
                        getWidth(),
                        getHeight(),
                        visualElapsedNanos(),
                        glowIntensity,
                        shinePosition
                );
                return;
            }

            graphics.setColor(configuration.fillColor());
            graphics.fill(shape);
            drawTexture(graphics, configuration.fillTexture(), shape, configuration.textureMode(), getWidth(), getHeight(), textureCache);
            if (configuration.striped()) {
                paintStripes(graphics, shape);
            }
            paintGlow(graphics, shape);
            paintShine(graphics, shape);
        }

        private void paintGlow(Graphics2D graphics, Shape shape) {
            if (glowIntensity <= 0.001f) {
                return;
            }
            int alpha = Math.max(0, Math.min(120, Math.round(120f * glowIntensity)));
            graphics.setComposite(AlphaComposite.SrcOver.derive(animationOpacity));
            graphics.setColor(new Color(255, 255, 255, alpha));
            graphics.fill(shape);
            graphics.setStroke(new java.awt.BasicStroke(Math.max(1f, 1f + glowIntensity * 2f)));
            graphics.setColor(new Color(255, 255, 255, Math.min(170, alpha + 30)));
            graphics.draw(shape);
        }

        private void paintShine(Graphics2D graphics, Shape shape) {
            if (shinePosition < -0.5f || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Shape oldClip = graphics.getClip();
            graphics.clip(shape);
            float center = shinePosition * getWidth();
            float spread = Math.max(12f, getWidth() * 0.16f);
            float start = center - spread;
            float end = center + spread;
            if (Math.abs(end - start) < 0.01f) {
                graphics.setClip(oldClip);
                return;
            }
            graphics.setPaint(new LinearGradientPaint(
                    start,
                    0f,
                    end,
                    0f,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{
                            new Color(255, 255, 255, 0),
                            new Color(255, 255, 255, 145),
                            new Color(255, 255, 255, 0)
                    }
            ));
            graphics.fillRect((int) Math.floor(start), 0,
                    Math.max(1, (int) Math.ceil(end - start)), getHeight());
            graphics.setClip(oldClip);
        }

        private void paintStripes(Graphics2D graphics, Shape clip) {
            Shape oldClip = graphics.getClip();
            graphics.clip(clip);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(configuration.stripeColor());

            int stripe = configuration.stripeWidth();
            int gap = configuration.stripeGap();
            int period = Math.max(1, stripe + gap);
            int offset = stripeOffset(period);
            int diagonal = getHeight();
            for (int x = -diagonal - period + offset; x < getWidth() + diagonal; x += period) {
                java.awt.Polygon polygon = new java.awt.Polygon();
                polygon.addPoint(x, getHeight());
                polygon.addPoint(x + stripe, getHeight());
                polygon.addPoint(x + stripe + diagonal, 0);
                polygon.addPoint(x + diagonal, 0);
                graphics.fillPolygon(polygon);
            }
            graphics.setClip(oldClip);
        }

        private int stripeOffset(int period) {
            if (configuration.stripeSpeedMs() <= 0) {
                return 0;
            }
            long elapsedMs = (System.nanoTime() - visualStartedAt) / 1_000_000L;
            return (int) ((elapsedMs * period / configuration.stripeSpeedMs()) % period);
        }
    }

    private final class HearthstoneEffectLayer extends Layer {
        @Override
        protected float layerOpacity() {
            return isHearthstoneStyle() ? contentAnimationOpacity : animationOpacity;
        }

        @Override
        protected boolean contentLayer() {
            return isHearthstoneStyle();
        }

        private HearthstoneEffectLayer() {
            setOpaque(false);
            setFocusable(false);
        }

        @Override
        protected void paintLayer(Graphics2D graphics) {
            if (!isHearthstoneStyle()) {
                return;
            }
            HearthstoneProgressEffect.paintLeadingEdge(
                    graphics,
                    fillLayer.getBounds(),
                    getWidth(),
                    getHeight(),
                    configuration.orientation(),
                    configuration.inverted(),
                    visualElapsedNanos(),
                    glowIntensity
            );
        }
    }

    private final class ProgressTextLabel extends JLabel {
        private ProgressTextLabel() {
            setOpaque(false);
            setVerticalAlignment(CENTER);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (!configuration.stringPainted() || getText() == null || getText().isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                float opacity = isHearthstoneStyle() ? contentAnimationOpacity : animationOpacity;
                g2.setComposite(AlphaComposite.SrcOver.derive(opacity));
                if (configuration.antialias()) {
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                }
                if (isHearthstoneStyle()) {
                    prepareHearthstoneContentGraphics(g2, this);
                }
                FontMetrics metrics = g2.getFontMetrics(getFont());
                int textWidth = metrics.stringWidth(getText());
                int x = switch (getHorizontalAlignment()) {
                    case LEFT -> 4;
                    case RIGHT -> Math.max(4, getWidth() - textWidth - 4);
                    default -> Math.max(0, (getWidth() - textWidth) / 2);
                } + textOffsetX;
                int y = Math.max(metrics.getAscent(),
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());

                if (isHearthstoneStyle()) {
                    g2.setColor(new Color(3, 11, 18, 235));
                    for (int offsetY = -2; offsetY <= 2; offsetY++) {
                        for (int offsetX = -2; offsetX <= 2; offsetX++) {
                            if (offsetX == 0 && offsetY == 0) {
                                continue;
                            }
                            if (Math.abs(offsetX) + Math.abs(offsetY) <= 3) {
                                g2.drawString(getText(), x + offsetX, y + offsetY);
                            }
                        }
                    }
                    g2.setColor(new Color(248, 255, 255));
                    g2.drawString(getText(), x, y);
                    g2.setColor(new Color(255, 255, 255, 120));
                    g2.drawString(getText(), x, y - 1);
                } else {
                    if (configuration.textShadow()) {
                        g2.setColor(configuration.textShadowColor());
                        g2.drawString(getText(),
                                x + configuration.textShadowOffsetX(),
                                y + configuration.textShadowOffsetY());
                    }
                    g2.setColor(configuration.textColor());
                    g2.drawString(getText(), x, y);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private void prepareHearthstoneContentGraphics(Graphics2D graphics, JComponent layer) {
        if (!isHearthstoneStyle()) {
            return;
        }
        Rectangle opening = contentArea();
        graphics.clip(new Rectangle(
                opening.x - layer.getX(),
                opening.y - layer.getY(),
                opening.width,
                opening.height
        ));

        double pivotX = opening.getCenterX() - layer.getX();
        double pivotY = opening.getCenterY() + contentAnimationOffsetY - layer.getY();
        graphics.translate(pivotX, pivotY);
        graphics.scale(contentAnimationScaleX, contentAnimationScaleY);
        graphics.translate(-pivotX, -pivotY);
    }

    private boolean isHearthstoneStyle() {
        return HearthstoneProgressEffect.supportsStyle(styleName);
    }

    private long visualElapsedNanos() {
        long startedAt = visualStartedAt;
        return startedAt <= 0L ? 0L : Math.max(0L, System.nanoTime() - startedAt);
    }

    private Shape roundedShape(int width, int height, int radius) {
        if (radius <= 0) {
            return new Rectangle(0, 0, width, height);
        }
        return new RoundRectangle2D.Double(0, 0, width, height, radius, radius);
    }

    private void drawTexture(Graphics2D graphics,
                             BufferedImage texture,
                             Shape clip,
                             TextureMode mode,
                             int targetWidth,
                             int targetHeight,
                             ScaledTextureCache cache) {
        if (texture == null || texture.getWidth() <= 0 || texture.getHeight() <= 0
                || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }
        Shape oldClip = graphics.getClip();
        graphics.clip(clip);
        if (mode == TextureMode.TILE) {
            graphics.setPaint(new TexturePaint(texture,
                    new Rectangle(0, 0, texture.getWidth(), texture.getHeight())));
            graphics.fillRect(0, 0, targetWidth, targetHeight);
        } else {
            ScaledTexture scaled = cache.resolve(texture, mode, targetWidth, targetHeight);
            graphics.drawImage(scaled.image(), scaled.x(), scaled.y(), null);
        }
        graphics.setClip(oldClip);
    }

    private static final class ScaledTextureCache {
        private BufferedImage source;
        private TextureMode mode;
        private int targetWidth = -1;
        private int targetHeight = -1;
        private ScaledTexture scaled;

        private ScaledTexture resolve(BufferedImage source,
                                      TextureMode mode,
                                      int targetWidth,
                                      int targetHeight) {
            if (scaled == null
                    || this.source != source
                    || this.mode != mode
                    || this.targetWidth != targetWidth
                    || this.targetHeight != targetHeight) {
                this.source = source;
                this.mode = mode;
                this.targetWidth = targetWidth;
                this.targetHeight = targetHeight;
                this.scaled = scale(source, mode, targetWidth, targetHeight);
            }
            return scaled;
        }

        private ScaledTexture scale(BufferedImage source,
                                    TextureMode mode,
                                    int targetWidth,
                                    int targetHeight) {
            int width = targetWidth;
            int height = targetHeight;
            int x = 0;
            int y = 0;

            if (mode == TextureMode.COVER || mode == TextureMode.CONTAIN) {
                double scaleX = targetWidth / (double) source.getWidth();
                double scaleY = targetHeight / (double) source.getHeight();
                double factor = mode == TextureMode.COVER
                        ? Math.max(scaleX, scaleY)
                        : Math.min(scaleX, scaleY);
                width = Math.max(1, (int) Math.round(source.getWidth() * factor));
                height = Math.max(1, (int) Math.round(source.getHeight() * factor));
                x = (targetWidth - width) / 2;
                y = (targetHeight - height) / 2;
            }

            BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rendered.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            return new ScaledTexture(rendered, x, y);
        }
    }

    private record ScaledTexture(BufferedImage image, int x, int y) {
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void stopAnimation(AnimationEngine.Handle animation) {
        if (animation != null) {
            animation.cancel();
        }
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to update ProgressBar on the EDT", error);
        }
    }
}
