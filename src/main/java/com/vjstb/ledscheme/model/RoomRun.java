package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Трасса кабеля на плане пультовой (закладная оптика, SDI, сеть, силовая…): ломаная от ввода у
 * сцены до конца. Название выводится на плане вдоль линии как обозначение трассы (запрос
 * пользователя 2026-10-23). Мелочь прилипает к трассе и хранит расстояние от первой точки
 * ({@link RoomLooseItem#getSCm()}).
 */
public class RoomRun {

    private String id = UUID.randomUUID().toString();
    /** Тип: optic, sdi, net, pwr или misc — определяет цвет. */
    private String kind = "misc";
    private String name = "";
    /** Вершины ломаной, от ввода к концу. */
    private List<RoomPoint> points = new ArrayList<>();

    public RoomRun() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<RoomPoint> getPoints() {
        return points;
    }

    public void setPoints(List<RoomPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
    }

}
