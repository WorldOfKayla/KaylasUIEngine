package org.foxesworld.engine.gui.components.checkbox;

import org.foxesworld.engine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import static org.foxesworld.engine.utils.FontUtils.hexToColor;

public class Checkbox extends JCheckBox {

    private CheckBoxListener checkBoxListener;
    public BufferedImage defaultTX;
    public BufferedImage rolloverTX;
    public BufferedImage selectedTX;
    public BufferedImage selectedRolloverTX;
    private final ComponentFactory componentFactory;
    private final Color defaultTextColor;
    private Color hoverTextColor;

    public Checkbox(ComponentFactory componentFactory, String string) {
        super(string);
        this.componentFactory = componentFactory;
        this.defaultTextColor = hexToColor(componentFactory.getStyle().getColor());
        this.hoverTextColor = hexToColor(componentFactory.getStyle().getHoverColor());
        this.setOpaque(false);
        this.setFocusable(false);
        this.listener(this);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    public void setHoverTextColor(Color hoverTextColor) {
        this.hoverTextColor = hoverTextColor;
    }

    public void listener(final JCheckBox checkbox) {
        this.addMouseListener(new MouseListener() {

            @Override
            public void mouseReleased(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {
                boolean isSel = checkbox.isSelected();
                if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                    if (checkBoxListener != null) {
                        checkBoxListener.onClick(checkbox);
                    }
                    if (isSel) {
                        componentFactory.getEngine().emitSound("checkbox", "checkboxOff");
                        if (checkBoxListener != null) {
                            checkBoxListener.onActivate(checkbox);
                        }
                    } else {
                        componentFactory.getEngine().emitSound("checkbox", "checkboxOn");
                        if (checkBoxListener != null) {
                            checkBoxListener.onDisable(checkbox);
                        }
                    }
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setForeground(defaultTextColor); // Reset to default text color
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (checkBoxListener != null) {
                    checkBoxListener.onHover(checkbox);
                }

                if (isEnabled()) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setForeground(hoverTextColor);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {}
        });
    }

    public void toggleCheckbox() {
        boolean isSel = isSelected();
        if (isSel) {
            componentFactory.getEngine().emitSound("checkbox", "checkboxOff");
        } else {
            componentFactory.getEngine().emitSound("checkbox", "checkboxOn");
        }
    }

    public void setCheckBoxListener(CheckBoxListener checkBoxListener) {
        this.checkBoxListener = checkBoxListener;
    }

    public boolean getValue() {
        return isSelected();
    }
}
