package org.takesome.kaylasEngine.gui.animation.internal.scheduling;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/** Package-private EDT-confined resource owner. */
final class DefaultAnimationResourceGroup implements AnimationResourceGroup {
    private final List<Timer> timers = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();

    @Override public void start(Timer timer) {
        if (timer == null) return;
        SwingEdt.run(() -> { if (!timers.contains(timer)) timers.add(timer); timer.start(); });
    }
    @Override public void stop(Timer timer) {
        if (timer == null) return;
        SwingEdt.run(() -> { timer.stop(); timers.remove(timer); });
    }
    @Override public <T extends AutoCloseable> T track(T resource) {
        if (resource == null) return null;
        SwingEdt.run(() -> { if (!resources.contains(resource)) resources.add(resource); });
        return resource;
    }
    @Override public void stop(AutoCloseable resource) {
        if (resource == null) return;
        SwingEdt.run(() -> { closeQuietly(resource); resources.remove(resource); });
    }
    @Override public void forget(AutoCloseable resource) {
        if (resource != null) SwingEdt.run(() -> resources.remove(resource));
    }
    @Override public void stopAll() {
        SwingEdt.run(() -> {
            for (Timer timer : List.copyOf(timers)) timer.stop();
            timers.clear();
            for (AutoCloseable resource : List.copyOf(resources)) closeQuietly(resource);
            resources.clear();
        });
    }
    @Override public int size() { return timers.size() + resources.size(); }

    private static void closeQuietly(AutoCloseable resource) {
        try { resource.close(); } catch (Exception ignored) { /* Best-effort teardown. */ }
    }
}
