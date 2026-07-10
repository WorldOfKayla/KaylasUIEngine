package org.takesome.kaylasEngine.gui.components.panel.listener;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.frame.FrameConstructor;
import org.takesome.kaylasEngine.gui.components.panel.PanelAttributes;

import javax.swing.JPanel;
import java.awt.Window;
import java.util.Objects;

/** Construction context supplied to a registered panel-listener installer. */
public record PanelListenerContext(
        JPanel panel,
        PanelAttributes attributes,
        FrameConstructor frameConstructor
) {
    public PanelListenerContext {
        panel = Objects.requireNonNull(panel, "panel");
        attributes = attributes == null ? new PanelAttributes() : attributes;
        frameConstructor = Objects.requireNonNull(frameConstructor, "frameConstructor");
    }

    /** Returns the owning engine. */
    public Engine engine() {
        return frameConstructor.getAppFrame();
    }

    /** Returns the top-level application window. */
    public Window window() {
        return frameConstructor;
    }
}
