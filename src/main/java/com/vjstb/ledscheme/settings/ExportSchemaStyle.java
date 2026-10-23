package com.vjstb.ledscheme.settings;

/** Оформление схем при ЭКСПОРТЕ (окно «Параметры экспорта» этапа «Вывод», запрос
 *  пользователя 2026-10-24: «выбор способа отрисовки в настройках экспорта; все схемы
 *  в таком режиме — в печатном стиле»). Отдельно от {@link SchemaStylePreset} из
 *  «Персонализации»: на экране пользователь может работать в тёмной теме, а схемы для
 *  печати/PDF выкладывать белыми, не переключая интерфейс туда-обратно.
 *
 * <ul>
 *   <li>{@link #FOLLOW} — как раньше: берётся «Оформление общей схемы» из
 *   «Персонализации», остальные схемы рисуются цветами текущей темы интерфейса
 *   (значение по умолчанию — поведение старых профилей не меняется).</li>
 *   <li>{@link #SCREEN} — экспортные схемы в экранном оформлении и цветах текущей темы.</li>
 *   <li>{@link #PRINT} — ВСЕ экспортируемые схемы (общая схема, схемы расключения
 *   экранов, обзор сцены, таблица экранов, легенда портов, риггинг, транспорт) — в
 *   печатном стиле: белый фон, тёмный текст, жёлтые блоки общей схемы, номинальные цвета
 *   линий, независимо от темы интерфейса.</li>
 * </ul>
 */
public enum ExportSchemaStyle {
    FOLLOW("Как в Персонализации"),
    SCREEN("Экранный"),
    PRINT("Печатный");

    private final String label;

    ExportSchemaStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Пресет общей схемы для экспорта: {@link #FOLLOW} — пресет профиля. */
    public SchemaStylePreset resolve(SchemaStylePreset profilePreset) {
        return switch (this) {
            case SCREEN -> SchemaStylePreset.SCREEN;
            case PRINT -> SchemaStylePreset.PRINT;
            case FOLLOW -> profilePreset;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
