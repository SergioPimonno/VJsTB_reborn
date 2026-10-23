package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Башня на плане: стопка слоёв (кофры и/или стопки без кофра) с положением и поворотом.
 * {@code lay} — явное положение относительно стола или станка под центром башни ({@code null} —
 * автоматически, см. {@code service.room.RoomGeometry#layMode}).
 */
public class RoomTower {

    /** Положение башни относительно стола/станка под ней. */
    public enum Lay {
        /** Стоит сверху. */
        ON,
        /** Стоит под столом/станком. */
        UNDER
    }

    private String id = UUID.randomUUID().toString();
    /** Буква на плане (A, B…). */
    private String tag = "";
    private String name = "";
    /** Центр, см. */
    private double xCm = 0;
    private double yCm = 0;
    /** 0, 90, 180, 270. */
    private int rotation = 0;
    private Lay lay;
    /** Слои снизу вверх. */
    private List<RoomSegment> segments = new ArrayList<>();

    public RoomTower() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getXCm() {
        return xCm;
    }

    public void setXCm(double xCm) {
        this.xCm = xCm;
    }

    public double getYCm() {
        return yCm;
    }

    public void setYCm(double yCm) {
        this.yCm = yCm;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public Lay getLay() {
        return lay;
    }

    public void setLay(Lay lay) {
        this.lay = lay;
    }

    public List<RoomSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<RoomSegment> segments) {
        this.segments = segments != null ? segments : new ArrayList<>();
    }

}
