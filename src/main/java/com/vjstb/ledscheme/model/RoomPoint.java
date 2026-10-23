package com.vjstb.ledscheme.model;

/**
 * Точка трассы на плане, сантиметры от левого верхнего угла площадки.
 */
public class RoomPoint {

    private double x = 0;
    private double y = 0;

    public RoomPoint() {
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public RoomPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

}
