package org.takesome.kaylasEngine.gui.animation.internal.overlay;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.gui.animation.AnimationCurve;

import javax.swing.JLayeredPane;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.function.Supplier;

/** Internal controller boundary for a fading layered-pane overlay. */
public interface LayeredOverlayController {
    static LayeredOverlayController create(JLayeredPane layeredPane,
                                           Supplier<Rectangle> boundsSupplier,
                                           Color color,
                                           String name,
                                           int frameDelayMs,
                                           Logger logger,
                                           String logPrefix) {
        return new DefaultLayeredOverlayController(
                layeredPane,
                boundsSupplier,
                color,
                name,
                frameDelayMs,
                logger,
                logPrefix
        );
    }

    void fadeIn(int targetAlpha, int durationMs, Runnable onComplete);
    void fadeIn(int targetAlpha, int durationMs, int frameDelayMs, AnimationCurve curve, Runnable onComplete);

    void fadeOut(int durationMs, Runnable onComplete);
    void fadeOut(int durationMs, int frameDelayMs, AnimationCurve curve, Runnable onComplete);

    void refreshBounds();
    void dispose();
    boolean isVisible();
}
