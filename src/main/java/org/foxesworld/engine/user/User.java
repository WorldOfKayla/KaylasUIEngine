package org.foxesworld.engine.user;

import org.foxesworld.engine.Engine;
import org.foxesworld.engine.gui.componentAccessor.ComponentsAccessor;
import org.foxesworld.engine.gui.GuiBuilder;

import java.util.List;

@SuppressWarnings("unused")
public abstract class User extends ComponentsAccessor {

    protected Engine engine;

    public User(GuiBuilder guiBuilder, String panelId, List<Class<?>> componentTypes) {
        super(guiBuilder, panelId, componentTypes);
    }

    protected abstract void setUserSpace();
}
