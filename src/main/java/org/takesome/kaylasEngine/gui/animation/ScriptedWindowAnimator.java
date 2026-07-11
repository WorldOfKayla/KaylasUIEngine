package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.gui.animation.internal.window.ScriptedWindowAnimationController;
import org.takesome.kaylasEngine.gui.loadingManager.ScriptedLoadingUi;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

/** Public facade for a loading-script-driven floating-window transition. */
public final class ScriptedWindowAnimator {
    private final ScriptedWindowAnimationController controller;

    public ScriptedWindowAnimator(FloatingWindow window, ScriptedLoadingUi.Transition transition) {
        controller = ScriptedWindowAnimationController.create(window, transition);
    }

    public void setAnimationStats(AnimationStats animationStats) {
        controller.setAnimationStats(animationStats);
    }

    public void toggleVisibility() { controller.toggleVisibility(); }
    public void animate(boolean entry) { controller.animate(entry); }
    public void cancel() { controller.cancel(); }
}
