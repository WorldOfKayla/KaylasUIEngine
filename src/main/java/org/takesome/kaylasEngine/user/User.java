package org.takesome.kaylasEngine.user;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor;
import org.takesome.kaylasEngine.gui.GuiBuilder;

import java.util.List;

@SuppressWarnings("unused")
public abstract class User extends ComponentsAccessor {

    protected Engine engine;

    public User(GuiBuilder guiBuilder, String panelId, List<Class<?>> componentTypes) {
        super(guiBuilder, panelId, componentTypes);
    }

    protected abstract void setUserSpace();
}
