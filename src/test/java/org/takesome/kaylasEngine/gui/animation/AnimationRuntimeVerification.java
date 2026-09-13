package org.takesome.kaylasEngine.gui.animation;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable regression verification for the modular animation runtime. */
public final class AnimationRuntimeVerification {
    private AnimationRuntimeVerification() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("log.dir", Path.of("build", "test-logs").toAbsolutePath().toString());
        System.setProperty("log.level", "OFF");
        Engine.LOGGER = LogManager.getLogger(AnimationRuntimeVerification.class);
        verifyCurves();
        verifyUnifiedEngine();
        verifyAnimationTelemetry();
        verifyTimelineValues();
        verifyTimelinePublishesInitialFrameSynchronously();
        verifyProgressEntranceIsNeverEmpty();
        verifyProgressLoopReentersVisibly();
        verifyResourceOwnership();
        verifyOptions();
        verifyFrozenOverlay();
        verifyPackageArchitecture();
        System.out.println("KINETICA Animation Runtime 2.4 verification passed.");
    }

    private static void verifyCurves() {
        AnimationCurve linear = AnimationCurve.named("linear");
        require(close(linear.apply(0.0f), 0.0f), "linear curve start changed");
        require(close(linear.apply(0.5f), 0.5f), "linear curve midpoint changed");
        require(close(linear.apply(1.0f), 1.0f), "linear curve end changed");

        AnimationCurve easeOut = AnimationCurve.named("easeOutQuad");
        require(easeOut.apply(0.5f) > 0.5f, "ease-out curve no longer accelerates early");

        AnimationCurve arrival = AnimationCurve.named("easeOutBack");
        require(close(arrival.apply(0.0f), 0.0f), "back easing start changed");
        require(arrival.apply(0.65f) > 1.0f, "back easing lost its arrival overshoot");
        require(close(arrival.apply(1.0f), 1.0f), "back easing end changed");

        AnimationCurve departure = AnimationCurve.named("easeInBack");
        require(departure.apply(0.35f) < 0.0f, "back easing lost its departure anticipation");

        AnimationCurve bezier = AnimationCurve.cubicBezier(0.25, 0.1, 0.25, 1.0);
        require(close(bezier.apply(0.0f), 0.0f), "cubic-Bezier start changed");
        require(close(bezier.apply(1.0f), 1.0f), "cubic-Bezier end changed");
        float midpoint = bezier.apply(0.5f);
        require(midpoint > 0.0f && midpoint < 1.0f, "cubic-Bezier midpoint is invalid");

        AnimationCurve legacyEaseOut = AnimationCurve.named("easeOut");
        require(legacyEaseOut.apply(0.5f) > 0.5f,
                "legacy timeline easeOut alias lost its curved response");

        AnimationCurve expo = AnimationCurve.named("easeOutExpo");
        require(expo.apply(0.5f) > 0.9f,
                "exponential easing lost its detailed fast-arrival response");

        AnimationCurve circ = AnimationCurve.named("easeInOutCirc");
        require(circ.apply(0.25f) < 0.25f && circ.apply(0.75f) > 0.75f,
                "circular easing shape changed");
    }

    private static void verifyUnifiedEngine() {
        AnimationEngine engine = AnimationEngine.shared();
        require(engine == AnimationEngine.shared(), "AnimationEngine is not a shared singleton");

        float[] value = {-1.0f};
        AnimationEngine.Handle completed = engine.tween(
                0,
                16,
                engine.curve("easeOutCubic"),
                current -> value[0] = current,
                null
        );
        require(close(value[0], 1.0f) && !completed.isActive(),
                "zero-duration AnimationEngine tween did not complete deterministically");

        AnimationEngine.Metrics metrics = engine.metrics();
        require(metrics.activeAnimations() >= 0
                        && metrics.adaptiveFrameDelayMs() >= 0
                        && metrics.tickCount() >= 0,
                "AnimationEngine metrics are invalid");
    }

    private static void verifyAnimationTelemetry() throws Exception {
        AnimationEngine engine = AnimationEngine.shared();
        List<AnimationEvent> events = new ArrayList<>();
        float[] value = {-1.0f};

        try (AutoCloseable registration = engine.listen(events::add)) {
            require(engine.listenerCount() == 1,
                    "animation listener registration was not retained");
            AnimationEngine.Handle handle = engine.tween(
                    "verification-tween",
                    0,
                    16,
                    AnimationCurve.named("easeOutExpo"),
                    current -> value[0] = current,
                    null
            );
            require(!handle.isActive(),
                    "zero-duration telemetry tween remained active");
        }

        require(engine.listenerCount() == 0,
                "animation listener registration leaked after close");
        require(close(value[0], 1.0f),
                "telemetry tween did not publish its final value");
        require(events.size() == 4,
                "immediate tween lifecycle event count changed: " + events.size());
        require(events.get(0).phase() == AnimationEvent.Phase.SCHEDULED
                        && events.get(1).phase() == AnimationEvent.Phase.STARTED
                        && events.get(2).phase() == AnimationEvent.Phase.FRAME
                        && events.get(3).phase() == AnimationEvent.Phase.COMPLETED,
                "immediate tween lifecycle ordering changed");
        require("verification-tween".equals(events.get(2).name())
                        && "easeOutExpo".equals(events.get(2).curve())
                        && events.get(2).hasProgress()
                        && Math.abs(events.get(2).progress() - 1.0) < 0.0001,
                "animation telemetry lost tween identity, curve or progress");

        AnimationPulse.Diagnostics diagnostics = engine.diagnostics();
        require(diagnostics.activeAnimations() >= 0
                        && diagnostics.smoothedFrameWorkNanos() >= 0
                        && diagnostics.lateFrameCount() >= 0
                        && diagnostics.maxFrameLatenessNanos() >= 0,
                "animation timing diagnostics are invalid");
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

        TimelineFrameState state = new TimelineFrameState(
                0.5, 0.75, 0.5, -5, 4, 0.8, 0.6, 0.25
        );
        require(state.progress() == 0.5 && state.offsetX() == -5,
                "timeline frame-state contract changed");
        require(state.opacity() == 0.8 && state.glow() == 0.6 && state.shine() == 0.25,
                "timeline visual-effect channels changed");
    }

    private static void verifyTimelinePublishesInitialFrameSynchronously() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingTimerGroup timers = new SwingTimerGroup();
            TimelineAnimator animator = new TimelineAnimator(timers, 16);
            List<TimelineKeyFrame> frames = new ArrayList<>(List.of(
                    frame(0.0, 1.05, 1.05, 0, -10, "linear"),
                    frame(1.0, 1.0, 1.0, 0, 0, "easeOutCubic")
            ));
            TimelineFrameState[] firstState = {null};
            AnimationEngine.Handle handle = animator.animate(
                    500,
                    frames,
                    state -> {
                        if (firstState[0] == null) {
                            firstState[0] = state;
                        }
                    },
                    null
            );
            require(firstState[0] != null,
                    "timeline did not publish an initial frame before animate() returned");
            require(firstState[0].offsetY() == -10
                            && close((float) firstState[0].scaleX(), 1.05f),
                    "timeline initial frame did not match keyframe zero");
            handle.cancel();
            timers.stopAll();
        });
    }

    private static void verifyProgressEntranceIsNeverEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ProgressBar progressBar = new ProgressBar();
            progressBar.setStyleName("hearthstone");
            progressBar.setSize(430, 40);
            progressBar.setRange(0, 100, 0);
            progressBar.setStringPainted(true);
            progressBar.setShowPercent(false);
            progressBar.doLayout();

            ProgressBarAnimator.Options options = new ProgressBarAnimator.Options()
                    .setProgressStep(4)
                    .setProgressUpdateMs(1_000)
                    .setTimelineDurationMs(500)
                    .setTimelineFrameDelayMs(16)
                    .setLoop(false)
                    .setRandomMessages(false)
                    .setShowText(true)
                    .setShowPercent(false)
                    .setAnimateActive(false)
                    .setAnimateComplete(false)
                    .setAnimateExit(false)
                    .setResetOnStop(true)
                    .setHideOnStop(false);

            ProgressBarAnimator animator = new ProgressBarAnimator(
                    progressBar,
                    progressBar.getTextLabel(),
                    "verification/progress-messages.json",
                    "verification/progress-animation.json",
                    "[ENTRANCE-VERIFY]",
                    options
            );
            animator.startProgressTest();

            require(progressBar.isVisible(),
                    "progress bar was not visible at entrance start");
            require(progressBar.getValue() == 4,
                    "progress entrance did not seed a visible initial value");
            require("Preparing first visible frame".equals(progressBar.getTextLabel().getText()),
                    "progress entrance did not seed text before animation");
            require(progressBar.getContentAnimationOffsetY() == -10,
                    "progress entrance did not synchronously apply its first offset");
            require(close(progressBar.getContentAnimationScaleX(), 1.05f),
                    "progress entrance did not synchronously apply its first scale");

            animator.stop();
            require(progressBar.getValue() == 0,
                    "progress stop did not reset the seeded initial value");

            // Repeated immediate restarts must supersede every stopped generation and publish a
            // complete first entrance frame, rather than being cleared by stale teardown work.
            for (int restart = 0; restart < 32; restart++) {
                animator.startProgressTest();
                require(progressBar.isVisible(),
                        "progress bar stayed hidden after immediate restart " + restart);
                require(progressBar.getValue() == 4,
                        "progress restart did not reseed a visible initial value: " + restart);
                require("Preparing first visible frame".equals(progressBar.getTextLabel().getText()),
                        "progress restart did not restore entrance text: " + restart);
                require(progressBar.getContentAnimationOffsetY() == -10,
                        "progress restart did not apply the entrance start position: " + restart);
                require(close(progressBar.getContentAnimationScaleX(), 1.05f),
                        "progress restart did not apply the entrance start scale: " + restart);
                animator.stop();
                require(progressBar.getValue() == 0,
                        "progress restart teardown did not reset value: " + restart);
            }
        });
    }

    private static void verifyProgressLoopReentersVisibly() throws Exception {
        CountDownLatch snapshotsReady = new CountDownLatch(3);
        List<LoopSnapshot> snapshots = Collections.synchronizedList(new ArrayList<>());
        ProgressBarAnimator[] animatorRef = {null};
        AtomicInteger completedCycles = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            ProgressBar progressBar = new ProgressBar();
            progressBar.setStyleName("hearthstone");
            progressBar.setSize(430, 40);
            progressBar.setRange(0, 100, 0);
            progressBar.setStringPainted(true);
            progressBar.setShowPercent(false);
            progressBar.doLayout();

            ProgressBarAnimator.Options options = new ProgressBarAnimator.Options()
                    .setProgressStep(20)
                    .setProgressUpdateMs(20)
                    .setInitialDelayMs(0)
                    .setCycleDelayMs(20)
                    .setTimelineDurationMs(80)
                    .setTimelineFrameDelayMs(8)
                    .setMaxValue(100)
                    .setLoop(true)
                    .setRandomMessages(false)
                    .setShowText(true)
                    .setShowPercent(false)
                    .setAnimateActive(false)
                    .setAnimateComplete(false)
                    .setAnimateExit(true)
                    .setResetOnStop(true)
                    .setHideOnStop(false);

            ProgressBarAnimator animator = new ProgressBarAnimator(
                    progressBar,
                    progressBar.getTextLabel(),
                    "verification/progress-messages.json",
                    "verification/progress-loop-animation.json",
                    "[LOOP-VERIFY]",
                    options
            );
            animatorRef[0] = animator;
            animator.setProgressListener(new ProgressBarAnimator.ProgressListener() {
                @Override
                public void onStart() {
                    // No-op.
                }

                @Override
                public void onProgress(int value) {
                    // No-op.
                }

                @Override
                public void onComplete() {
                    int cycle = completedCycles.incrementAndGet();
                    if (cycle > 3) {
                        return;
                    }
                    Timer snapshotTimer = new Timer(150, event -> {
                        ((Timer) event.getSource()).stop();
                        snapshots.add(new LoopSnapshot(
                                cycle,
                                progressBar.isVisible(),
                                progressBar.getValue(),
                                progressBar.getFillLayer().getWidth(),
                                progressBar.getContentAnimationOffsetY(),
                                progressBar.getContentAnimationScaleX(),
                                progressBar.getTextLabel().getText()
                        ));
                        snapshotsReady.countDown();
                    });
                    snapshotTimer.setRepeats(false);
                    snapshotTimer.start();
                }
            });
            animator.startProgressTest();
        });

        require(snapshotsReady.await(5, TimeUnit.SECONDS),
                "progress loop did not produce three visible re-entry snapshots");
        SwingUtilities.invokeAndWait(() -> animatorRef[0].stop());

        require(snapshots.size() == 3,
                "progress loop snapshot count changed: " + snapshots.size());
        for (LoopSnapshot snapshot : snapshots) {
            require(snapshot.visible(),
                    "progress became hidden after loop cycle " + snapshot.cycle());
            require(snapshot.value() > 0,
                    "progress value remained empty after loop cycle " + snapshot.cycle());
            require(snapshot.fillWidth() > 0,
                    "progress fill remained empty after loop cycle " + snapshot.cycle());
            require(snapshot.offsetY() == 0,
                    "progress stayed outside the opening after loop cycle " + snapshot.cycle()
                            + ": offsetY=" + snapshot.offsetY());
            require(Math.abs(snapshot.scaleX() - 1.0f) < 0.002f,
                    "progress did not settle after loop cycle " + snapshot.cycle()
                            + ": scale=" + snapshot.scaleX());
            require(snapshot.text() != null && !snapshot.text().isBlank(),
                    "progress text remained empty after loop cycle " + snapshot.cycle());
        }
    }

    private record LoopSnapshot(
            int cycle,
            boolean visible,
            int value,
            int fillWidth,
            int offsetY,
            float scaleX,
            String text
    ) { }

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

    private static void verifyFrozenOverlay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JLayeredPane pane = new JLayeredPane();
            pane.setSize(200, 100);

            JPanel titleBar = new JPanel();
            titleBar.setName("titleBar");
            titleBar.setOpaque(true);
            titleBar.setBackground(Color.RED);
            titleBar.setBounds(0, 0, 200, 30);

            JPanel content = new JPanel();
            content.setName("content");
            content.setOpaque(true);
            content.setBackground(Color.BLUE);
            content.setBounds(0, 30, 200, 70);

            pane.add(content, JLayeredPane.DEFAULT_LAYER);
            pane.add(titleBar, JLayeredPane.DEFAULT_LAYER);

            LayeredPaneOverlay overlay = new LayeredPaneOverlay(
                    pane,
                    () -> new Rectangle(0, 30, 200, 70),
                    Color.BLACK,
                    "loadingOverlay",
                    16,
                    LogManager.getLogger(AnimationRuntimeVerification.class),
                    "[OVERLAY-VERIFY]"
            );
            overlay.fadeIn(128, 0, null);

            Component titleTarget = SwingUtilities.getDeepestComponentAt(pane, 10, 10);
            Component contentTarget = SwingUtilities.getDeepestComponentAt(pane, 10, 40);
            require(titleTarget == titleBar,
                    "frozen overlay covered the title bar");
            require(contentTarget != null && "loadingOverlay".equals(contentTarget.getName()),
                    "frozen overlay did not intercept the content area");

            content.setBackground(Color.GREEN);
            BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                pane.printAll(graphics);
            } finally {
                graphics.dispose();
            }

            Color titlePixel = new Color(image.getRGB(10, 10), true);
            Color contentPixel = new Color(image.getRGB(10, 40), true);
            require(titlePixel.getRed() > titlePixel.getGreen(),
                    "title bar was visually replaced by the overlay");
            require(contentPixel.getBlue() > contentPixel.getGreen(),
                    "overlay no longer paints the frozen pre-change snapshot");

            overlay.dispose();
        });
    }

    private static void verifyPackageArchitecture() {
        for (String className : List.of(
                "org.takesome.kaylasEngine.gui.animation.internal.easing.DefaultAnimationCurveEvaluation",
                "org.takesome.kaylasEngine.gui.animation.internal.pulse.SwingAnimationPulseRuntime",
                "org.takesome.kaylasEngine.gui.animation.internal.pulse.PulseTimingDiagnostics",
                "org.takesome.kaylasEngine.gui.animation.internal.scheduling.DefaultAnimationResourceGroup",
                "org.takesome.kaylasEngine.gui.animation.internal.timeline.DefaultTimelineExecution",
                "org.takesome.kaylasEngine.gui.animation.internal.overlay.DefaultLayeredOverlayController",
                "org.takesome.kaylasEngine.gui.animation.internal.window.DefaultScriptedWindowAnimationController",
                "org.takesome.kaylasEngine.gui.animation.internal.progress.DefaultProgressAnimationController",
                "org.takesome.kaylasEngine.gui.animation.internal.drawer.DefaultDrawerAnimationController"
        )) {
            requireHiddenFinal(className);
        }
        require(Modifier.isPublic(AnimationEngine.class.getModifiers()),
                "AnimationEngine must remain public");
        require(Modifier.isPublic(AnimationCurve.class.getModifiers()),
                "AnimationCurve must remain public");
        require(Modifier.isPublic(AnimationPulse.class.getModifiers()),
                "AnimationPulse must remain public");
        require(Modifier.isPublic(AnimationEvent.class.getModifiers()),
                "AnimationEvent must remain public");
        require(Modifier.isPublic(AnimationListener.class.getModifiers()),
                "AnimationListener must remain public");
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
