package org.takesome.kaylasEngine.gui.components.slider;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.JSlider;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine slider with optional non-linear, discrete semantic values.
 *
 * <p>The Swing model continues to store the real semantic value. When an allowed-value list is
 * installed, assignments are snapped to that list while the UI delegate is free to place those
 * values at equal visual intervals.</p>
 */
@SuppressWarnings("unused")
public class Slider extends JSlider {

    private SliderListener sliderListener;
    private final ComponentFactory componentFactory;
    private List<Integer> allowedValues = List.of();

    public Slider(ComponentFactory componentFactory) {
        super(componentFactory.getComponentAttribute().getMinValue(),
                componentFactory.getComponentAttribute().getMaxValue());
        this.componentFactory = componentFactory;
        setOpaque(false);
    }

    /**
     * Installs a strictly increasing list of selectable semantic values.
     *
     * <p>An empty list restores ordinary linear slider behaviour.</p>
     */
    public void setAllowedValues(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            allowedValues = List.of();
            return;
        }

        List<Integer> normalized = new ArrayList<>(values.size());
        Integer previous = null;
        for (Integer value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Slider allowed values must not contain null");
            }
            if (previous != null && value <= previous) {
                throw new IllegalArgumentException(
                        "Slider allowed values must be strictly increasing: " + values
                );
            }
            normalized.add(value);
            previous = value;
        }

        allowedValues = List.copyOf(normalized);
        setValue(super.getValue());
    }

    public List<Integer> getAllowedValues() {
        return allowedValues;
    }

    public boolean hasAllowedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    @Override
    public void setValue(int value) {
        List<Integer> values = allowedValues;
        if (values == null || values.isEmpty()) {
            super.setValue(value);
            return;
        }

        int current = super.getValue();
        int currentIndex = values.indexOf(current);

        // Swing keyboard actions move by one model unit. On a non-linear scale, interpret that
        // one-unit request as previous/next semantic value instead of snapping back in place.
        if (currentIndex >= 0 && Math.abs((long) value - current) == 1L) {
            int direction = Integer.compare(value, current);
            int targetIndex = Math.max(0, Math.min(values.size() - 1, currentIndex + direction));
            super.setValue(values.get(targetIndex));
            return;
        }

        super.setValue(nearestAllowedValue(values, value));
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

    private static int nearestAllowedValue(List<Integer> values, int requested) {
        int nearest = values.get(0);
        long nearestDistance = Math.abs((long) requested - nearest);
        for (int value : values) {
            long distance = Math.abs((long) requested - value);
            if (distance < nearestDistance) {
                nearest = value;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
