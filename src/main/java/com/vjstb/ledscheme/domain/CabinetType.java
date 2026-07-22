package com.vjstb.ledscheme.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Модель LED cabinet в библиотеке. Библиотека общая для всех проектов,
 * экспортируется/импортируется в JSON.
 */
@Entity
@Table(name = "cabinet_type", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class CabinetType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Физическая ширина, мм. */
    @Column(nullable = false)
    private double widthMm;

    /** Физическая высота, мм. */
    @Column(nullable = false)
    private double heightMm;

    /** Физическая глубина (габарит), мм. Необязательно. */
    private Double depthMm;

    /** Разрешение по горизонтали, px. */
    @Column(nullable = false)
    private int resolutionWidth;

    /** Разрешение по вертикали, px. */
    @Column(nullable = false)
    private int resolutionHeight;

    /** Потребление электричества, Вт. */
    @Column(nullable = false)
    private double powerConsumptionW;

    /** Вес, кг. */
    @Column(nullable = false)
    private double weightKg;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}
