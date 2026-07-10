package org.takesome.kaylasEngine.gui.components.spinner;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

@SuppressWarnings("unused")
public class Spinner extends JSpinner {
    private SpinnerListener spinnerListener;
    private boolean listenerInstalled;

    public Spinner(Number initialValue, Comparable<?> minimum, Comparable<?> maximum, Number stepSize) {
        super(new SpinnerNumberModel(initialValue, minimum, maximum, stepSize));
    }

    public void init() {
        if (listenerInstalled) {
            return;
        }
        listenerInstalled = true;
        addChangeListener(e -> fireSpinnerChange());
    }

    public void fireSpinnerChange() {
        if (spinnerListener != null) {
            spinnerListener.onSpinnerChange(this);
        }
    }

    public void setMinimumValue(Number min) {
        if (getModel() instanceof SpinnerNumberModel model) {
            model.setMinimum((Comparable<?>) min);
        } else {
            throw new IllegalArgumentException("Model is not a SpinnerNumberModel");
        }
    }

    public void setMaximumValue(Number max) {
        if (getModel() instanceof SpinnerNumberModel model) {
            model.setMaximum((Comparable<?>) max);
        } else {
            throw new IllegalArgumentException("Model is not a SpinnerNumberModel");
        }
    }

    public void setSpinnerListener(SpinnerListener spinnerListener) {
        this.spinnerListener = spinnerListener;
        init();
    }

    public SpinnerListener getSpinnerListener() {
        return spinnerListener;
    }
}
