package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.Engine;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Snapshot-based Swing drawer animator shared by launchers and engine-owned UIs.
 *
 * <p>The animated component tree is rendered once into a lightweight image layer. During the
 * transition only that image layer moves, so complex forms, textured buttons and nested panels do not
 * re-layout or repaint on every animation tick. The live drawer is restored only when the transition
 * reaches its final state.</p>
 */
public final class SnapshotDrawerAnimator {
    private static final String ANIMATION_KEY = "kaylas.drawer.animation";
    private static final String SNAPSHOT_KEY = "kaylas.drawer.snapshot";
    private static final String OPEN_KEY = "kaylas.drawer.open";
    private static final String TARGET_OPEN_KEY = "kaylas.drawer.targetOpen";
    private static final String OPEN_BOUNDS_KEY = "kaylas.drawer.openBounds";
    private static final String OPEN_PARENT_SIZE_KEY = "kaylas.drawer.openParentSize";
    private static final String CONTROL_BOUNDS_KEY = "kaylas.drawer.control.bounds";
    private static final String CONTROL_PARENT_KEY = "kaylas.drawer.control.parent";
    private static final String CONTROL_Z_ORDER_KEY = "kaylas.drawer.control.zOrder";

    private static final int DEFAULT_DURATION_MS = 360;
    private static final int DEFAULT_MIN_FRAME_DELAY_MS = 8;
    private static final int DEFAULT_MAX_FRAME_DELAY_MS = 17;
    private static final int DEFAULT_REPAINT_PADDING_PX = 2;
    private static final long SNAPSHOT_WARN_NANOS = 24_000_000L;

    private final Supplier<? extends JComponent> drawerSupplier;
    private final Supplier<? extends JComponent> controlSupplier;
    private final Edge edge;
    private final int durationMs;
    private final int minFrameDelayMs;
    private final int maxFrameDelayMs;
    private final int repaintPaddingPx;
    private final Consumer<StateChange> onTargetStateChanged;
    private final Consumer<StateChange> onFinished;

    private SnapshotDrawerAnimator(Builder builder) {
        this.drawerSupplier = Objects.requireNonNull(builder.drawerSupplier, "drawerSupplier");
        this.controlSupplier = builder.controlSupplier;
        this.edge = Objects.requireNonNull(builder.edge, "edge");
        this.durationMs = Math.max(1, builder.durationMs);
        this.minFrameDelayMs = Math.max(1, builder.minFrameDelayMs);
        this.maxFrameDelayMs = Math.max(this.minFrameDelayMs, builder.maxFrameDelayMs);
        this.repaintPaddingPx = Math.max(0, builder.repaintPaddingPx);
        this.onTargetStateChanged = builder.onTargetStateChanged;
        this.onFinished = builder.onFinished;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Toggles the drawer between the remembered open state and its closed off-screen state. */
    public void toggle() {
        runOnEdt(() -> setOpenOnEdt(!isOpenOnEdt(resolveDrawer())));
    }

    /** Opens the drawer if it is not already open. */
    public void open() {
        setOpen(true);
    }

    /** Closes the drawer if it is not already closed. */
    public void close() {
        setOpen(false);
    }

    /** Moves the drawer to the requested state. Calls from non-EDT threads are marshalled to EDT. */
    public void setOpen(boolean open) {
        runOnEdt(() -> setOpenOnEdt(open));
    }

    /** Returns the current remembered state. Safe to call from any thread. */
    public boolean isOpen() {
        if (SwingUtilities.isEventDispatchThread()) {
            return isOpenOnEdt(resolveDrawer());
        }
        final boolean[] value = {false};
        try {
            SwingUtilities.invokeAndWait(() -> value[0] = isOpenOnEdt(resolveDrawer()));
        } catch (Exception error) {
            Engine.getLOGGER().warn("Unable to resolve drawer state", error);
        }
        return value[0];
    }

    private void setOpenOnEdt(boolean targetOpen) {
        JComponent drawer = resolveDrawer();
        if (drawer == null) {
            Engine.getLOGGER().warn("Drawer animation skipped: drawer component is null.");
            return;
        }
        setOpenOnEdt(drawer, targetOpen);
    }

    private void setOpenOnEdt(JComponent drawer, boolean targetOpen) {
        try {
            Container root = drawer.getParent();
            if (root == null) {
                Engine.getLOGGER().warn("Drawer animation skipped: drawer parent is null.");
                return;
            }

            if (isAnimating(drawer)) {
                Engine.getLOGGER().debug("Drawer animation skipped: transition is already running.");
                return;
            }
            if (isOpenOnEdt(drawer) == targetOpen) {
                ensureFinalState(drawer, root, targetOpen, resolveControl(), null);
                return;
            }

            JComponent control = resolveControl();
            if (control != null) {
                rememberControlBounds(control);
            }

            Rectangle openBounds = resolveOpenBounds(drawer, root);
            Rectangle closedBounds = closedBounds(openBounds);
            Rectangle startBounds = targetOpen ? closedBounds : openBounds;
            Rectangle endBounds = targetOpen ? openBounds : closedBounds;

            long snapshotStartedAt = System.nanoTime();
            BufferedImage snapshotImage = createSnapshot(drawer, openBounds);
            long snapshotElapsed = System.nanoTime() - snapshotStartedAt;
            if (snapshotElapsed >= SNAPSHOT_WARN_NANOS) {
                Engine.getLOGGER().debug("Drawer snapshot rendered in {} ms", snapshotElapsed / 1_000_000L);
            }

            JLabel snapshot = createSnapshotLabel(snapshotImage, startBounds);
            drawer.setVisible(false);
            drawer.setBounds(openBounds);
            drawer.putClientProperty(SNAPSHOT_KEY, snapshot);
            drawer.putClientProperty(TARGET_OPEN_KEY, targetOpen);

            root.add(snapshot);
            if (control != null) {
                floatControlAboveOverlay(control, root);
                bringOverlayBelowFloatingControl(root, snapshot, control);
                fire(onTargetStateChanged, new StateChange(drawer, control, targetOpen));
            } else {
                bringToFront(root, snapshot);
                fire(onTargetStateChanged, new StateChange(drawer, null, targetOpen));
            }

            repaint(root, snapshot.getBounds());
            animateSnapshot(drawer, root, snapshot, openBounds, closedBounds, startBounds, endBounds, targetOpen, control);
        } catch (Exception error) {
            Engine.getLOGGER().error("Exception in drawer animation", error);
        }
    }

    private JComponent resolveDrawer() {
        return drawerSupplier == null ? null : drawerSupplier.get();
    }

    private JComponent resolveControl() {
        return controlSupplier == null ? null : controlSupplier.get();
    }

    private boolean isAnimating(JComponent drawer) {
        Object timerObject = drawer.getClientProperty(ANIMATION_KEY);
        return timerObject instanceof Timer timer && timer.isRunning();
    }

    private void animateSnapshot(JComponent drawer,
                                 Container root,
                                 JComponent snapshot,
                                 Rectangle openBounds,
                                 Rectangle closedBounds,
                                 Rectangle startBounds,
                                 Rectangle endBounds,
                                 boolean targetOpen,
                                 JComponent control) {
        long startedAt = System.nanoTime();
        int frameDelayMs = frameDelayMs(root);
        long durationNanos = durationMs * 1_000_000L;

        Timer timer = new Timer(frameDelayMs, null);
        timer.setCoalesce(true);
        timer.setRepeats(true);
        drawer.putClientProperty(ANIMATION_KEY, timer);
        timer.addActionListener(event -> {
            try {
                float progress = clamp01((System.nanoTime() - startedAt) / (float) durationNanos);
                float eased = easeInOutCubic(progress);
                int newX = Math.round(startBounds.x + (endBounds.x - startBounds.x) * eased);
                int newY = Math.round(startBounds.y + (endBounds.y - startBounds.y) * eased);

                Rectangle before = snapshot.getBounds();
                if (before.x != newX || before.y != newY) {
                    snapshot.setLocation(newX, newY);
                    repaintUnion(root, before, snapshot.getBounds());
                }

                if (progress >= 1f) {
                    timer.stop();
                    finish(drawer, root, snapshot, openBounds, closedBounds, targetOpen, control);
                }
            } catch (Exception error) {
                Engine.getLOGGER().error("Error during drawer animation", error);
                timer.stop();
                finish(drawer, root, snapshot, openBounds, closedBounds, targetOpen, control);
            }
        });
        timer.start();
    }

    private void finish(JComponent drawer,
                        Container root,
                        JComponent snapshot,
                        Rectangle openBounds,
                        Rectangle closedBounds,
                        boolean open,
                        JComponent control) {
        Rectangle dirty = snapshot == null ? null : snapshot.getBounds();
        if (snapshot != null && snapshot.getParent() == root) {
            root.remove(snapshot);
        }

        drawer.putClientProperty(ANIMATION_KEY, null);
        drawer.putClientProperty(SNAPSHOT_KEY, null);
        drawer.putClientProperty(TARGET_OPEN_KEY, null);
        drawer.putClientProperty(OPEN_KEY, open);

        ensureFinalState(drawer, root, open, control, open ? openBounds : closedBounds);
        root.revalidate();
        repaintUnion(root, dirty, open ? drawer.getBounds() : closedBounds);
        fire(onFinished, new StateChange(drawer, control, open));
    }

    private void ensureFinalState(JComponent drawer,
                                  Container root,
                                  boolean open,
                                  JComponent control,
                                  Rectangle boundsOverride) {
        Rectangle openBounds = boundsOverride == null && open ? resolveOpenBounds(drawer, root) : null;
        Rectangle closedBounds = boundsOverride == null && !open ? closedBounds(resolveOpenBounds(drawer, root)) : null;
        Rectangle targetBounds = boundsOverride != null ? boundsOverride : (open ? openBounds : closedBounds);

        drawer.putClientProperty(OPEN_KEY, open);
        drawer.setBounds(targetBounds);
        drawer.setVisible(open);

        if (open) {
            bringOverlayBelowFloatingControl(root, drawer, control);
        } else if (control != null) {
            restoreControl(control);
        }
    }

    private Rectangle resolveOpenBounds(JComponent drawer, Container parent) {
        Dimension size = resolveSize(drawer, parent);
        Insets insets = parent instanceof JComponent component ? component.getInsets() : null;
        int parentLeft = insets == null ? 0 : insets.left;
        int parentTop = insets == null ? 0 : insets.top;
        Dimension parentSize = parent.getSize();

        Object stored = drawer.getClientProperty(OPEN_BOUNDS_KEY);
        Object storedParentSize = drawer.getClientProperty(OPEN_PARENT_SIZE_KEY);
        if (stored instanceof Rectangle bounds
                && storedParentSize instanceof Dimension previousParentSize
                && previousParentSize.equals(parentSize)
                && bounds.width == size.width
                && bounds.height == size.height) {
            return new Rectangle(bounds);
        }

        int openX = drawer.getX() >= parentLeft ? drawer.getX() : parentLeft;
        int openY = drawer.getY() >= parentTop ? drawer.getY() : parentTop;
        Rectangle bounds = new Rectangle(openX, openY, size.width, size.height);
        drawer.putClientProperty(OPEN_BOUNDS_KEY, new Rectangle(bounds));
        drawer.putClientProperty(OPEN_PARENT_SIZE_KEY, new Dimension(parentSize));
        return bounds;
    }

    private Dimension resolveSize(JComponent drawer, Container parent) {
        int width = drawer.getWidth();
        int height = drawer.getHeight();
        Dimension preferred = drawer.getPreferredSize();

        if (width <= 0 && preferred != null && preferred.width > 0) {
            width = preferred.width;
        }
        if (height <= 0 && preferred != null && preferred.height > 0) {
            height = preferred.height;
        }
        if (width <= 0) {
            width = Math.max(1, parent == null ? 1 : parent.getWidth());
        }
        if (height <= 0) {
            height = Math.max(1, parent == null ? 1 : parent.getHeight());
        }
        return new Dimension(width, height);
    }

    private Rectangle closedBounds(Rectangle openBounds) {
        return switch (edge) {
            case LEFT -> new Rectangle(openBounds.x - openBounds.width, openBounds.y, openBounds.width, openBounds.height);
            case RIGHT -> new Rectangle(openBounds.x + openBounds.width, openBounds.y, openBounds.width, openBounds.height);
            case TOP -> new Rectangle(openBounds.x, openBounds.y - openBounds.height, openBounds.width, openBounds.height);
            case BOTTOM -> new Rectangle(openBounds.x, openBounds.y + openBounds.height, openBounds.width, openBounds.height);
        };
    }

    private BufferedImage createSnapshot(JComponent drawer, Rectangle openBounds) {
        Rectangle previousBounds = drawer.getBounds();
        boolean previousVisible = drawer.isVisible();
        boolean previousDoubleBuffered = drawer.isDoubleBuffered();

        drawer.setBounds(openBounds);
        drawer.setVisible(true);
        drawer.setDoubleBuffered(false);
        drawer.doLayout();
        drawer.validate();

        BufferedImage snapshot = new BufferedImage(
                Math.max(1, openBounds.width),
                Math.max(1, openBounds.height),
                BufferedImage.TYPE_INT_ARGB_PRE
        );
        Graphics2D graphics = snapshot.createGraphics();
        try {
            drawer.paint(graphics);
        } finally {
            graphics.dispose();
            drawer.setDoubleBuffered(previousDoubleBuffered);
            drawer.setVisible(previousVisible);
            drawer.setBounds(previousBounds);
        }
        return snapshot;
    }

    private JLabel createSnapshotLabel(BufferedImage image, Rectangle startBounds) {
        JLabel label = new JLabel(new ImageIcon(image));
        label.setOpaque(false);
        label.setDoubleBuffered(false);
        label.setBounds(startBounds);
        return label;
    }

    private boolean isOpenOnEdt(JComponent drawer) {
        if (drawer == null) {
            return false;
        }
        Object open = drawer.getClientProperty(OPEN_KEY);
        return open instanceof Boolean value ? value : drawer.isVisible();
    }

    private int frameDelayMs(java.awt.Component component) {
        int refreshRate = refreshRate(component);
        int calculated = Math.round(1000f / refreshRate);
        return Math.max(minFrameDelayMs, Math.min(maxFrameDelayMs, calculated));
    }

    private int refreshRate(java.awt.Component component) {
        GraphicsConfiguration configuration = component.getGraphicsConfiguration();
        if (configuration != null && configuration.getDevice() != null && configuration.getDevice().getDisplayMode() != null) {
            int refreshRate = configuration.getDevice().getDisplayMode().getRefreshRate();
            if (refreshRate > 0 && refreshRate != DisplayMode.REFRESH_RATE_UNKNOWN) {
                return Math.max(60, Math.min(refreshRate, 144));
            }
        }
        return 60;
    }

    private void bringOverlayBelowFloatingControl(Container root, JComponent overlay, JComponent control) {
        if (root == null || overlay == null || overlay.getParent() != root) {
            return;
        }

        if (control != null && control.getParent() == root) {
            int overlayZOrder = Math.min(1, root.getComponentCount() - 1);
            if (root.getComponentZOrder(overlay) != overlayZOrder) {
                root.setComponentZOrder(overlay, overlayZOrder);
            }
            if (root.getComponentZOrder(control) > 0) {
                root.setComponentZOrder(control, 0);
            }
        } else {
            bringToFront(root, overlay);
        }
    }

    private void bringToFront(Container root, JComponent component) {
        if (root != null && component != null && component.getParent() == root && root.getComponentZOrder(component) > 0) {
            root.setComponentZOrder(component, 0);
        }
    }

    private void rememberControlBounds(JComponent control) {
        Object stored = control.getClientProperty(CONTROL_BOUNDS_KEY);
        if (stored instanceof Rectangle bounds && bounds.width > 0 && bounds.height > 0) {
            return;
        }

        Rectangle bounds = control.getBounds();
        if (bounds.width > 0 && bounds.height > 0) {
            control.putClientProperty(CONTROL_BOUNDS_KEY, new Rectangle(bounds));
        }
    }

    private void rememberOriginalControlPlacement(JComponent control, Container parent) {
        if (!(control.getClientProperty(CONTROL_PARENT_KEY) instanceof Container)) {
            control.putClientProperty(CONTROL_PARENT_KEY, parent);
        }

        rememberControlBounds(control);

        if (!(control.getClientProperty(CONTROL_Z_ORDER_KEY) instanceof Integer)) {
            int zOrder = parent.getComponentZOrder(control);
            if (zOrder >= 0) {
                control.putClientProperty(CONTROL_Z_ORDER_KEY, zOrder);
            }
        }
    }

    private void floatControlAboveOverlay(JComponent control, Container root) {
        Container currentParent = control.getParent();
        if (currentParent == root) {
            control.setVisible(true);
            control.setEnabled(true);
            if (root.getComponentZOrder(control) > 0) {
                root.setComponentZOrder(control, 0);
            }
            repaint(root, control.getBounds());
            return;
        }

        if (currentParent == null) {
            control.setVisible(true);
            control.setEnabled(true);
            return;
        }

        rememberOriginalControlPlacement(control, currentParent);

        Rectangle bounds = control.getBounds();
        Point rootLocation = SwingUtilities.convertPoint(currentParent, bounds.x, bounds.y, root);
        currentParent.remove(control);

        control.setBounds(rootLocation.x, rootLocation.y, bounds.width, bounds.height);
        root.add(control);
        root.setComponentZOrder(control, 0);
        control.setVisible(true);
        control.setEnabled(true);

        currentParent.revalidate();
        currentParent.repaint();
        root.revalidate();
        repaint(root, control.getBounds());
    }

    private void restoreControl(JComponent control) {
        Object storedParent = control.getClientProperty(CONTROL_PARENT_KEY);
        if (!(storedParent instanceof Container originalParent)) {
            control.setVisible(true);
            control.setEnabled(true);
            Container parent = control.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
            return;
        }

        Container currentParent = control.getParent();
        if (currentParent != originalParent) {
            if (currentParent != null) {
                currentParent.remove(control);
            }
            originalParent.add(control);
        }

        Object storedBounds = control.getClientProperty(CONTROL_BOUNDS_KEY);
        if (storedBounds instanceof Rectangle bounds && bounds.width > 0 && bounds.height > 0) {
            control.setBounds(new Rectangle(bounds));
        }

        Object storedZOrder = control.getClientProperty(CONTROL_Z_ORDER_KEY);
        if (storedZOrder instanceof Integer zOrder && zOrder >= 0) {
            originalParent.setComponentZOrder(control, Math.min(zOrder, originalParent.getComponentCount() - 1));
        }

        control.setVisible(true);
        control.setEnabled(true);
        control.putClientProperty(CONTROL_PARENT_KEY, null);
        control.putClientProperty(CONTROL_Z_ORDER_KEY, null);

        if (currentParent != null && currentParent != originalParent) {
            currentParent.revalidate();
            currentParent.repaint();
        }
        originalParent.revalidate();
        originalParent.repaint();
    }

    private void repaint(Container parent, Rectangle dirty) {
        if (parent == null) {
            return;
        }
        if (dirty == null) {
            parent.repaint();
            return;
        }
        parent.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    private void repaintUnion(Container parent, Rectangle first, Rectangle second) {
        if (parent == null) {
            return;
        }
        Rectangle dirty = first == null ? second : first.union(second);
        if (dirty == null) {
            parent.repaint();
            return;
        }
        dirty.grow(repaintPaddingPx, repaintPaddingPx);
        parent.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    private float easeInOutCubic(float value) {
        float clamped = clamp01(value);
        return clamped < 0.5f
                ? 4f * clamped * clamped * clamped
                : 1f - (float) Math.pow(-2f * clamped + 2f, 3f) / 2f;
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void fire(Consumer<StateChange> listener, StateChange event) {
        if (listener != null) {
            listener.accept(event);
        }
    }

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
            this.edge = edge;
            return this;
        }

        public Builder durationMs(int durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder frameDelayRangeMs(int minFrameDelayMs, int maxFrameDelayMs) {
            this.minFrameDelayMs = minFrameDelayMs;
            this.maxFrameDelayMs = maxFrameDelayMs;
            return this;
        }

        public Builder repaintPaddingPx(int repaintPaddingPx) {
            this.repaintPaddingPx = repaintPaddingPx;
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
