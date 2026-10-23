package com.vjstb.ledscheme.model;

/**
 * Ширина одной раздельной башни: рама, из которой собраны её горизонтальные элементы (перемычки,
 * основание) — запрос пользователя 2026-10-22: «в башню можно вставить короткие рамы, чтобы сделать
 * башню уже» (стандартная рама 950 мм + стакан 50 = шаг столбов 1000 мм; короткая 850 + 50 = 900 мм).
 * Нет записи — башня стандартной ширины, как было. Хранится на {@link
 * Screen#getStructureTowerWidths()}; номер — башни (пара столбов {@code 2k}/{@code 2k+1}), как у
 * {@link StructureTowerPlacement}.
 */
public class StructureTowerWidth {

    private int towerIndex;
    /** Тип рамы из библиотеки конструктива ({@code StructureFrameType#getId()}), длинная сторона
     *  которой лежит поперёк башни; {@code null} — стандартная ширина. */
    private String frameTypeId;

    public StructureTowerWidth() {
    }

    public StructureTowerWidth(int towerIndex, String frameTypeId) {
        this.towerIndex = towerIndex;
        this.frameTypeId = frameTypeId;
    }

    public int getTowerIndex() {
        return towerIndex;
    }

    public void setTowerIndex(int towerIndex) {
        this.towerIndex = towerIndex;
    }

    public String getFrameTypeId() {
        return frameTypeId;
    }

    public void setFrameTypeId(String frameTypeId) {
        this.frameTypeId = frameTypeId;
    }

    public StructureTowerWidth copy() {
        return new StructureTowerWidth(towerIndex, frameTypeId);
    }
}
