package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.components.Bounds;

import java.awt.*;

@SuppressWarnings("unused")
public class LoadManagerAttributes {
    private String spritePath, bgPath, blurColor, titleColor, descColor;
    private int rows, cols,delay,animSpeed;
    private Bounds bounds;
    public String getSpritePath() {
        return spritePath;
    }
    public String getBgPath() {
        return bgPath;
    }
    public String getBlurColor() {
        return blurColor;
    }
    public int getRows() {
        return rows;
    }
    public int getCols() {
        return cols;
    }
    public int getDelay() {
        return delay;
    }
    public int getAnimSpeed() {
        return animSpeed;
    }
    public Rectangle getBounds() {
        return new Rectangle(bounds.getX(), bounds.getY(), bounds.getSize().getWidth(), bounds.getSize().getHeight());
    }
    public String getTitleColor() {
        return titleColor;
    }
    public String getDescColor() {
        return descColor;
    }
}
