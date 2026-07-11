package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButton;

import java.awt.Graphics;

/** Look and Feel enhanced multi-state engine button. */
public class KaylasMultiButton extends MultiButton {
    /** Creates a themed multi-state button. */
    public KaylasMultiButton(ComponentFactory componentFactory) {
        super(componentFactory);
        KaylasComponentSupport.install(this, "multiButton", true);
        setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
