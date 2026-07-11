package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.button.Button;

import javax.swing.ImageIcon;
import java.awt.Graphics;

/** Look and Feel enhanced engine button with keyboard focus feedback. */
public class KaylasButton extends Button {
    /** Creates a themed text button. */
    public KaylasButton(ComponentFactory componentFactory, String text) {
        super(componentFactory, text);
        initialize();
    }

    /** Creates a themed button with an icon and text. */
    public KaylasButton(ComponentFactory componentFactory, ImageIcon icon, String text) {
        super(componentFactory, icon, text);
        initialize();
    }

    private void initialize() {
        KaylasComponentSupport.install(this, "button", true);
        setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        KaylasComponentSupport.paintFocusRing(this, graphics, 2);
    }
}
