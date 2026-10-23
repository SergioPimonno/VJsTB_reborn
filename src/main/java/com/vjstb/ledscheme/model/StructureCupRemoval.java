package com.vjstb.ledscheme.model;

/**
 * Убранный вручную стакан наземного конструктива (запрос пользователя 2026-10-22: «бывает что
 * собираем без них — ПКМ по стакану, убрать, и пересчитываем башню»). Стакан стоит на стыке двух
 * вертикальных рам одного столба и ряда и даёт 50 мм высоты (рама 950 + стакан 50 = 1 м); запись
 * означает «на этом стыке стакана нет» — верхняя рама стоит прямо на нижней, всё выше опускается на
 * высоту стакана. Нет записи — стакан есть (старые проекты читаются как раньше).
 *
 * <p>{@code towerIndex}/{@code row} — столб и ряд, как у {@link StructureFrameCell}; {@code
 * segmentIndex} — номер ВЕРХНЕЙ рамы стыка (≥ 1): стакан между рамами {@code segmentIndex − 1} и
 * {@code segmentIndex}. Хранится на {@link Screen#getStructureRemovedCups()}.
 */
public class StructureCupRemoval {

    private int towerIndex;
    private int row;
    private int segmentIndex;

    public StructureCupRemoval() {
    }

    public StructureCupRemoval(int towerIndex, int row, int segmentIndex) {
        this.towerIndex = towerIndex;
        this.row = row;
        this.segmentIndex = segmentIndex;
    }

    public int getTowerIndex() {
        return towerIndex;
    }

    public void setTowerIndex(int towerIndex) {
        this.towerIndex = towerIndex;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public boolean matches(int towerIndex, int row, int segmentIndex) {
        return this.towerIndex == towerIndex && this.row == row && this.segmentIndex == segmentIndex;
    }

    public StructureCupRemoval copy() {
        return new StructureCupRemoval(towerIndex, row, segmentIndex);
    }
}
