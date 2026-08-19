package com.vjstb.ledscheme.model;

/** Один размещённый кофр в {@link VehicleLoadSection} — персистентная форма
 *  {@code ui.VehicleLoadCanvasPanel.Placement}. Ссылается на тип кофра ПО ID
 *  ({@code caseTypeId}), не embed'ит объект — тот же приём, что и у
 *  {@code Screen#cabinetTypeId}/{@code riggingHoistTypeId} (см.
 *  {@code Workspace#caseTypeById}), чтобы не плодить устаревающие копии
 *  библиотечной записи внутри сохранённого проекта. */
public class VehicleLoadPlacement {

    private String caseTypeId;
    private double xMm;
    private double yMm;
    private boolean rotated;
    private int stackCount = 1;
    private String note = "";

    public VehicleLoadPlacement() {
    }

    public String getCaseTypeId() {
        return caseTypeId;
    }

    public void setCaseTypeId(String caseTypeId) {
        this.caseTypeId = caseTypeId;
    }

    public double getXMm() {
        return xMm;
    }

    public void setXMm(double xMm) {
        this.xMm = xMm;
    }

    public double getYMm() {
        return yMm;
    }

    public void setYMm(double yMm) {
        this.yMm = yMm;
    }

    public boolean isRotated() {
        return rotated;
    }

    public void setRotated(boolean rotated) {
        this.rotated = rotated;
    }

    public int getStackCount() {
        return stackCount;
    }

    public void setStackCount(int stackCount) {
        this.stackCount = stackCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
