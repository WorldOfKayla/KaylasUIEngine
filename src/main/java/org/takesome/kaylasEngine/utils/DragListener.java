package org.takesome.kaylasEngine.utils;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Objects;

/**
 * Reusable drag listener for moving undecorated windows.
 *
 * <p>When applied to a container, dragging is installed across its complete non-interactive
 * component hierarchy. Children added later are registered automatically. Interactive controls
 * such as buttons, selectors and text components remain fully clickable and are never used as
 * drag handles.</p>
 */
public class DragListener extends MouseAdapter implements ContainerListener {

    /** Client property that disables inherited window dragging for a component subtree. */
    public static final String DRAG_DISABLED_PROPERTY = "windowDragDisabled";

    private final Window targetWindow;
    private Point clickOffset;

    public DragListener(Window targetWindow) {
        this.targetWindow = Objects.requireNonNull(targetWindow, "targetWindow");
    }

    /**
     * Applies drag behavior to a component hierarchy, even before it is attached to a window.
     *
     * @param component root drag-handle component
     */
    public void apply(Component component) {
        if (component == null) {
            return;
        }
        installRecursively(component);
    }

    /**
     * Applies drag behavior to a component hierarchy for the supplied window.
     *
     * @param component    root drag-handle component
     * @param targetWindow window being moved
     */
    public void apply(Component component, Window targetWindow) {
        if (component == null || targetWindow == null) {
            return;
        }
        if (this.targetWindow != targetWindow) {
            new DragListener(targetWindow).apply(component);
            return;
        }
        installRecursively(component);
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) || !targetWindow.isShowing()) {
            return;
        }
        try {
            Point windowLocation = targetWindow.getLocationOnScreen();
            Point clickLocation = event.getLocationOnScreen();
            clickOffset = new Point(
                    clickLocation.x - windowLocation.x,
                    clickLocation.y - windowLocation.y
            );
            event.consume();
        } catch (IllegalComponentStateException ignored) {
            clickOffset = null;
        }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        clickOffset = null;
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (clickOffset == null || !targetWindow.isShowing()) {
            return;
        }
        try {
            Point cursor = event.getLocationOnScreen();
            targetWindow.setLocation(cursor.x - clickOffset.x, cursor.y - clickOffset.y);
            event.consume();
        } catch (IllegalComponentStateException ignored) {
            clickOffset = null;
        }
    }

    @Override
    public void componentAdded(ContainerEvent event) {
        installRecursively(event.getChild());
    }

    @Override
    public void componentRemoved(ContainerEvent event) {
        uninstallRecursively(event.getChild());
    }

    private void installRecursively(Component component) {
        if (component == null || isDragDisabled(component)) {
            return;
        }

        removeOtherDragListeners(component);
        addMouseListeners(component);

        if (component instanceof Container container) {
            addContainerListener(container);
            for (Component child : container.getComponents()) {
                installRecursively(child);
            }
        }
    }

    private void uninstallRecursively(Component component) {
        if (component == null) {
            return;
        }

        component.removeMouseListener(this);
        component.removeMouseMotionListener(this);

        if (component instanceof Container container) {
            container.removeContainerListener(this);
            for (Component child : container.getComponents()) {
                uninstallRecursively(child);
            }
        }
    }

    private void addMouseListeners(Component component) {
        if (!containsIdentity(component.getMouseListeners(), this)) {
            component.addMouseListener(this);
        }
        if (!containsIdentity(component.getMouseMotionListeners(), this)) {
            component.addMouseMotionListener(this);
        }
    }

    private void addContainerListener(Container container) {
        if (!containsIdentity(container.getContainerListeners(), this)) {
            container.addContainerListener(this);
        }
    }

    private void removeOtherDragListeners(Component component) {
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener instanceof DragListener && listener != this) {
                component.removeMouseListener(listener);
            }
        }
        for (MouseMotionListener listener : component.getMouseMotionListeners()) {
            if (listener instanceof DragListener && listener != this) {
                component.removeMouseMotionListener(listener);
            }
        }
        if (component instanceof Container container) {
            for (ContainerListener listener : container.getContainerListeners()) {
                if (listener instanceof DragListener && listener != this) {
                    container.removeContainerListener(listener);
                }
            }
        }
    }

    private boolean isDragDisabled(Component component) {
        if (component instanceof JComponent swingComponent
                && Boolean.TRUE.equals(swingComponent.getClientProperty(DRAG_DISABLED_PROPERTY))) {
            return true;
        }
        return component instanceof AbstractButton
                || component instanceof JTextComponent
                || component instanceof JComboBox<?>
                || component instanceof JList<?>
                || component instanceof JTable
                || component instanceof JTree
                || component instanceof JSlider
                || component instanceof JSpinner
                || component instanceof JScrollBar;
    }

    private static boolean containsIdentity(Object[] values, Object expected) {
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }
}
