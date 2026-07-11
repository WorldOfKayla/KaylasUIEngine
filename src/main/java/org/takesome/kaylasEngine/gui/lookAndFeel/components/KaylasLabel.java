package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.label.Label;

/** Look and Feel metadata wrapper for the engine label. */
public class KaylasLabel extends Label {
    /** Creates a themed engine label. */
    public KaylasLabel(ComponentFactory componentFactory) {
        super(componentFactory);
        KaylasComponentSupport.install(this, "label", false);
    }
}
