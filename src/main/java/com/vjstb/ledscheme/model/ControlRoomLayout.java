package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Раскладка пультовой — одна сохранённая планировка площадки FOH (башни, мебель, станки,
 * мониторы, мелочь и трассы). У каждой сцены свой СПИСОК раскладок ({@link
 * Scene#getControlRoomLayouts()}), их может быть несколько (варианты, разные площадки) — решение
 * пользователя 2026-10-23, см. {@code docs/control-room-planner/PLAN.md}. Все размеры — в
 * сантиметрах. Сериализуется обычной Jackson-сериализацией, как {@link NetworkManagerPlan}.
 */
public class ControlRoomLayout {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private int orderIndex = 0;
    /** Ширина площадки, см. */
    private double roomWidthCm = 700;
    /** Глубина площадки, см. */
    private double roomDepthCm = 450;
    private List<RoomTower> towers = new ArrayList<>();
    private List<RoomLooseItem> loose = new ArrayList<>();
    private List<RoomFurniture> furniture = new ArrayList<>();
    private List<RoomRun> runs = new ArrayList<>();

    public ControlRoomLayout() {
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public double getRoomWidthCm() {
        return roomWidthCm;
    }

    public void setRoomWidthCm(double roomWidthCm) {
        this.roomWidthCm = roomWidthCm;
    }

    public double getRoomDepthCm() {
        return roomDepthCm;
    }

    public void setRoomDepthCm(double roomDepthCm) {
        this.roomDepthCm = roomDepthCm;
    }

    public List<RoomTower> getTowers() {
        return towers;
    }

    public void setTowers(List<RoomTower> towers) {
        this.towers = towers != null ? towers : new ArrayList<>();
    }

    public List<RoomLooseItem> getLoose() {
        return loose;
    }

    public void setLoose(List<RoomLooseItem> loose) {
        this.loose = loose != null ? loose : new ArrayList<>();
    }

    public List<RoomFurniture> getFurniture() {
        return furniture;
    }

    public void setFurniture(List<RoomFurniture> furniture) {
        this.furniture = furniture != null ? furniture : new ArrayList<>();
    }

    public List<RoomRun> getRuns() {
        return runs;
    }

    public void setRuns(List<RoomRun> runs) {
        this.runs = runs != null ? runs : new ArrayList<>();
    }

    public ControlRoomLayout(String name) {
        this.name = name;
    }

}
