package org.takesome.kaylasEngine.gui.components.textArea;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TextArea extends JTextArea {

    public TextArea(ComponentFactory componentFactory) {
        if(componentFactory.getComponentAttribute().getLocaleKey() != null) {
            setText(componentFactory.getEngine().getLANG().getString(componentFactory.getComponentAttribute().getLocaleKey()));
        }
        setOpaque(componentFactory.getStyle().isOpaque());
        setPreferredSize(new Dimension((int) componentFactory.getBounds().getWidth(), (int) componentFactory.getBounds().getHeight()));
        if(componentFactory.getComponentAttribute().getBorder() != null) {
            List borders = new List();
            for (String val : componentFactory.getComponentAttribute().getBorder().split(",")) {
                borders.add(val);
            }
            setBorder(new EmptyBorder(
                    Integer.parseInt(borders.getItem(0)),
                    Integer.parseInt(borders.getItem(1)),
                    Integer.parseInt(borders.getItem(2)),
                    Integer.parseInt(borders.getItem(3))));
        }
        if (!componentFactory.getComponentAttribute().isEditable()) {
            setEditable(false);
            disableTextSelection();
        }

        if(componentFactory.getComponentAttribute().isLineWrap()){
            setLineWrap(true);
            setWrapStyleWord(true);
        }

    }


    /**
     * Applies one semantic text color to both normal and disabled Swing rendering states.
     *
     * <p>{@link JTextArea} uses {@code disabledTextColor} instead of {@code foreground} when the
     * component is disabled. Keeping both values synchronized ensures that declarative
     * {@code color} attributes remain visible for read-only disabled text areas.</p>
     *
     * @param color resolved text color.
     */
    public void setTextColor(Color color) {
        applyTextColor(this, color);
    }

    static void applyTextColor(JTextArea textArea, Color color) {
        textArea.setForeground(color);
        textArea.setDisabledTextColor(color);
    }

    private void disableTextSelection() {
        setFocusable(false);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                getCaret().setVisible(false);
            }
        });

        getInputMap().put(KeyStroke.getKeyStroke("COPY"), "none");
        getInputMap().put(KeyStroke.getKeyStroke("CUT"), "none");
        getInputMap().put(KeyStroke.getKeyStroke("PASTE"), "none");
        getInputMap().put(KeyStroke.getKeyStroke("SELECT_ALL"), "none");
    }


    public void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}
