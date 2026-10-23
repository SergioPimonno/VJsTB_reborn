package com.vjstb.ledscheme.model;

/**
 * Ручное положение одной раздельной башни наземного конструктива в плане экрана (запрос
 * пользователя 2026-10-22: «две башни встык под 90°, и сразу под любым углом»). Хранится в
 * {@link Screen#getStructureTowerPlacements()}, действует только при
 * {@link Screen#isStructureManualLayout()}; без ручного режима положение башен считает
 * {@code service.StructureCurveMath#placement} по форме экрана и этих записей не читает.
 *
 * <p>{@code towerIndex} — номер БАШНИ (пары столбов {@code 2k}/{@code 2k+1} в ячейках
 * конструктива, см. {@link StructureFrameCell}), не столба. Координаты — в системе ЭКРАНА (вид
 * сверху, мм: x вдоль развёрнутой ширины {@code [0, W]}, z к зрителю, лицевая поверхность
 * экрана на {@code z = 0}), а не группы: поэтому экран с башнями можно сдвинуть или повернуть
 * целиком ({@link Screen#getStructurePoseXMm()}), и башни поедут вместе с ним.
 * {@code (anchorXMm, anchorZMm)} — середина ПЕРЕДНЕЙ плоскости башни (грани переднего ряда, ближней к
 * экрану), {@code yawDeg} — поворот вокруг вертикали, тот же знак, что {@code glRotate(yawDeg, 0,
 * 1, 0)} в 3D (см. {@code StructureCurveMath.Placement}). Башня — жёсткий блок, угол любой, не
 * только кратный 90°.
 */
public class StructureTowerPlacement {

    private int towerIndex;
    private double anchorXMm;
    private double anchorZMm;
    private double yawDeg;

    public StructureTowerPlacement() {
    }

    public StructureTowerPlacement(int towerIndex, double anchorXMm, double anchorZMm, double yawDeg) {
        this.towerIndex = towerIndex;
        this.anchorXMm = anchorXMm;
        this.anchorZMm = anchorZMm;
        this.yawDeg = yawDeg;
    }

    public int getTowerIndex() {
        return towerIndex;
    }

    public void setTowerIndex(int towerIndex) {
        this.towerIndex = towerIndex;
    }

    public double getAnchorXMm() {
        return anchorXMm;
    }

    public void setAnchorXMm(double anchorXMm) {
        this.anchorXMm = anchorXMm;
    }

    public double getAnchorZMm() {
        return anchorZMm;
    }

    public void setAnchorZMm(double anchorZMm) {
        this.anchorZMm = anchorZMm;
    }

    public double getYawDeg() {
        return yawDeg;
    }

    public void setYawDeg(double yawDeg) {
        this.yawDeg = yawDeg;
    }

    /** Глубокая копия для снимка отмены и черновых копий экрана. */
    public StructureTowerPlacement copy() {
        return new StructureTowerPlacement(towerIndex, anchorXMm, anchorZMm, yawDeg);
    }
}
