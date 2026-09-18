package com.vjstb.ledscheme.service.schemalayout;

/** Геометрические константы раскладки гнёзд узла общей схемы (docs/schema-ports-
 *  rework/PLAN.md, задача T2.1/§2.4) — единственное место, где они заданы; ни
 *  {@code SchemaCanvasPanel}, ни {@code AppModel} больше не хранят свои копии (в
 *  отличие от старого кода, где {@code PORT_ROW_H}/{@code CARD_HEADER_H} были
 *  продублированы в двух местах и незаметно разошлись — 13 и 14 — см. DIALOG.md,
 *  реплика 1, п.5). Значения — стартовые ориентиры, подбирались по эталонным
 *  картинкам T0.2, а не по месту; доводка внешнего вида — этап 3/6 PLAN.md. */
public final class SchemaLayoutMetrics {

    private SchemaLayoutMetrics() {
    }

    /** Шаг одной строки-гнезда на сторонах LEFT/RIGHT (вдоль вертикали). */
    public static final double ROW_STEP = 14;
    /** Минимальный шаг одной колонки-гнезда на сторонах TOP/BOTTOM (вдоль
     *  горизонтали) — реально используемый шаг ещё зависит от ширины подписи
     *  номера слота (см. {@link NodePortLayout}), это только нижняя граница. */
    public static final double COL_STEP_MIN = 16;
    /** Резерв под шапку ОДНОГО отсека карты — та же величина, что и шаг обычной
     *  строки/колонки (шапка по сути ещё одна такая же строка/колонка без гнезда),
     *  см. javadoc у прежней {@code CARD_HEADER_H} в {@code SchemaCanvasPanel}. */
    public static final double BAY_HEADER_STEP = ROW_STEP;
    /** Промежуток МЕЖДУ соседними отсеками одной стороны. */
    public static final double BAY_GAP = 6;
    /** Внутренний отступ рамки отсека сверху/снизу (вдоль поперечной оси). */
    public static final double BAY_PAD = 3;
    /** Диаметр/толщина точки-гнезда вдоль поперечной оси стороны (шире, чем вдоль
     *  самой стороны — гнездо на LEFT/RIGHT шире, чем высокое: 8×6, наоборот на
     *  TOP/BOTTOM). */
    public static final double PIN_ACROSS = 8;
    public static final double PIN_ALONG = 6;
    /** Полоса под название узла — строки LEFT/RIGHT начинаются НИЖЕ неё; для TOP/
     *  BOTTOM название не мешает (те подписи в отдельной полосе ближе к границе). */
    public static final double TITLE_BAND = 20;
    /** Глубина зоны TOP/BOTTOM (внутрь блока от границы) — фиксированная: строка
     *  номеров гнёзд + строка подписи группы со скобкой, см. §2.4 PLAN.md. Не
     *  зависит от длины подписи — та растягивается ВДОЛЬ границы, не вглубь. */
    public static final double HORIZONTAL_SIDE_DEPTH = ROW_STEP * 2;
    /** Минимальная глубина зоны LEFT/RIGHT, если ни одной подписи нет (узел без
     *  разъёмов на этой стороне вообще) — просто чтобы не схлопывалось в 0. */
    public static final double VERTICAL_SIDE_DEPTH_MIN = 30;
    /** Отступ подписи от гнезда вглубь блока на LEFT/RIGHT. */
    public static final double LABEL_PAD = 8;
    /** Минимальная ширина/высота блока — тот же ориентир, что {@code
     *  SchemaCanvasPanel.MIN_NODE_W/H} (узел без разъёмов вообще). */
    public static final double MIN_SIZE = 60;
    public static final int LABEL_FONT_SIZE = 10;
    public static final int TITLE_FONT_SIZE = 12;
}
