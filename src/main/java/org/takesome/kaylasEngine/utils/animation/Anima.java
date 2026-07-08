package org.takesome.kaylasEngine.utils.animation;

import java.awt.geom.Point2D;

public class Anima {

    public Anima(int x, int  y, int z, float opacity){
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.opacity = opacity;
    }
    private final int posX, posY, posZ;
    private final float opacity;
    private Point2D[] controlPoints;

    private void calculateControlPoints(Anima[] animaArr) {
        this.controlPoints = new Point2D[]{
                new Point2D.Double(animaArr[0].getPosX(), animaArr[0].getPosY()),
                new Point2D.Double(animaArr[0].getPosX(), (animaArr[0].getPosY() + animaArr[1].getPosY()) / 2),
                new Point2D.Double(animaArr[1].getPosX(), (animaArr[0].getPosY() + animaArr[1].getPosY()) / 2),
                new Point2D.Double(animaArr[1].getPosX(), animaArr[1].getPosY())
        };
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public float getOpacity() {
        return opacity;
    }

    public Point2D[] getControlPoints() {
        return controlPoints;
    }
}
