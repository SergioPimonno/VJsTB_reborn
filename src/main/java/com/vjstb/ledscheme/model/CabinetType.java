package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Тип LED cabinet в библиотеке. Библиотека общая для всех проектов,
 * экспортируется/импортируется в JSON.
 */
public class CabinetType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    /** Физическая ширина, мм. */
    private double widthMm = 500;
    /** Физическая высота, мм. */
    private double heightMm = 500;
    /** Физическая глубина (габарит), мм. Необязательно. */
    private Double depthMm;
    /** Разрешение по горизонтали, px. */
    private int resolutionWidth = 128;
    /** Разрешение по вертикали, px. */
    private int resolutionHeight = 128;
    /** Потребление электричества, Вт. */
    private double powerConsumptionW = 150;
    /** Вес, кг. */
    private double weightKg = 12;

    public CabinetType() {
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

    public double getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(double widthMm) {
        this.widthMm = widthMm;
    }

    public double getHeightMm() {
        return heightMm;
    }

    public void setHeightMm(double heightMm) {
        this.heightMm = heightMm;
    }

    public Double getDepthMm() {
        return depthMm;
    }

    public void setDepthMm(Double depthMm) {
        this.depthMm = depthMm;
    }

    public int getResolutionWidth() {
        return resolutionWidth;
    }

    public void setResolutionWidth(int resolutionWidth) {
        this.resolutionWidth = resolutionWidth;
    }

    public int getResolutionHeight() {
        return resolutionHeight;
    }

    public void setResolutionHeight(int resolutionHeight) {
        this.resolutionHeight = resolutionHeight;
    }

    public double getPowerConsumptionW() {
        return powerConsumptionW;
    }

    public void setPowerConsumptionW(double powerConsumptionW) {
        this.powerConsumptionW = powerConsumptionW;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public CabinetType copy() {
        CabinetType c = new CabinetType();
        c.id = id;
        c.name = name;
        c.widthMm = widthMm;
        c.heightMm = heightMm;
        c.depthMm = depthMm;
        c.resolutionWidth = resolutionWidth;
        c.resolutionHeight = resolutionHeight;
        c.powerConsumptionW = powerConsumptionW;
        c.weightKg = weightKg;
        return c;
    }
}
