package com.vjstb.ledscheme.model;

/** Точка излома связи Сетевого менеджера (см. {@link NetworkLink#getWaypoints()})
 *  — координаты канваса Сетевого менеджера, между которыми связь рисуется
 *  ломаной линией вместо одной прямой порт-в-порт (баг-репорт: "нужно также как
 *  в блоксхемах добавить возможность ломать линию"). Структурно идентична
 *  {@link EdgeWaypoint} общей схемы площадки, но своя копия — та же конвенция
 *  дублирования небольших record-подобных моделей по фиче, что и везде в этом
 *  кодовой базе (например, VehicleLoadPlacement не переиспользует поля Screen). */
public class NetworkLinkWaypoint {

    private double x;
    private double y;

    public NetworkLinkWaypoint() {
    }

    public NetworkLinkWaypoint(double x, double y) {
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

    public NetworkLinkWaypoint copy() {
        return new NetworkLinkWaypoint(x, y);
    }
}
