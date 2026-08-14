package com.vjstb.ledscheme.model;

import java.util.UUID;

/** Один проектор в спецификации проекта — снапшот введённых/расчётных чисел на
 *  момент добавления. Не входит в граф общей схемы (SchemaNode). Раньше
 *  заполнялся из отдельного UI-калькулятора (выпилен) — модель и её отображение
 *  в {@code OutputStagePanel} оставлены для уже сохранённых данных. */
public class ProjectorInstance {

    private String id = UUID.randomUUID().toString();
    private String label = "";
    private double ansiLumens;
    private double screenGain;
    private String lensName = "";
    private double lensMinThrowRatio;
    private double lensMaxThrowRatio;
    private double throwDistanceM;
    private double imageWidthM;
    private double imageHeightM;
    private double verticalOffsetPercent;
    /** Условия освещения на момент добавления — храним как свободную строку, чтобы
     *  модель не зависела от пакета service (см. остальные модели: свободная строка
     *  вместо FK на enum сервисного слоя, как SchemaEdge.wireType). */
    private String ambientLight = "DIM";

    public ProjectorInstance() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getAnsiLumens() {
        return ansiLumens;
    }

    public void setAnsiLumens(double ansiLumens) {
        this.ansiLumens = ansiLumens;
    }

    public double getScreenGain() {
        return screenGain;
    }

    public void setScreenGain(double screenGain) {
        this.screenGain = screenGain;
    }

    public String getLensName() {
        return lensName;
    }

    public void setLensName(String lensName) {
        this.lensName = lensName;
    }

    public double getLensMinThrowRatio() {
        return lensMinThrowRatio;
    }

    public void setLensMinThrowRatio(double lensMinThrowRatio) {
        this.lensMinThrowRatio = lensMinThrowRatio;
    }

    public double getLensMaxThrowRatio() {
        return lensMaxThrowRatio;
    }

    public void setLensMaxThrowRatio(double lensMaxThrowRatio) {
        this.lensMaxThrowRatio = lensMaxThrowRatio;
    }

    public double getThrowDistanceM() {
        return throwDistanceM;
    }

    public void setThrowDistanceM(double throwDistanceM) {
        this.throwDistanceM = throwDistanceM;
    }

    public double getImageWidthM() {
        return imageWidthM;
    }

    public void setImageWidthM(double imageWidthM) {
        this.imageWidthM = imageWidthM;
    }

    public double getImageHeightM() {
        return imageHeightM;
    }

    public void setImageHeightM(double imageHeightM) {
        this.imageHeightM = imageHeightM;
    }

    public double getVerticalOffsetPercent() {
        return verticalOffsetPercent;
    }

    public void setVerticalOffsetPercent(double verticalOffsetPercent) {
        this.verticalOffsetPercent = verticalOffsetPercent;
    }

    public String getAmbientLight() {
        return ambientLight;
    }

    public void setAmbientLight(String ambientLight) {
        this.ambientLight = ambientLight;
    }
}
