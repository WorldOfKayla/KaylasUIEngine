package org.takesome.kaylasEngine.gui.animation;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle owner for Swing timers and shared-pulse subscriptions.
 *
 * <p>Timer and animation mutation is marshalled to the EDT. Windows and panels can call
 * {@link #stopAll()} during disposal without knowing which scheduling backend an animation uses.</p>
 */
public final class SwingTimerGroup implements AutoCloseable {
    private final List<Timer> timers = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();

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

    public <T extends AutoCloseable> T track(T resource) {
        if (resource == null) {
            return null;
        }
        runOnEdt(() -> {
            if (!resources.contains(resource)) {
                resources.add(resource);
            }
        });
        return resource;
    }

    public void stop(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        runOnEdt(() -> {
            closeQuietly(resource);
            resources.remove(resource);
        });
    }

    /** Removes an already-completed resource without closing it again. */
    public void forget(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        runOnEdt(() -> resources.remove(resource));
    }

    public void stopAll() {
        runOnEdt(() -> {
            for (Timer timer : List.copyOf(timers)) {
                timer.stop();
            }
            timers.clear();

            for (AutoCloseable resource : List.copyOf(resources)) {
                closeQuietly(resource);
            }
            resources.clear();
        });
    }

    public int size() {
        return timers.size() + resources.size();
    }

    @Override
    public void close() {
        stopAll();
    }

    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
            // Animation disposal is best-effort and must not block window teardown.
        }
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
