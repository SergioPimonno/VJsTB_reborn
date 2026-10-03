package com.vjstb.ledscheme.settings;

import com.vjstb.ledscheme.model.NodeSide;

/** С какой стороны связи (режим «Авто под 90°») заходят в блоки экранов на общей схеме —
 *  запрос пользователя 2026-10-02: «чтобы линии старались заходить в блоки экранов снизу — так
 *  визуально красивее», настройка по образцу «Ориентация блоков по умолчанию», отдельная для
 *  сигнала и питания ({@link UserProfile#getSchemaScreenEntrySide}).
 *
 * <p>Касается только КОНЦА-ПРИЁМНИКА связи на узле экрана без настоящего гнезда (связь на блок целиком
 * или на гнездо-кабинет миниатюры расключения): выход экрана и узлы с гнёздами остаются на сторонах,
 * которые задаёт раскладка. {@link #NEAREST} — прежнее поведение (ближайшая к другому концу грань). */
public enum ScreenEntrySide {
    BOTTOM("Снизу", NodeSide.BOTTOM),
    TOP("Сверху", NodeSide.TOP),
    LEFT("Слева", NodeSide.LEFT),
    RIGHT("Справа", NodeSide.RIGHT),
    NEAREST("Ближайшая грань", null);

    private final String label;
    private final NodeSide side;

    ScreenEntrySide(String label, NodeSide side) {
        this.label = label;
        this.side = side;
    }

    public String getLabel() {
        return label;
    }

    /** Грань блока, к которой принудительно подводится линия; {@code null} — не принудительно. */
    public NodeSide nodeSide() {
        return side;
    }

    @Override
    public String toString() {
        return label;
    }
}
