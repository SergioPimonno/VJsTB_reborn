package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Один физический LED cabinet в сетке экрана.
 * phase: 0 = не назначена, 1/2/3 = фаза питания L1/L2/L3.
 */
public class CabinetInstance {

    private String id = UUID.randomUUID().toString();
    private int rowIndex;
    private int colIndex;
    /** Ячейка исключена из экрана: не считается, не рисуется, не участвует в цепочках.
     *  Так задаётся неправильная (не прямоугольная) форма экрана — маской ячеек. */
    private boolean hidden;
    private int phase;
    /** Тип кабинета для ЭТОЙ ячейки, если отличается от типа экрана по умолчанию (null = тип экрана). */
    private String cabinetTypeId;
    /** Форма ЭТОЙ ячейки, если отличается от формы эффективного типа кабинета (null = форма типа). */
    private CabinetShape shapeOverride;
    /** Угол непрямоугольной формы ЭТОЙ ячейки, если отличается от угла эффективного
     *  типа кабинета (null = угол типа, см. CabinetType.getRotationDeg) — Task #92/v1.5:
     *  угол правится не только на уровне типа в библиотеке, но и точечно по ячейке
     *  через радиальное меню (0°/90°/180°/270°/"другое"), не требуя заводить отдельный
     *  тип кабинета только ради другого угла. */
    private Integer rotationOverride;
    /** Свободное смещение ЭТОЙ ячейки от её расчётной сеточной позиции (мм) — Task #7/v1.6:
     *  разрешает разорвать сетку и сдвинуть кабинет физически (например, подвинуть на
     *  реальном месте монтажа), не трогая rowIndex/colIndex (те остаются «логическим»
     *  местом ячейки в сетке — нумерация, поиск соседей для привязки, resize сетки —
     *  и не меняются перетаскиванием). 0,0 (по умолчанию) — сетка без изменений, как раньше. */
    private double offsetXMm;
    private double offsetYMm;

    public CabinetInstance() {
    }

    public CabinetInstance(int rowIndex, int colIndex) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** 0-based позиция в сетке — используется для расчёта координат, поиска и хранится как есть в JSON. */
    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    /** Номер строки/столбца для показа пользователю (с 1, а не с 0). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getDisplayRow() {
        return rowIndex + 1;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getDisplayCol() {
        return colIndex + 1;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public String getCabinetTypeId() {
        return cabinetTypeId;
    }

    public void setCabinetTypeId(String cabinetTypeId) {
        this.cabinetTypeId = cabinetTypeId;
    }

    public CabinetShape getShapeOverride() {
        return shapeOverride;
    }

    public void setShapeOverride(CabinetShape shapeOverride) {
        this.shapeOverride = shapeOverride;
    }

    public Integer getRotationOverride() {
        return rotationOverride;
    }

    public void setRotationOverride(Integer rotationOverride) {
        this.rotationOverride = rotationOverride;
    }

    public double getOffsetXMm() {
        return offsetXMm;
    }

    public void setOffsetXMm(double offsetXMm) {
        this.offsetXMm = offsetXMm;
    }

    public double getOffsetYMm() {
        return offsetYMm;
    }

    public void setOffsetYMm(double offsetYMm) {
        this.offsetYMm = offsetYMm;
    }

    public CabinetInstance copy() {
        CabinetInstance c = new CabinetInstance();
        c.id = id;
        c.rowIndex = rowIndex;
        c.colIndex = colIndex;
        c.hidden = hidden;
        c.phase = phase;
        c.cabinetTypeId = cabinetTypeId;
        c.shapeOverride = shapeOverride;
        c.rotationOverride = rotationOverride;
        c.offsetXMm = offsetXMm;
        c.offsetYMm = offsetYMm;
        return c;
    }
}
