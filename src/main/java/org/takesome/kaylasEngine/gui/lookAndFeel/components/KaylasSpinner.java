package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.spinner.Spinner;

import java.awt.Graphics;

/** Look and Feel enhanced engine spinner and editor. */
public class KaylasSpinner extends Spinner {
    /** Creates a themed numeric spinner. */
    public KaylasSpinner(Number initialValue,
                         Comparable<?> minimum,
                         Comparable<?> maximum,
                         Number stepSize) {
        super(initialValue, minimum, maximum, stepSize);
        KaylasComponentSupport.install(this, "spinner", true);
        KaylasComponentSupport.configureSpinnerEditor(this);
        addPropertyChangeListener("editor", event ->
                KaylasComponentSupport.configureSpinnerEditor(this));
    }

    @Override
    protected void paintBorder(Graphics graphics) {
        super.paintBorder(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
