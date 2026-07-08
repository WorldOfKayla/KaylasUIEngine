package org.takesome.kaylasEngine.gui.components.panel;

import org.takesome.kaylasEngine.gui.components.Bounds;

@SuppressWarnings("unused")
public class PanelAttributes {
    private boolean opaque = false, visible,focusable, doubleBuffered = true;
    private int cornerRadius, zIndex = 0;
    private String border = "", listener = "",background = "",backgroundImage, layout;
    private Bounds bounds;

    public boolean isOpaque() {
        return opaque;
    }
    public boolean isVisible() {
        return visible;
    }
    public boolean isFocusable() {
        return focusable;
    }
    public int getCornerRadius() {
        return cornerRadius;
    }
    public String getBorder() {
        return border;
    }
    public String getListener() {
        return listener;
    }
    public String getLayout() {
        return layout;
    }
    public boolean isDoubleBuffered() {
        return doubleBuffered;
    }
    public String getBackground() {
        return background;
    }
    public String getBackgroundImage() {
        return backgroundImage;
    }
    public Bounds getBounds() {
        return bounds;
    }

    public int getzIndex() {
        return zIndex;
    }

    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }
}
