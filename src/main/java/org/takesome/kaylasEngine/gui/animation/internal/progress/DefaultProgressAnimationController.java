package org.takesome.kaylasEngine.gui.animation.internal.progress;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.animation.AnimationEngine;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.Options;
import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator.ProgressListener;
import org.takesome.kaylasEngine.gui.animation.SwingTimerGroup;
import org.takesome.kaylasEngine.gui.animation.TimelineAnimator;
import org.takesome.kaylasEngine.gui.animation.TimelineFrameState;
import org.takesome.kaylasEngine.gui.animation.TimelineKeyFrame;
import org.takesome.kaylasEngine.gui.components.progressBar.ProgressBar;
import org.takesome.kaylasEngine.resources.ResourceLoader;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Engine-owned Swing progress animation controller.
 *
 * <p>Applications provide resource paths, localized messages and runtime options. The engine owns timer
 * lifecycle, interpolation, progress loop semantics and EDT safety.</p>
 */
final class DefaultProgressAnimationController implements ProgressAnimationController {
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_ANIMATION_FRAME_MS = 16;
    private static final int DEFAULT_PROGRESS_UPDATE_MS = 100;
    private static final int DEFAULT_TIMELINE_DURATION_MS = 500;

    /** Runtime policy for progress animation. Applications may build this from JSON, Lua or code. */
    private final JComponent progressBar;
    private final IntConsumer progressValueSetter;
    private final IntSupplier progressMaximumSupplier;
    private final Consumer<String> progressTextSetter;
    private final Consumer<Boolean> progressTextVisibilitySetter;
    private final Consumer<Boolean> progressPercentVisibilitySetter;
    private final DoubleConsumer progressOpacitySetter;
    private final DoubleConsumer progressGlowSetter;
    private final DoubleConsumer progressShineSetter;
    private final JLabel progressText;
    private final Rectangle originalBounds;
    private final String logPrefix;
    private final SwingTimerGroup timers = new SwingTimerGroup();
    private final TimelineAnimator timelineAnimator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private final Options options;
    private final java.util.function.Supplier<List<String>> messageResolver;

    private List<String> messages = List.of();
    private ProgressListener progressListener;
    private ProgressAnimationConfig animationConfig = new ProgressAnimationConfig();
    private AnimationEngine.Handle progressTimer;
    private AnimationEngine.Handle activeEffect;
    private AnimationEngine.Handle transitionEffect;
    private long activeEffectGeneration;
    private int progressValue;
    private int messageIndex;

    DefaultProgressAnimationController(ProgressAnimationController.Config config) {
        Objects.requireNonNull(config, "config");
        this.progressBar = Objects.requireNonNull(config.progressBar(), "progressBar");
        this.progressText = Objects.requireNonNull(config.progressText(), "progressText");
        this.progressValueSetter = Objects.requireNonNull(config.progressValueSetter(), "progressValueSetter");
        this.progressMaximumSupplier = Objects.requireNonNull(config.progressMaximumSupplier(), "progressMaximumSupplier");
        this.progressTextSetter = Objects.requireNonNull(config.progressTextSetter(), "progressTextSetter");
        this.progressTextVisibilitySetter = Objects.requireNonNull(
                config.progressTextVisibilitySetter(),
                "progressTextVisibilitySetter"
        );
        this.progressPercentVisibilitySetter = Objects.requireNonNull(
                config.progressPercentVisibilitySetter(),
                "progressPercentVisibilitySetter"
        );
        this.progressOpacitySetter = Objects.requireNonNull(config.progressOpacitySetter(), "progressOpacitySetter");
        this.progressGlowSetter = Objects.requireNonNull(config.progressGlowSetter(), "progressGlowSetter");
        this.progressShineSetter = Objects.requireNonNull(config.progressShineSetter(), "progressShineSetter");
        this.messageResolver = Objects.requireNonNull(config.messageResolver(), "messageResolver");
        this.originalBounds = progressBar.getBounds();
        this.logPrefix = config.logPrefix() == null || config.logPrefix().isBlank()
                ? "[UI-PROGRESS]"
                : config.logPrefix();
        this.options = new Options(config.options());
        this.timelineAnimator = new TimelineAnimator(timers, this.options.timelineFrameDelayMs());
        loadMessagesFromJson(config.messagesResource());
        loadAnimationConfig(config.animationConfigResource());
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public void startProgressTest() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long generation = lifecycleGeneration.incrementAndGet();

        runOnEdt(() -> {
            if (!isCurrentGeneration(generation)) {
                return;
            }

            // A new session owns the resource group. This also clears any handles left by a
            // previously cancelled session whose teardown was superseded by this start.
            timers.stopAll();
            Engine.getLOGGER().debug(
                    "{} progress animator start: generation={} source=AnimationEngine updateMs={} step={} loop={} entranceMs={} activeMs={} completeMs={} frameDelayMs={} randomMessages={} showText={} showPercent={}",
                    logPrefix,
                    generation,
                    options.progressUpdateMs(),
                    options.progressStep(),
                    options.loop(),
                    options.timelineDurationMs(),
                    options.activeTimelineDurationMs(),
                    options.completeTimelineDurationMs(),
                    options.timelineFrameDelayMs(),
                    options.randomMessages(),
                    options.showText(),
                    options.showPercent());
            progressValue = 0;
            messageIndex = 0;
            progressTextVisibilitySetter.accept(options.showText());
            progressPercentVisibilitySetter.accept(options.showPercent());
            if (!options.showText()) {
                progressTextSetter.accept("");
            }
            if (progressListener != null) {
                progressListener.onStart();
            }
            beginEntranceCycle(generation);
        });
    }

    public void stop() {
        running.set(false);
        long generation = lifecycleGeneration.incrementAndGet();
        runOnEdt(() -> {
            if (lifecycleGeneration.get() != generation || running.get()) {
                return;
            }
            stopProgressTimer();
            stopActiveEffect();
            stopTransitionEffect();
            timers.stopAll();
            progressBar.setBounds(originalBounds);
            resetEffects();
            if (options.resetOnStop()) {
                setProgressValueImmediately(0);
                progressValue = 0;
            }
            if (options.hideOnStop()) {
                progressBar.setVisible(false);
            }
            Engine.getLOGGER().debug("{} progress animator stopped: generation={}", logPrefix, generation);
        });
    }

    @Override
    public List<String> loadedMessages() {
        return messages;
    }

    private void loadMessagesFromJson(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            messages = List.of();
            return;
        }
        try (InputStream inputStream = ResourceLoader.open(filePath, ProgressBarAnimator.class.getClassLoader());
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);
            List<String> loaded = new ArrayList<>();
            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.size(); i++) {
                    loaded.add(jsonArray.get(i).getAsString());
                }
            }
            messages = List.copyOf(loaded);
        } catch (IOException | JsonSyntaxException | JsonIOException error) {
            Engine.getLOGGER().warn("{} unable to load progress messages: {}", logPrefix, error.getMessage());
            messages = List.of();
        }
    }

    private void loadAnimationConfig(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            animationConfig = new ProgressAnimationConfig();
            return;
        }
        try {
            ProgressAnimationConfig loaded = ResourceLoader.loadJson(
                    filePath,
                    ProgressAnimationConfig.class,
                    GSON,
                    ProgressBarAnimator.class.getClassLoader()
            );
            animationConfig = loaded == null ? new ProgressAnimationConfig() : loaded;
            TimelineKeyFrame.sort(animationConfig.entrance);
            TimelineKeyFrame.sort(animationConfig.active);
            TimelineKeyFrame.sort(animationConfig.complete);
            TimelineKeyFrame.sort(animationConfig.exit);
        } catch (Exception error) {
            Engine.getLOGGER().warn("{} unable to load progress animation config: {}", logPrefix, error.getMessage());
            animationConfig = new ProgressAnimationConfig();
        }
    }

    private void updateProgressMessage() {
        if (!options.showText()) {
            return;
        }
        List<String> source = messageResolver.get();
        if (source == null || source.isEmpty()) {
            return;
        }
        List<String> available = source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(message -> !message.isEmpty())
                .toList();
        if (available.isEmpty()) {
            return;
        }
        String message;
        if (options.randomMessages()) {
            message = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        } else {
            message = available.get(messageIndex % available.size());
            messageIndex++;
        }
        progressTextSetter.accept(message);
    }

    private void startProgressTimer(long generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }

        stopProgressTimer();
        final int maxValue = resolveMaxValue();
        progressTimer = timers.track(AnimationEngine.shared().interval(
                "progress:value-loop",
                options.progressUpdateMs(),
                options.initialDelayMs(),
                () -> {
                    if (!isCurrentGeneration(generation)) {
                        progressTimer = null;
                        return false;
                    }

                    int visibleValue = Math.min(progressValue, maxValue);
                    setProgressValue(visibleValue);
                    if (progressListener != null) {
                        progressListener.onProgress(visibleValue);
                    }

                    progressValue += options.progressStep();
                    if (progressValue > maxValue) {
                        completeProgressCycle(maxValue, generation);
                        return false;
                    }
                    return true;
                }
        ));
    }

    private void setProgressValue(int value) {
        progressValueSetter.accept(value);
    }

    private int resolveMaxValue() {
        if (options.maxValue() > 0) {
            return options.maxValue();
        }
        return Math.max(1, progressMaximumSupplier.getAsInt());
    }

    private void completeProgressCycle(int maxValue, long generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        stopProgressTimer();
        setProgressValueImmediately(maxValue);
        if (progressListener != null) {
            progressListener.onProgress(maxValue);
        }
        stopActiveEffect();
        animateProgressBarComplete(() -> {
            if (!isCurrentGeneration(generation)) {
                return;
            }
            animateProgressBarExit(generation, () -> {
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                progressValue = 0;
                if (progressListener != null) {
                    progressListener.onComplete();
                }
                if (options.loop()) {
                    runAfterCycleDelay(generation, () -> beginEntranceCycle(generation));
                } else {
                    running.set(false);
                }
            });
        });
    }

    private void runAfterCycleDelay(long generation, Runnable action) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        if (options.cycleDelayMs() <= 0) {
            action.run();
            return;
        }
        final AnimationEngine.Handle[] delay = {null};
        delay[0] = timers.track(AnimationEngine.shared().delay("progress:cycle-delay", options.cycleDelayMs(), () -> {
            if (delay[0] != null) {
                timers.forget(delay[0]);
            }
            if (isCurrentGeneration(generation)) {
                action.run();
            }
        }));
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            timers.stop(progressTimer);
            progressTimer = null;
        }
    }

    private void animateProgressBarEntrance(long generation, Runnable onComplete) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        stopTransitionEffect();
        List<TimelineKeyFrame> entrance = animationConfig.entrance;
        if (!options.animateEntrance() || entrance == null || entrance.isEmpty()) {
            resetEffects();
            revealProgressBar();
            onComplete.run();
            return;
        }

        TimelineKeyFrame settledFrame = entrance.get(entrance.size() - 1);
        // Timeline execution publishes keyframe zero synchronously. Reveal only after that state
        // has been applied, so the fixed frame and partially visible content appear together.
        transitionEffect = animateWithTimeline(options.timelineDurationMs(), entrance, () -> {
            transitionEffect = null;
            if (!isCurrentGeneration(generation)) {
                return;
            }
            applyKeyFrame(settledFrame);
            revealProgressBar();
            onComplete.run();
        });
        revealProgressBar();
    }

    private void beginEntranceCycle(long generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        stopTransitionEffect();
        resetEffects();
        prepareEntranceContent();
        animateProgressBarEntrance(generation, () -> {
            if (!isCurrentGeneration(generation)) {
                return;
            }
            startActiveEffectLoop();
            startProgressTimer(generation);
        });
    }

    private void prepareEntranceContent() {
        int maxValue = resolveMaxValue();
        int initialVisibleValue = Math.min(maxValue, Math.max(1, options.progressStep()));
        progressValue = initialVisibleValue;
        setProgressValueImmediately(initialVisibleValue);
        updateProgressMessage();
    }

    private void setProgressValueImmediately(int value) {
        if (progressBar instanceof ProgressBar composite) {
            composite.setValueImmediately(value);
        } else {
            setProgressValue(value);
        }
    }

    private void stopTransitionEffect() {
        if (transitionEffect != null) {
            timers.stop(transitionEffect);
            transitionEffect = null;
        }
    }

    private boolean isCurrentGeneration(long generation) {
        return running.get() && lifecycleGeneration.get() == generation;
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private void startActiveEffectLoop() {
        List<TimelineKeyFrame> active = animationConfig.active;
        if (!options.animateActive() || active == null || active.isEmpty() || !running.get()) {
            return;
        }
        long generation = ++activeEffectGeneration;
        playActiveEffect(active, generation);
    }

    private void playActiveEffect(List<TimelineKeyFrame> active, long generation) {
        if (!running.get() || generation != activeEffectGeneration) {
            return;
        }
        activeEffect = animateWithTimeline(
                options.activeTimelineDurationMs(),
                active,
                () -> {
                    activeEffect = null;
                    if (running.get() && generation == activeEffectGeneration) {
                        playActiveEffect(active, generation);
                    }
                }
        );
    }

    private void stopActiveEffect() {
        activeEffectGeneration++;
        if (activeEffect != null) {
            timers.stop(activeEffect);
            activeEffect = null;
        }
    }

    private void animateProgressBarComplete(Runnable onComplete) {
        List<TimelineKeyFrame> complete = animationConfig.complete;
        if (!options.animateComplete() || complete == null || complete.isEmpty()) {
            onComplete.run();
            return;
        }
        animateWithTimeline(options.completeTimelineDurationMs(), complete, onComplete);
    }

    private void animateProgressBarExit(long generation, Runnable onComplete) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        List<TimelineKeyFrame> exit = animationConfig.exit;
        if (!options.animateExit() || exit == null || exit.isEmpty()) {
            if (options.hideOnStop() && !usesHearthstoneContentSlide()) {
                progressBar.setVisible(false);
            }
            onComplete.run();
            return;
        }
        stopTransitionEffect();
        transitionEffect = animateWithTimeline(options.timelineDurationMs(), exit, () -> {
            transitionEffect = null;
            if (!isCurrentGeneration(generation)) {
                return;
            }
            if (!usesHearthstoneContentSlide()) {
                progressBar.setVisible(false);
            }
            onComplete.run();
        });
    }

    private boolean usesHearthstoneContentSlide() {
        return progressBar instanceof ProgressBar composite && composite.usesHearthstoneEffect();
    }

    private AnimationEngine.Handle animateWithTimeline(int durationMs,
                                                        List<TimelineKeyFrame> keyFrames,
                                                        Runnable onComplete) {
        return timelineAnimator.animate(durationMs, keyFrames, this::applyTimelineState, onComplete);
    }

    private void applyKeyFrame(TimelineKeyFrame frame) {
        applyTimelineState(new TimelineFrameState(
                frame.time(),
                frame.scaleX(),
                frame.scaleY(),
                frame.offsetX(),
                frame.offsetY(),
                frame.opacity(),
                frame.glow(),
                frame.shine()
        ));
    }

    private void applyTimelineState(TimelineFrameState state) {
        if (usesHearthstoneContentSlide()) {
            ProgressBar composite = (ProgressBar) progressBar;
            progressBar.setBounds(originalBounds);
            progressOpacitySetter.accept(1.0);
            composite.setContentAnimationOffsetY(state.offsetY());
            composite.setContentAnimationScale(state.scaleX(), state.scaleY());
            composite.setContentAnimationOpacity(state.opacity());
        } else {
            int newWidth = Math.max(0, (int) Math.round(originalBounds.width * state.scaleX()));
            int newHeight = Math.max(0, (int) Math.round(originalBounds.height * state.scaleY()));
            int newX = originalBounds.x - (newWidth - originalBounds.width) / 2 + state.offsetX();
            int newY = originalBounds.y - (newHeight - originalBounds.height) / 2 + state.offsetY();
            progressBar.setBounds(newX, newY, newWidth, newHeight);
            progressOpacitySetter.accept(state.opacity());
        }
        progressGlowSetter.accept(state.glow());
        progressShineSetter.accept(state.shine());
    }

    private void revealProgressBar() {
        progressBar.setVisible(true);
        progressBar.revalidate();
        progressBar.repaint();
        if (progressBar.getParent() != null) {
            Rectangle bounds = progressBar.getBounds();
            progressBar.getParent().repaint(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    private void resetEffects() {
        progressOpacitySetter.accept(1.0);
        if (progressBar instanceof ProgressBar composite) {
            composite.resetContentAnimation();
        }
        progressGlowSetter.accept(0.0);
        progressShineSetter.accept(-1.0);
    }

    private static final class ProgressAnimationConfig {
        private List<TimelineKeyFrame> entrance;
        private List<TimelineKeyFrame> active;
        private List<TimelineKeyFrame> complete;
        private List<TimelineKeyFrame> exit;
    }
}
