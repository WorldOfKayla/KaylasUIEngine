package org.takesome.kaylasEngine.gui.animation;

import javax.swing.SwingUtilities;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable regression verification for the modular animation runtime. */
public final class AnimationRuntimeVerification {
    private AnimationRuntimeVerification() {
    }

    public static void main(String[] args) throws Exception {
        verifyCurves();
        verifyTimelineValues();
        verifyResourceOwnership();
        verifyOptions();
        verifyPackageArchitecture();
        System.out.println("KINETICA Animation Runtime 2.3 verification passed.");
    }

    private static void verifyCurves() {
        AnimationCurve linear = AnimationCurve.named("linear");
        require(close(linear.apply(0.0f), 0.0f), "linear curve start changed");
        require(close(linear.apply(0.5f), 0.5f), "linear curve midpoint changed");
        require(close(linear.apply(1.0f), 1.0f), "linear curve end changed");

        AnimationCurve easeOut = AnimationCurve.named("easeOutQuad");
        require(easeOut.apply(0.5f) > 0.5f, "ease-out curve no longer accelerates early");

        AnimationCurve bezier = AnimationCurve.cubicBezier(0.25, 0.1, 0.25, 1.0);
        require(close(bezier.apply(0.0f), 0.0f), "cubic-Bezier start changed");
        require(close(bezier.apply(1.0f), 1.0f), "cubic-Bezier end changed");
        float midpoint = bezier.apply(0.5f);
        require(midpoint > 0.0f && midpoint < 1.0f, "cubic-Bezier midpoint is invalid");
    }

    private static void verifyTimelineValues() {
        List<TimelineKeyFrame> frames = new ArrayList<>(List.of(
                frame(1.0, 1.0, 1.0, 0, 0, "easeOut"),
                frame(0.0, 0.0, 0.0, -20, 10, "linear"),
                frame(0.5, 0.75, 0.5, -5, 4, "easeInOut")
        ));
        TimelineKeyFrame.sort(frames);
        require(frames.get(0).time() == 0.0 && frames.get(2).time() == 1.0,
                "timeline key-frame ordering changed");

        TimelineFrameState state = new TimelineFrameState(0.5, 0.75, 0.5, -5, 4);
        require(state.progress() == 0.5 && state.offsetX() == -5,
                "timeline frame-state contract changed");
    }

    private static TimelineKeyFrame frame(double time,
                                          double scaleX,
                                          double scaleY,
                                          int offsetX,
                                          int offsetY,
                                          String interpolation) {
        TimelineKeyFrame frame = new TimelineKeyFrame();
        setField(frame, "time", time);
        setField(frame, "scaleX", scaleX);
        setField(frame, "scaleY", scaleY);
        setField(frame, "offsetX", offsetX);
        setField(frame, "offsetY", offsetY);
        setField(frame, "interpolation", interpolation);
        return frame;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to prepare timeline verification", error);
        }
    }

    private static void verifyResourceOwnership() throws Exception {
        SwingTimerGroup group = new SwingTimerGroup();
        AtomicInteger closed = new AtomicInteger();
        AutoCloseable resource = closed::incrementAndGet;
        group.track(resource);
        SwingUtilities.invokeAndWait(() -> { });
        require(group.size() == 1, "tracked animation resource was not retained");
        group.stopAll();
        SwingUtilities.invokeAndWait(() -> { });
        require(closed.get() == 1 && group.size() == 0,
                "animation resource group did not close and release its resource");

        require(AnimationPulse.shared() == AnimationPulse.shared(),
                "shared animation pulse is not a singleton facade");
        require(AnimationPulse.shared().activeAnimationCount() >= 0,
                "animation pulse metrics are invalid");
    }

    private static void verifyOptions() {
        ProgressBarAnimator.Options source = new ProgressBarAnimator.Options()
                .setProgressUpdateMs(0)
                .setProgressStep(0)
                .setTimelineDurationMs(0)
                .setTimelineFrameDelayMs(0)
                .setLoop(false);
        ProgressBarAnimator.Options copy = new ProgressBarAnimator.Options(source);
        require(copy.progressUpdateMs() == 1 && copy.progressStep() == 1,
                "progress option lower bounds changed");
        require(copy.timelineDurationMs() == 1 && copy.timelineFrameDelayMs() == 1,
                "timeline option lower bounds changed");
        require(!copy.loop(), "progress option copy discarded loop policy");
    }

    private static void verifyPackageArchitecture() {
        for (String className : List.of(
                "org.takesome.kaylasEngine.gui.animation.internal.easing.DefaultAnimationCurveEvaluation",
                "org.takesome.kaylasEngine.gui.animation.internal.pulse.SwingAnimationPulseRuntime",
                "org.takesome.kaylasEngine.gui.animation.internal.scheduling.DefaultAnimationResourceGroup",
                "org.takesome.kaylasEngine.gui.animation.internal.timeline.DefaultTimelineExecution",
                "org.takesome.kaylasEngine.gui.animation.internal.overlay.DefaultLayeredOverlayController",
                "org.takesome.kaylasEngine.gui.animation.internal.window.DefaultScriptedWindowAnimationController",
                "org.takesome.kaylasEngine.gui.animation.internal.progress.DefaultProgressAnimationController",
                "org.takesome.kaylasEngine.gui.animation.internal.drawer.DefaultDrawerAnimationController"
        )) {
            requireHiddenFinal(className);
        }
        require(Modifier.isPublic(AnimationCurve.class.getModifiers()),
                "AnimationCurve must remain public");
        require(Modifier.isPublic(AnimationPulse.class.getModifiers()),
                "AnimationPulse must remain public");
        require(Modifier.isPublic(SnapshotDrawerAnimator.class.getModifiers()),
                "SnapshotDrawerAnimator must remain public");
    }

    private static void requireHiddenFinal(String className) {
        try {
            Class<?> type = Class.forName(className);
            require(!Modifier.isPublic(type.getModifiers()),
                    "Internal animation implementation is public: " + className);
            require(Modifier.isFinal(type.getModifiers()),
                    "Internal animation implementation is not final: " + className);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Internal animation implementation not found: " + className, error);
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
