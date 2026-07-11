package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.slider.Slider;

import java.awt.Graphics;

/** Look and Feel enhanced engine slider with visible keyboard focus. */
public class KaylasSlider extends Slider {
    /** Creates a themed engine slider. */
    public KaylasSlider(ComponentFactory componentFactory) {
        super(componentFactory);
        KaylasComponentSupport.install(this, "slider", true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
