package org.takesome.kaylasEngine.gui.lookAndFeel.components;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/** Look and Feel enhanced engine combobox with keyboard selection. */
public class KaylasCombobox extends Combobox {
    /** Creates a themed combobox with initial values. */
    public KaylasCombobox(ComponentFactory componentFactory, String[] values, int initialY) {
        super(componentFactory, values, initialY);
        initializeKeyboardAccess();
    }

    /** Creates an initially empty themed combobox. */
    public KaylasCombobox(ComponentFactory componentFactory, int initialY) {
        super(componentFactory, initialY);
        initializeKeyboardAccess();
    }

    private void initializeKeyboardAccess() {
        KaylasComponentSupport.install(this, "combobox", true);
        bind(KeyEvent.VK_UP, "kaylas.selectPrevious", -1);
        bind(KeyEvent.VK_DOWN, "kaylas.selectNext", 1);
        getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
                "kaylas.selectFirst"
        );
        getActionMap().put("kaylas.selectFirst", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (getValues().length > 0) {
                    setSelectedIndex(0);
                }
            }
        });
        getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
                "kaylas.selectLast"
        );
        getActionMap().put("kaylas.selectLast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (getValues().length > 0) {
                    setSelectedIndex(getValues().length - 1);
                }
            }
        });
    }

    private void bind(int keyCode, String actionKey, int delta) {
        getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(keyCode, 0),
                actionKey
        );
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int count = getValues().length;
                if (count == 0) {
                    return;
                }
                int next = Math.max(0, Math.min(count - 1, getSelectedIndex() + delta));
                setSelectedIndex(next);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D clippedGraphics = (Graphics2D) graphics.create();
        try {
            clippedGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            int width = getWidth();
            int height = getHeight();
            if (width > 0 && height > 0) {
                int arc = Math.max(2, KaylasComponentSupport.theme().arc() * 2);
                clippedGraphics.clip(new RoundRectangle2D.Float(
                        0,
                        0,
                        width,
                        height,
                        arc,
                        arc
                ));
            }
            super.paintComponent(clippedGraphics);
        } finally {
            clippedGraphics.dispose();
        }
        KaylasComponentSupport.paintFocusRing(this, graphics, 1);
    }
}
