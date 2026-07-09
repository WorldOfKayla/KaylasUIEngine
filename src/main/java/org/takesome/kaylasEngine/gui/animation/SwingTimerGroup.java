package org.takesome.kaylasEngine.gui.animation;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * Small lifecycle owner for Swing timers.
 *
 * <p>Use this for UI-only loops instead of submitting long-lived animation tasks to background
 * executors. Timer mutation is marshalled to the EDT, and {@link #stopAll()} can be used by windows
 * and panels during disposal.</p>
 */
public final class SwingTimerGroup implements AutoCloseable {
    private final List<Timer> timers = new ArrayList<>();

    public void start(Timer timer) {
        if (timer == null) {
            return;
        }
        runOnEdt(() -> {
            if (!timers.contains(timer)) {
                timers.add(timer);
            }
            timer.start();
        });
    }

    public void stop(Timer timer) {
        if (timer == null) {
            return;
        }
        runOnEdt(() -> {
            timer.stop();
            timers.remove(timer);
        });
    }

    public void stopAll() {
        runOnEdt(() -> {
            for (Timer timer : List.copyOf(timers)) {
                timer.stop();
            }
            timers.clear();
        });
    }

    public int size() {
        return timers.size();
    }

    @Override
    public void close() {
        stopAll();
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
