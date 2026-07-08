package org.takesome.kaylasEngine.utils;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A smooth and responsive drag listener for moving windows (JFrame, JDialog, etc.).
 *
 * Improvements over the basic version:
 * - Keeps offset between cursor and window position (prevents "jumping").
 * - Provides smoother control, feels more natural when dragging.
 * - Encapsulated and reusable via apply() methods.
 *
 * @author Foxes
 */
public class DragListener extends MouseAdapter {

    private final Window targetWindow;
    private Point clickOffset; // difference between click and window's top-left

    /**
     * Constructor.
     *
     * @param targetWindow The window to be moved.
     */
    public DragListener(Window targetWindow) {
        this.targetWindow = targetWindow;
    }

    /**
     * Apply drag behavior to a component, automatically resolving its window ancestor.
     *
     * @param component The component to use as drag handle.
     */
    public void apply(Component component) {
        Window window = SwingUtilities.getWindowAncestor(component);
        if (window != null) {
            component.addMouseListener(this);
            component.addMouseMotionListener(this);
        }
    }

    /**
     * Apply drag behavior explicitly to a given window.
     *
     * @param component    The component to use as drag handle.
     * @param targetWindow The window to move.
     */
    public void apply(Component component, Window targetWindow) {
        if (targetWindow != null) {
            component.addMouseListener(this);
            component.addMouseMotionListener(this);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point windowLocation = targetWindow.getLocationOnScreen();
        Point clickLocation = e.getLocationOnScreen();
        // store offset between click and window's position
        clickOffset = new Point(clickLocation.x - windowLocation.x,
                clickLocation.y - windowLocation.y);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        clickOffset = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (clickOffset == null) return;

        Point cursor = e.getLocationOnScreen();
        int newX = cursor.x - clickOffset.x;
        int newY = cursor.y - clickOffset.y;

        targetWindow.setLocation(newX, newY);
    }
}