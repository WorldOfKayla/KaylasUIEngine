package org.takesome.kaylasEngine.gui.animation.internal.progress;

import org.takesome.kaylasEngine.gui.animation.ProgressBarAnimator;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Internal stateful controller for progress-loop and timeline animation. */
public interface ProgressAnimationController {
    record Config(
            JComponent progressBar,
            JLabel progressText,
            String messagesResource,
            String animationConfigResource,
            String logPrefix,
            ProgressBarAnimator.Options options,
            IntConsumer progressValueSetter,
            IntSupplier progressMaximumSupplier,
            Consumer<String> progressTextSetter,
            Consumer<Boolean> progressTextVisibilitySetter,
            Consumer<Boolean> progressPercentVisibilitySetter,
            Supplier<List<String>> messageResolver
    ) { }

    static ProgressAnimationController create(Config config) {
        return new DefaultProgressAnimationController(config);
    }

    void setProgressListener(ProgressBarAnimator.ProgressListener listener);
    void startProgressTest();
    void stop();
    List<String> loadedMessages();
}
