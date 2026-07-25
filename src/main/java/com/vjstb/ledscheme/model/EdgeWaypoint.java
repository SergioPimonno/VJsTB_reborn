package com.vjstb.ledscheme.model;

/** Точка излома связи общей схемы (см. {@link SchemaEdge#getWaypoints()}) — абсолютные
 *  координаты холста схемы, между которыми связь рисуется прямыми отрезками вместо
 *  одной прямой линии между узлами (ортогональная/произвольная маршрутизация, см.
 *  Task #85/v1.4, по образцу профессиональных диаграмм из референсного PDF). */
public class EdgeWaypoint {

    private double x;
    private double y;

    public EdgeWaypoint() {
    }

    public EdgeWaypoint(double x, double y) {
        this.x = x;
        this.y = y;
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

    public EdgeWaypoint copy() {
        return new EdgeWaypoint(x, y);
    }
}
