package org.takesome.kaylasEngine.gui.animation;

/**
 * Listener for opt-in animation lifecycle and frame telemetry.
 *
 * <p>Listeners run on the animation thread, which is normally the Swing EDT. Implementations
 * should remain lightweight and must not block. A failing listener is isolated from the animation
 * being observed.</p>
 */
@FunctionalInterface
public interface AnimationListener {
    /** Receives one animation lifecycle or frame event. */
    void onAnimationEvent(AnimationEvent event);
}
