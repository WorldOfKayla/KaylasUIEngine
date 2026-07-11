package org.takesome.kaylasEngine.gui.animation;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.gui.animation.internal.overlay.LayeredOverlayController;

import javax.swing.JLayeredPane;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.function.Supplier;

/** Public facade for a fading overlay hosted by a Swing layered pane. */
public final class LayeredPaneOverlay {
    private final LayeredOverlayController controller;

    public LayeredPaneOverlay(JLayeredPane layeredPane,
                              Supplier<Rectangle> boundsSupplier,
                              Color color,
                              String name,
                              int frameDelayMs,
                              Logger logger,
                              String logPrefix) {
        controller = LayeredOverlayController.create(
                layeredPane,
                boundsSupplier,
                color,
                name,
                frameDelayMs,
                logger,
                logPrefix
        );
    }

    public void fadeIn(int targetAlpha, int durationMs, Runnable onComplete) {
        controller.fadeIn(targetAlpha, durationMs, onComplete);
    }

    public void fadeIn(int targetAlpha,
                       int durationMs,
                       int frameDelayMs,
                       AnimationCurve curve,
                       Runnable onComplete) {
        controller.fadeIn(targetAlpha, durationMs, frameDelayMs, curve, onComplete);
    }

    public void fadeOut(int durationMs, Runnable onComplete) {
        controller.fadeOut(durationMs, onComplete);
    }

    public void fadeOut(int durationMs,
                        int frameDelayMs,
                        AnimationCurve curve,
                        Runnable onComplete) {
        controller.fadeOut(durationMs, frameDelayMs, curve, onComplete);
    }

    public void refreshBounds() { controller.refreshBounds(); }
    public void dispose() { controller.dispose(); }
    public boolean isVisible() { return controller.isVisible(); }
}
