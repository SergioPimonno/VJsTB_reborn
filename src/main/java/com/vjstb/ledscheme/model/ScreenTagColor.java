package com.vjstb.ledscheme.model;

import java.awt.Color;

/**
 * Цветная метка экрана для визуальной группировки по зоне/площадке на схеме сцены
 * (Задача v3.0, баг-репорт 2026-09-14 — обзор сцены прерига: "цветные метки
 * принадлежности к зоне у экранов"). Небольшой фиксированный набор различимых
 * цветов, а не произвольный {@code Color}/hex — та же логика, что у {@link
 * MaskColorPreset} (узнаваемые, заведомо различимые варианты вместо цветовой
 * пикалки с риском, что два экрана одной зоны получат чуть разные оттенки и
 * визуально перестанут читаться как одна группа). {@code NONE} — метка не
 * назначена, экран рисуется как раньше (без тонировки заливки).
 */
public enum ScreenTagColor {
    NONE("Без метки", null),
    RED("Красный", 0xc0463a),
    ORANGE("Оранжевый", 0xc9803f),
    YELLOW("Жёлтый", 0xc7ad3f),
    GREEN("Зелёный", 0x4f9d5d),
    TEAL("Бирюзовый", 0x3f9188),
    BLUE("Синий", 0x3f7ec2),
    PURPLE("Фиолетовый", 0x7d6bb0),
    PINK("Розовый", 0xc15b96);

    private final String label;
    private final Integer rgb;

    ScreenTagColor(String label, Integer rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public String getLabel() {
        return label;
    }

    /** {@code null} для {@link #NONE} — вызывающий код сам решает, чем заменить
     *  (обычно — не тонировать заливку вовсе, см. {@code SceneCanvasPanel#paint}). */
    public Color color() {
        return rgb != null ? new Color(rgb) : null;
    }

    @Override
    public String toString() {
        return label;
    }
}
