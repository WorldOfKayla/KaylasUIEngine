package org.takesome.kaylasEngine.gui.components.button;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.Align;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.SwingConstants;
import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/** Applies a resolved immutable style to a {@link Button}. */
public final class ButtonStyle {
    private static final int TEXTURE_STATE_COUNT = 4;

    private final ComponentFactory componentFactory;
    private final ImageUtils imageUtils;
    private final StyleAttributes style;
    private final Align align;
    private final BufferedImage texture;

    public ButtonStyle(ComponentFactory componentFactory) {
        this.componentFactory = Objects.requireNonNull(componentFactory, "componentFactory");
        this.imageUtils = componentFactory.getEngine().getImageUtils();
        this.style = componentFactory.getStyle();
        this.align = Align.from(style.getAlign());
        this.texture = loadTexture(style.getTexture());
    }

    public void apply(Button button) {
        Objects.requireNonNull(button, "button");
        button.setHorizontalAlignment(swingAlignment(align));
        button.setFont(componentFactory.getEngine().getFONTUTILS().getFont(
                style.getFont(),
                style.getFontSize(),
                style.getFontStyle()
        ));
        button.setHoverColor(hexToColor(style.getHoverColor()));
        button.setForeground(hexToColor(style.getColor()));
        applyTextureStates(button);
    }

    private void applyTextureStates(Button button) {
        if (texture == null || texture.getHeight() < TEXTURE_STATE_COUNT) {
            return;
        }
        int stateHeight = texture.getHeight() / TEXTURE_STATE_COUNT;
        if (stateHeight <= 0) {
            return;
        }
        button.defaultTX = slice(0, stateHeight);
        button.rolloverTX = slice(stateHeight, stateHeight);
        button.pressedTX = slice(stateHeight * 2, stateHeight);
        button.lockedTX = slice(stateHeight * 3, stateHeight);
    }

    private BufferedImage slice(int startY, int height) {
        int safeHeight = Math.min(height, texture.getHeight() - startY);
        if (safeHeight <= 0) {
            return null;
        }
        return imageUtils.getTexture(
                texture,
                style.getBorderRadius(),
                0,
                startY,
                texture.getWidth(),
                safeHeight
        );
    }

    private BufferedImage loadTexture(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        try {
            return imageUtils.getLocalImage(texturePath);
        } catch (RuntimeException error) {
            Engine.getLOGGER().warn("Unable to load button texture '{}'.", texturePath, error);
            return null;
        }
    }

    private static int swingAlignment(Align align) {
        return switch (align) {
            case CENTER -> SwingConstants.CENTER;
            case RIGHT -> SwingConstants.RIGHT;
            case LEFT -> SwingConstants.LEFT;
        };
    }
}
