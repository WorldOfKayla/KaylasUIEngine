package org.takesome.kaylasEngine.gui.components.compositeSlider;

import java.util.List;

public class SliderRange {
    private final int minValue;
    private final int maxValue;
    private final int initialValue;
    private final List<Integer> values;

    public SliderRange(int minValue, int maxValue, int initialValue, List<Integer> values) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.initialValue = initialValue;
        this.values = values;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getInitialValue() {
        return initialValue;
    }

    public List<Integer> getValues() {
        return values;
    }
}
