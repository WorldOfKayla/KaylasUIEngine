package org.takesome.kaylasEngine.gui.components.panel.listener;

/**
 * Installs one named behavior/listener on a panel created by the declarative UI system.
 *
 * <p>Implementations are registered in {@link PanelListenerRegistry} and referenced from UI
 * descriptors by name. This keeps concrete behaviors out of the panel renderer itself.</p>
 */
@FunctionalInterface
public interface PanelListenerInstaller {

    /**
     * Installs the listener or behavior represented by this strategy.
     *
     * @param context panel construction context
     */
    void install(PanelListenerContext context);
}
