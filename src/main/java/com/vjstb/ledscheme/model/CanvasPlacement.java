package com.vjstb.ledscheme.model;

import java.util.UUID;

/** Экран, размещённый внутри канваса (компоновка контента) — позиция в пикселях канваса. */
public class CanvasPlacement {

    private String id = UUID.randomUUID().toString();
    private String screenId;
    private int x;
    private int y;

    public CanvasPlacement() {
    }

    public CanvasPlacement(String screenId, int x, int y) {
        this.screenId = screenId;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
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

    public CanvasPlacement copy() {
        CanvasPlacement p = new CanvasPlacement();
        p.id = id;
        p.screenId = screenId;
        p.x = x;
        p.y = y;
        return p;
    }
}
