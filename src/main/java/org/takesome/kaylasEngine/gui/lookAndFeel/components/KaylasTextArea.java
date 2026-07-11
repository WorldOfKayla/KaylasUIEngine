package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.textArea.TextArea;

import java.awt.Color;
import java.awt.Graphics;

/** Look and Feel enhanced multi-line engine text component. */
public class KaylasTextArea extends TextArea {
    /** Creates a themed multi-line text component. */
    public KaylasTextArea(ComponentFactory componentFactory) {
        super(componentFactory);
        KaylasComponentSupport.installTextComponent(this, "textArea");
    }

    @Override
    public void setForeground(Color color) {
        super.setForeground(color);
        if (color != null) {
            setDisabledTextColor(color);
        }
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
