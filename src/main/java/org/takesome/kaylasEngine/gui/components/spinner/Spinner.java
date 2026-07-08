package org.takesome.kaylasEngine.gui.components.spinner;

import javax.swing.*;

@SuppressWarnings("unused")
public class Spinner extends JSpinner {
    private SpinnerListener spinnerListener;
    public Spinner(Number initialValue, Comparable<?> minimum, Comparable<?> maximum, Number stepSize) {
        super(new SpinnerNumberModel(initialValue, minimum, maximum, stepSize));
    }

    public void init() {
        addChangeListener(e -> spinnerListener.onSpinnerChange(this));
    }

    public void setMinimumValue(Number min) {
        if (getModel() instanceof SpinnerNumberModel) {
            ((SpinnerNumberModel) getModel()).setMinimum((Comparable<?>) min);
        } else {
            throw new IllegalArgumentException("Model is not a SpinnerNumberModel");
        }
    }

    public void setMaximumValue(Number max) {
        if (getModel() instanceof SpinnerNumberModel) {
            ((SpinnerNumberModel) getModel()).setMaximum((Comparable<?>) max);
        } else {
            throw new IllegalArgumentException("Model is not a SpinnerNumberModel");
        }
    }

    public void setSpinnerListener(SpinnerListener spinnerListener) {
        this.spinnerListener = spinnerListener;
    }

    public SpinnerListener getSpinnerListener() {
        return spinnerListener;
    }
}
