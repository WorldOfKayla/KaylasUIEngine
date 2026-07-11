package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.textfield.TextField;

import java.awt.Color;
import java.awt.Graphics;

/** Look and Feel enhanced single-line engine text input. */
public class KaylasTextField extends TextField {
    /** Creates a themed single-line text field. */
    public KaylasTextField(ComponentFactory componentFactory) {
        super(componentFactory);
        KaylasComponentSupport.installTextComponent(this, "textField");
    }

    @Override
    public void setForeground(Color color) {
        super.setForeground(color);
        if (color != null) {
            setDisabledTextColor(color);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
