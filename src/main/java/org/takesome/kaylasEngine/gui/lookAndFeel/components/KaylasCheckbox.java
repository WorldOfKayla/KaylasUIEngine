package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.checkbox.Checkbox;

import java.awt.Graphics;

/** Look and Feel enhanced engine checkbox with keyboard accessibility. */
public class KaylasCheckbox extends Checkbox {
    /** Creates a themed checkbox. */
    public KaylasCheckbox(ComponentFactory componentFactory, String text) {
        super(componentFactory, text);
        KaylasComponentSupport.install(this, "checkbox", true);
        setRolloverEnabled(true);
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
