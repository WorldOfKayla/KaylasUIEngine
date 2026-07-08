package org.takesome.kaylasEngine.gui;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.command.DynamicCommandRegistry;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor;
import org.takesome.kaylasEngine.server.ServerAttributes;

import java.awt.event.ActionEvent;
import java.util.List;

public abstract class ActionHandler extends ComponentsAccessor implements DynamicCommandRegistry {
    protected Engine engine;
    protected ServerAttributes currentServer;

    public ActionHandler(GuiBuilder guiBuilder, String panelId, List<Class<?>> componentTypes) {
        super(guiBuilder, panelId, componentTypes);
        this.engine = guiBuilder.getEngine();
        Engine.LOGGER.info("ActionHandler created with {} panel and listens {}", panelId, componentTypes);
    }

    @SuppressWarnings("unused")
    public abstract void handleAction(ActionEvent e);
    public Engine getEngine() {
        return engine;
    }
    public ServerAttributes getCurrentServer() {
        return currentServer;
    }
}
