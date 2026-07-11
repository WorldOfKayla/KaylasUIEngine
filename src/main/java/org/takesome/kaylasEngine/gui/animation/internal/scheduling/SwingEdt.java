package org.takesome.kaylasEngine.gui.animation.internal.scheduling;

import javax.swing.SwingUtilities;

/** Internal EDT dispatch helpers shared by animation runtimes. */
public final class SwingEdt {
    private SwingEdt() {
    }

    public static void run(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
