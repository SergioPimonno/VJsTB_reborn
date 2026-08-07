package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Канвас — виртуальный выходной кадр для компоновки контента (как «Advanced Output»
 * в Resolume или выходной канвас медиасервера): собственное разрешение (настраивается
 * как разрешение сигнала — ширина/высота в пикселях), внутри размещаются экраны сцены
 * на своих позициях. Один и тот же канвас используется и для генерации тестовой маски
 * канваса целиком, и как основа для пресетов под медиасерверы/Resolume.
 */
public class ContentCanvas {

    private String id = UUID.randomUUID().toString();
    private String name = "Канвас";
    private int widthPx = 1920;
    private int heightPx = 1080;
    private List<CanvasPlacement> placements = new ArrayList<>();
    /** Общие для ВСЕХ гридов этого канваса настройки подписи имени (см.
     *  PixelGridRenderer.GridRenderOptions) — в отличие от фона/чекбоксов, которые
     *  у каждого CanvasPlacement свои. */
    private boolean largeGridNames;
    /** null — белый (прежнее поведение по умолчанию). */
    private Integer textColorRgb;
    private boolean dropShadow;

    public ContentCanvas() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(int widthPx) {
        this.widthPx = widthPx;
    }

    public int getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(int heightPx) {
        this.heightPx = heightPx;
    }

    public List<CanvasPlacement> getPlacements() {
        return placements;
    }

    public void setPlacements(List<CanvasPlacement> placements) {
        this.placements = placements;
    }

    public boolean isLargeGridNames() {
        return largeGridNames;
    }

    public void setLargeGridNames(boolean largeGridNames) {
        this.largeGridNames = largeGridNames;
    }

    public Integer getTextColorRgb() {
        return textColorRgb;
    }

    public void setTextColorRgb(Integer textColorRgb) {
        this.textColorRgb = textColorRgb;
    }

    public boolean isDropShadow() {
        return dropShadow;
    }

    public void setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
    }

    public ContentCanvas copy() {
        ContentCanvas c = new ContentCanvas();
        c.id = id;
        c.name = name;
        c.widthPx = widthPx;
        c.heightPx = heightPx;
        c.placements = new ArrayList<>();
        for (CanvasPlacement p : placements) {
            c.placements.add(p.copy());
        }
        c.largeGridNames = largeGridNames;
        c.textColorRgb = textColorRgb;
        c.dropShadow = dropShadow;
        return c;
    }
}
