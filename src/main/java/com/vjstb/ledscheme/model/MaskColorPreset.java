package com.vjstb.ledscheme.model;

import java.awt.Color;

/**
 * Цветовая пара чек-борда маски экрана (Task #13/v1.6) — позволяет визуально
 * различать экраны при объединении их масок в общий канвас (например, левый
 * экран — Red/Gray, центральный — Green/Gray, правый — Blue/Gray), под теми же
 * узнаваемыми названиями, что и в референсном списке пресетов стороннего
 * медиасервера пользователя. «Обычный» — прежнее поведение (два тёмных
 * нейтральных оттенка, как было до этой задачи) и остаётся значением по
 * умолчанию, чтобы уже сгенерированные маски не поменяли вид без явного выбора.
 * Развёртки/градиенты (Full H/V Colour sweep, Grayscale H/V, Rainbow) и сложные
 * тестовые паттерны (Fruit/Search/Glazed/Union/TCT и т.д.) из референсного списка
 * сюда сознательно не портированы — не документированы и не нужны для простой
 * визуальной идентификации кабинетов/экранов.
 */
public enum MaskColorPreset {
    NORMAL("Обычный", 0x2b2f36, 0x3d434c),
    WHITE("White", 0xf0f0f0, 0xd0d0d0),
    GRAY("Gray", 0x808080, 0x606060),
    BLACK("Black", 0x1a1a1a, 0x000000),
    BLACK_WHITE("Black/White", 0x000000, 0xffffff),
    LIGHT_DARK_GRAY("Light Gray/Dark Gray", 0xc8c8c8, 0x404040),
    RED_GRAY("Red/Gray", 0xcc2222, 0x404040),
    GREEN_GRAY("Green/Gray", 0x22cc22, 0x404040),
    BLUE_GRAY("Blue/Gray", 0x2266cc, 0x404040),
    FULL_RED("Full Red", 0xff0000, 0xaa0000),
    FULL_GREEN("Full Green", 0x00ff00, 0x00aa00),
    FULL_BLUE("Full Blue", 0x0000ff, 0x0000aa);

    private final String label;
    private final int colorA;
    private final int colorB;

    MaskColorPreset(String label, int colorA, int colorB) {
        this.label = label;
        this.colorA = colorA;
        this.colorB = colorB;
    }

    public String getLabel() {
        return label;
    }

    /** Цвет чек-борд-клетки по чётности (rowIndex + colIndex) % 2 — 0 или 1;
     *  два разных, но не сливающихся друг с другом оттенка, чтобы клетки
     *  оставались различимы (важно для «Full X», где иначе была бы сплошная
     *  заливка одним цветом без видимой границы между кабинетами). */
    public Color color(int parity) {
        return new Color(parity == 0 ? colorA : colorB);
    }

    @Override
    public String toString() {
        return label;
    }
}
