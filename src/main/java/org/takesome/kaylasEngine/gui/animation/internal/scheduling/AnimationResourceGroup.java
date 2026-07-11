package org.takesome.kaylasEngine.gui.animation.internal.scheduling;

import javax.swing.Timer;

/** Internal ownership boundary for timers and closeable animation resources. */
public interface AnimationResourceGroup extends AutoCloseable {
    static AnimationResourceGroup create() { return new DefaultAnimationResourceGroup(); }
    void start(Timer timer);
    void stop(Timer timer);
    <T extends AutoCloseable> T track(T resource);
    void stop(AutoCloseable resource);
    void forget(AutoCloseable resource);
    void stopAll();
    int size();
    @Override default void close() { stopAll(); }
}
