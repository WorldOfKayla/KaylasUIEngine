package org.takesome.kaylasEngine.utils.animation;

import java.awt.geom.Point2D;

public class BezierCurve {
    private final Point2D[] controlPoints;

    public BezierCurve(Point2D... controlPoints) {
        this.controlPoints = controlPoints;
    }

    public Point2D calculatePoint(float t) {
        if (controlPoints.length == 0) return null;

        Point2D[] tempPoints = new Point2D[controlPoints.length];
        System.arraycopy(controlPoints, 0, tempPoints, 0, controlPoints.length);

        for (int i = controlPoints.length - 1; i > 0; i--) {
            for (int j = 0; j < i; j++) {
                double x = (1 - t) * tempPoints[j].getX() + t * tempPoints[j + 1].getX();
                double y = (1 - t) * tempPoints[j].getY() + t * tempPoints[j + 1].getY();
                tempPoints[j] = new Point2D.Double(x, y);
            }
        }

        return tempPoints[0];
    }
}