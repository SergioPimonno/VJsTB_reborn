package com.vjstb.ledscheme.model;

/**
 * Порядок нумерации приёмных карт (К1…КN) и блоков питания (БП1…БПn) на экране — параметр «Предпочтений»
 * (решения D15, D21 плана docs/receiver-cards/PLAN.md): восемь вариантов «стартовый угол × по строкам/по
 * столбцам» либо «как нумерация модулей экрана» ({@link #AS_MODULES}, берётся шаблон самого экрана). Хранится в
 * профиле пользователя, а не в проекте: имена карт на печатной схеме зависят от привычки техника, а не от экрана.
 * По умолчанию — {@link #TL_ROWS} («слева направо, сверху вниз»).
 */
public enum ReceiverCardNumbering {
    TL_ROWS("слева направо, сверху вниз", ModuleNumberingPattern.Corner.TOP_LEFT, ModuleNumberingPattern.Axis.ROWS),
    TR_ROWS("справа налево, сверху вниз", ModuleNumberingPattern.Corner.TOP_RIGHT, ModuleNumberingPattern.Axis.ROWS),
    BL_ROWS("слева направо, снизу вверх", ModuleNumberingPattern.Corner.BOTTOM_LEFT, ModuleNumberingPattern.Axis.ROWS),
    BR_ROWS("справа налево, снизу вверх", ModuleNumberingPattern.Corner.BOTTOM_RIGHT, ModuleNumberingPattern.Axis.ROWS),
    TL_COLUMNS("сверху вниз, слева направо", ModuleNumberingPattern.Corner.TOP_LEFT, ModuleNumberingPattern.Axis.COLUMNS),
    TR_COLUMNS("сверху вниз, справа налево", ModuleNumberingPattern.Corner.TOP_RIGHT, ModuleNumberingPattern.Axis.COLUMNS),
    BL_COLUMNS("снизу вверх, слева направо", ModuleNumberingPattern.Corner.BOTTOM_LEFT, ModuleNumberingPattern.Axis.COLUMNS),
    BR_COLUMNS("снизу вверх, справа налево", ModuleNumberingPattern.Corner.BOTTOM_RIGHT, ModuleNumberingPattern.Axis.COLUMNS),
    AS_MODULES("как нумерация модулей экрана", null, null);

    /** Значение по умолчанию (прототип и план: «слева направо, сверху вниз»). */
    public static final ReceiverCardNumbering DEFAULT = TL_ROWS;

    private final String label;
    private final ModuleNumberingPattern.Corner corner;
    private final ModuleNumberingPattern.Axis axis;

    ReceiverCardNumbering(String label, ModuleNumberingPattern.Corner corner, ModuleNumberingPattern.Axis axis) {
        this.label = label;
        this.corner = corner;
        this.axis = axis;
    }

    public String getLabel() {
        return label;
    }

    /** Угол и ось, по которым упорядочиваются карты/блоки; для {@link #AS_MODULES} — из шаблона экрана
     *  (змейка на порядок карт не влияет — как в прототипе). */
    public ModuleNumberingPattern resolve(ModuleNumberingPattern screenPattern) {
        if (this == AS_MODULES) {
            ModuleNumberingPattern p = screenPattern != null ? screenPattern : new ModuleNumberingPattern();
            return new ModuleNumberingPattern(p.getCorner(), p.getAxis(), false);
        }
        return new ModuleNumberingPattern(corner, axis, false);
    }

    @Override
    public String toString() {
        return label;
    }
}
