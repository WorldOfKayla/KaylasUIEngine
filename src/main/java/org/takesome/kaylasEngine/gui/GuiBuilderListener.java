package org.takesome.kaylasEngine.gui;

import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Listener interface for {@link org.takesome.kaylasEngine.gui.GuiBuilder} events.
 *
 * <p>
 * Implementations receive notifications about various stages of GUI construction
 * such as individual panel creation, completion of all panels, and separately loaded panels.
 * Callbacks may be invoked on the Swing Event Dispatch Thread (EDT) depending on how the builder
 * was executed (synchronous vs asynchronous).
 * </p>
 */
public interface GuiBuilderListener {

    /**
     * Called when the GUI (or a major part of it) has been built.
     *
     * <p>
     * This is a general notification — use {@link #onPanelsBuilt()} for the moment when all panels
     * have been finished. Implementations should be prepared for this method to be invoked in different
     * threading contexts.
     * </p>
     */
    void onGuiBuilt();

    /**
     * Called when a specific panel group has been constructed.
     *
     * @param panels         map of panel groups (group name -> {@link OptionGroups}).
     * @param componentGroup the name of the component group that was built.
     * @param parentPanel    the parent container into which the group was inserted.
     */
    void onPanelBuild(Map<String, OptionGroups> panels, String componentGroup, Container parentPanel);

    /**
     * Called once after all panels have been built.
     *
     * <p>
     * This callback is intended to signal finalization of panel construction and is a good place
     * to trigger post-build initialization (event wiring, data population, layout validation, etc.).
     * </p>
     */
    void onPanelsBuilt();

    /**
     * Called when an additional panel (one that was loaded separately via {@code readFrom} / load panel)
     * has been built and attached.
     *
     * @param panel the additional {@link JPanel} that was constructed and attached.
     */
    void onAdditionalPanelBuild(JPanel panel);
}
