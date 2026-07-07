package org.foxesworld.engine.gui.components;

import java.awt.Rectangle;

public class Bounds {
    private int x;
    private int y;
    private Size size = new Size();

    public Bounds() {
    }

    public Bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.size = new Size(width, height);
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public Size getSize() {
        if (size == null) {
            size = new Size();
        }
        return size;
    }

    public void setSize(Size size) {
        this.size = size == null ? new Size() : size;
    }

    public Rectangle getBounds() {
        Size safeSize = getSize();
        return new Rectangle(this.x, this.y, safeSize.width, safeSize.height);
    }

    public static class Size {
        private int width;
        private int height;

        public Size() {
        }

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
}
