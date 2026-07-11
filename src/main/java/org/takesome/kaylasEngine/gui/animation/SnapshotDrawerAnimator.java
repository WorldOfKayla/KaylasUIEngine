package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.drawer.DrawerAnimationController;

import javax.swing.JComponent;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Public facade for a snapshot-backed sliding drawer animation. */
public final class SnapshotDrawerAnimator {
    private static final int DEFAULT_DURATION_MS = 360;
    private static final int DEFAULT_MIN_FRAME_DELAY_MS = 8;
    private static final int DEFAULT_MAX_FRAME_DELAY_MS = 17;
    private static final int DEFAULT_REPAINT_PADDING_PX = 2;

    private final DrawerAnimationController controller;

    private SnapshotDrawerAnimator(Builder builder) {
        controller = DrawerAnimationController.create(new DrawerAnimationController.Config(
                Objects.requireNonNull(builder.drawerSupplier, "drawerSupplier"),
                builder.controlSupplier,
                Objects.requireNonNull(builder.edge, "edge"),
                builder.durationMs,
                builder.minFrameDelayMs,
                builder.maxFrameDelayMs,
                builder.repaintPaddingPx,
                builder.onTargetStateChanged,
                builder.onFinished
        ));
    }

    public static Builder builder() { return new Builder(); }
    public void toggle() { controller.toggle(); }
    public void open() { controller.open(); }
    public void close() { controller.close(); }
    public void setOpen(boolean open) { controller.setOpen(open); }
    public boolean isOpen() { return controller.isOpen(); }

    public enum Edge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    public record StateChange(JComponent drawer, JComponent control, boolean open) { }

    public static final class Builder {
        private Supplier<? extends JComponent> drawerSupplier;
        private Supplier<? extends JComponent> controlSupplier;
        private Edge edge = Edge.LEFT;
        private int durationMs = DEFAULT_DURATION_MS;
        private int minFrameDelayMs = DEFAULT_MIN_FRAME_DELAY_MS;
        private int maxFrameDelayMs = DEFAULT_MAX_FRAME_DELAY_MS;
        private int repaintPaddingPx = DEFAULT_REPAINT_PADDING_PX;
        private Consumer<StateChange> onTargetStateChanged;
        private Consumer<StateChange> onFinished;

        private Builder() { }

        public Builder drawerSupplier(Supplier<? extends JComponent> drawerSupplier) {
            this.drawerSupplier = drawerSupplier;
            return this;
        }

        public Builder panelSupplier(Supplier<? extends JComponent> drawerSupplier) {
            return drawerSupplier(drawerSupplier);
        }

        public Builder controlSupplier(Supplier<? extends JComponent> controlSupplier) {
            this.controlSupplier = controlSupplier;
            return this;
        }

        public Builder edge(Edge edge) {
            this.edge = Objects.requireNonNull(edge, "edge");
            return this;
        }

        public Builder durationMs(int durationMs) {
            this.durationMs = Math.max(1, durationMs);
            return this;
        }

        public Builder frameDelayRangeMs(int minFrameDelayMs, int maxFrameDelayMs) {
            this.minFrameDelayMs = Math.max(1, minFrameDelayMs);
            this.maxFrameDelayMs = Math.max(this.minFrameDelayMs, maxFrameDelayMs);
            return this;
        }

        public Builder repaintPaddingPx(int repaintPaddingPx) {
            this.repaintPaddingPx = Math.max(0, repaintPaddingPx);
            return this;
        }

        public Builder onTargetStateChanged(Consumer<StateChange> onTargetStateChanged) {
            this.onTargetStateChanged = onTargetStateChanged;
            return this;
        }

        public Builder onFinished(Consumer<StateChange> onFinished) {
            this.onFinished = onFinished;
            return this;
        }

        public SnapshotDrawerAnimator build() {
            return new SnapshotDrawerAnimator(this);
        }
    }
}
