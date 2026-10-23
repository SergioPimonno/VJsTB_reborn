package com.vjstb.ledscheme.model;

/**
 * Переопределения параметров одного объекта на плане («Применить к этому объекту» в редакторе
 * по правому клику): значения {@code null} — брать из библиотеки.
 */
public class RoomOverrides {

    private String name;
    private Double widthCm;
    private Double depthCm;
    private Double heightCm;
    private Double weightKg;
    private Double powerW;

    public RoomOverrides() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(Double widthCm) {
        this.widthCm = widthCm;
    }

    public Double getDepthCm() {
        return depthCm;
    }

    public void setDepthCm(Double depthCm) {
        this.depthCm = depthCm;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getPowerW() {
        return powerW;
    }

    public void setPowerW(Double powerW) {
        this.powerW = powerW;
    }

}
