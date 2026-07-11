package org.takesome.kaylasEngine.gui.animation.internal.window;

import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.loadingManager.ScriptedLoadingUi;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

/** Internal execution boundary for scripted floating-window transitions. */
public interface ScriptedWindowAnimationController {
    static ScriptedWindowAnimationController create(FloatingWindow window,
                                                     ScriptedLoadingUi.Transition transition) {
        return new DefaultScriptedWindowAnimationController(window, transition);
    }

    void setAnimationStats(AnimationStats animationStats);
    void toggleVisibility();
    void animate(boolean entry);
    void cancel();
}
