package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.FloatingWindow;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.Objects;

/** Resolves declarative loading-window positions against frame, screen or current coordinates. */
final class LoadingUiPositionResolver {
    private LoadingUiPositionResolver() {
    }

    static Point resolve(ScriptedLoadingUi.Position position,
                         FloatingWindow window,
                         Point currentPosition) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(window, "window");

        Rectangle referenceBounds;
        switch (position.reference) {
            case "current" -> {
                Point current = currentPosition == null ? window.getLocation() : currentPosition;
                return new Point(
                        current.x + position.offsetX,
                        current.y + position.offsetY
                );
            }
            case "absolute" -> {
                return new Point(position.offsetX, position.offsetY);
            }
            case "screen" -> referenceBounds = screenBounds(window);
            default -> referenceBounds = window.getEngine().getFrame().getBounds();
        }

        double referencePointX = referenceBounds.x
                + referenceBounds.width * position.referenceX;
        double referencePointY = referenceBounds.y
                + referenceBounds.height * position.referenceY;
        int x = (int) Math.round(
                referencePointX - window.getWidth() * position.windowX + position.offsetX
        );
        int y = (int) Math.round(
                referencePointY - window.getHeight() * position.windowY + position.offsetY
        );
        return new Point(x, y);
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
