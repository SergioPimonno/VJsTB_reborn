package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Объект мебели, станок или монитор на плане. {@code onTop} — станок поставлен сверху на станки
 * пола как стол; {@code overrides} — параметры, переопределённые для этого объекта.
 */
public class RoomFurniture {

    private String id = UUID.randomUUID().toString();
    private RoomLibRef ref;
    /** Центр, см. */
    private double xCm = 0;
    private double yCm = 0;
    private int rotation = 0;
    private boolean onTop = false;
    private RoomOverrides overrides;

    public RoomFurniture() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RoomLibRef getRef() {
        return ref;
    }

    public void setRef(RoomLibRef ref) {
        this.ref = ref;
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

    public boolean isOnTop() {
        return onTop;
    }

    public void setOnTop(boolean onTop) {
        this.onTop = onTop;
    }

    public RoomOverrides getOverrides() {
        return overrides;
    }

    public void setOverrides(RoomOverrides overrides) {
        this.overrides = overrides;
    }

}
