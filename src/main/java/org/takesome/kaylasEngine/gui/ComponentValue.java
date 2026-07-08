package org.takesome.kaylasEngine.gui;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactoryListener;

public abstract class ComponentValue implements ComponentFactoryListener {

    protected Engine engine;
    public ComponentValue(Engine engine){
        this.engine = engine;
    }
    protected abstract void setInitialData(ComponentAttributes componentAttributes);
}
