package com.vjstb.ledscheme.settings;

/** Пресет оформления общей схемы (docs/schema-ports-rework/PLAN.md, задача T1.5,
 *  D12) — рисует тот же {@code SchemaCanvasPanel}, меняются только цвета/толщины
 *  (см. будущий {@code SchemaStyle}, этап 3 PLAN.md).
 *
 * <ul>
 *   <li>{@link #SCREEN} («Экранный») — текущий вид, следует теме интерфейса.</li>
 *   <li>{@link #PRINT} («Печатный») — белый фон, жёлтые блоки, номинальные цвета
 *   линий (сеть/синхро/LED-данные/питание по номиналу), как на схемах пользователя
 *   из yEd (см. DIALOG.md) — для печати/PDF, а не для работы на экране.</li>
 * </ul>
 *
 * <p>Пользовательский цвет конкретной связи ({@code SchemaEdge#getColor()})
 * по-прежнему важнее любого пресета — см. D9 PLAN.md.
 */
public enum SchemaStylePreset {
    SCREEN("Экранный"),
    PRINT("Печатный");

    private final String label;

    SchemaStylePreset(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
