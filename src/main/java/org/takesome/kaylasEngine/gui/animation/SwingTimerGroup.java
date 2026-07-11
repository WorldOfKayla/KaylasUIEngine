package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.scheduling.AnimationResourceGroup;

import javax.swing.Timer;

/** Lifecycle owner for Swing timers and shared-pulse subscriptions. */
public final class SwingTimerGroup implements AutoCloseable {
    private final AnimationResourceGroup resources = AnimationResourceGroup.create();

    public void start(Timer timer) { resources.start(timer); }
    public void stop(Timer timer) { resources.stop(timer); }
    public <T extends AutoCloseable> T track(T resource) { return resources.track(resource); }
    public void stop(AutoCloseable resource) { resources.stop(resource); }
    public void forget(AutoCloseable resource) { resources.forget(resource); }
    public void stopAll() { resources.stopAll(); }
    public int size() { return resources.size(); }
    @Override public void close() { stopAll(); }
}
