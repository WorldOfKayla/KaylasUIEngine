package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.passfield.PassField;

import java.awt.Color;
import java.awt.Graphics;

/** Look and Feel enhanced engine password field. */
public class KaylasPassField extends PassField {
    /** Creates a themed password field. */
    public KaylasPassField(ComponentFactory componentFactory, String placeholder) {
        super(componentFactory, placeholder);
        KaylasComponentSupport.installTextComponent(this, "passwordField");
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
