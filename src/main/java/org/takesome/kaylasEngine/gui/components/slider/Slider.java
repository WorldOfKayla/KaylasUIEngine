package org.takesome.kaylasEngine.gui.components.slider;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.JSlider;
import java.awt.Cursor;

@SuppressWarnings("unused")
public class Slider extends JSlider {

    private SliderListener sliderListener;
    private final ComponentFactory componentFactory;

    public Slider(ComponentFactory componentFactory) {
        super(componentFactory.getComponentAttribute().getMinValue(), componentFactory.getComponentAttribute().getMaxValue());
        this.componentFactory = componentFactory;
        setOpaque(false);
    }

    public void setSliderListener(SliderListener sliderListener) {
        this.sliderListener = sliderListener;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public SliderListener getSliderListener() {
        return sliderListener;
    }

    public void fireSliderChange() {
        if (sliderListener != null) {
            sliderListener.onSliderChange(this);
        }
    }

    public ComponentFactory getComponentFactory() {
        return componentFactory;
    }
}
