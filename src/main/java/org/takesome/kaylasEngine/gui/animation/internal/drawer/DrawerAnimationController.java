package org.takesome.kaylasEngine.gui.animation.internal.drawer;

import org.takesome.kaylasEngine.gui.animation.SnapshotDrawerAnimator.Edge;
import org.takesome.kaylasEngine.gui.animation.SnapshotDrawerAnimator.StateChange;

import javax.swing.JComponent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Internal stateful controller for snapshot-backed drawer transitions. */
public interface DrawerAnimationController {
    record Config(
            Supplier<? extends JComponent> drawerSupplier,
            Supplier<? extends JComponent> controlSupplier,
            Edge edge,
            int durationMs,
            int minFrameDelayMs,
            int maxFrameDelayMs,
            int repaintPaddingPx,
            Consumer<StateChange> onTargetStateChanged,
            Consumer<StateChange> onFinished
    ) { }

    static DrawerAnimationController create(Config config) {
        return new DefaultDrawerAnimationController(config);
    }

    void toggle();
    void open();
    void close();
    void setOpen(boolean open);
    boolean isOpen();
}
