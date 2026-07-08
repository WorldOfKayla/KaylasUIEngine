package org.takesome.kaylasEngine.gui.components.slider;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("unused")
public class Slider extends JSlider {

    private SliderListener sliderListener;
    private final ComponentFactory componentFactory;

    public Slider(ComponentFactory componentFactory){
        super(componentFactory.getComponentAttribute().getMinValue(), componentFactory.getComponentAttribute().getMaxValue());
        this.componentFactory = componentFactory;
    }

    public void setSliderListener(SliderListener sliderListener) {
        this.sliderListener = sliderListener;
        this.addChangeListener(e -> sliderListener.onSliderChange(this));
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
