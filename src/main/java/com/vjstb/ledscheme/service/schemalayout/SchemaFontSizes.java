package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaSheet;

/**
 * Разрешение «эффективного» размера шрифта блока/подписи линии общей схемы —
 * единственное место, где склеены три уровня (запрос пользователя 2026-09-30,
 * docs/masks-and-schema-sheets/PLAN.md, пункт 1, трек C4: «кнопка „задать размер
 * шрифта для всей схемы" — для всех блоков с дефолтным размером; блоки с кастомным
 * размером не трогать»):
 * <ol>
 *   <li>собственный {@link SchemaNode#getFontSize()} / {@link SchemaEdge#getFontSize()}
 *       (задан через контекстное меню блока/линии) — всегда побеждает;</li>
 *   <li>умолчание схемы-листа {@link SchemaSheet#getDefaultFontSize()} /
 *       {@link SchemaSheet#getDefaultEdgeFontSize()} (кнопка «Шрифт схемы…»);</li>
 *   <li>стандарт: {@link SchemaLayoutMetrics#LABEL_FONT_SIZE} для блока,
 *       {@link #EDGE_FONT_SIZE} для подписи линии.</li>
 * </ol>
 *
 * <p>Размер схемы НИКОГДА не записывается в сами узлы/связи: иначе они стали бы
 * «кастомными» и последующая смена шрифта схемы их уже не затрагивала бы — ровно то,
 * чего пользователь не хотел. Поэтому все места, читающие размер (отрисовка холста,
 * раскладка гнёзд, минимальный размер блока в {@code AppModel}), обязаны брать его
 * отсюда, а не из {@code getFontSize()} напрямую.
 *
 * <p>Класс без Swing и без состояния — чтобы покрывался обычными юнит-тестами.
 */
public final class SchemaFontSizes {

    /** Стандартный размер подписи линии (пункты) — прежний {@code
     *  SchemaCanvasPanel.EDGE_FONT}, вынесен сюда, чтобы холст и тесты делили одну
     *  константу. */
    public static final int EDGE_FONT_SIZE = 10;

    private SchemaFontSizes() {
    }

    /** Размер, заданный явно для блока: свой, иначе умолчание схемы; {@code null} —
     *  нигде не задан (рисуется стандартным шрифтом — вызывающий код в этом случае
     *  не должен подменять унаследованный шрифт). {@code sheet} может быть {@code
     *  null} (узел вне листа — как у старых/тестовых данных). */
    public static Integer nodeOverride(SchemaNode node, SchemaSheet sheet) {
        if (node.getFontSize() != null) {
            return node.getFontSize();
        }
        return sheet != null ? sheet.getDefaultFontSize() : null;
    }

    /** Итоговый размер шрифта подписей блока (пункты). Заголовок блока крупнее на
     *  2pt (см. {@code SchemaCanvasPanel.nodeTitleFont}). */
    public static int nodeSize(SchemaNode node, SchemaSheet sheet) {
        Integer o = nodeOverride(node, sheet);
        return o != null ? o : SchemaLayoutMetrics.LABEL_FONT_SIZE;
    }

    /** Размер, заданный явно для подписи линии: свой, иначе умолчание схемы; {@code
     *  null} — стандартный. */
    public static Integer edgeOverride(SchemaEdge edge, SchemaSheet sheet) {
        if (edge.getFontSize() != null) {
            return edge.getFontSize();
        }
        return sheet != null ? sheet.getDefaultEdgeFontSize() : null;
    }

    /** Итоговый размер шрифта подписи линии (пункты). */
    public static int edgeSize(SchemaEdge edge, SchemaSheet sheet) {
        Integer o = edgeOverride(edge, sheet);
        return o != null ? o : EDGE_FONT_SIZE;
    }

    /** Нормализация введённого пользователем размера: {@code null}/0/отрицательное —
     *  «стандартный» ({@code null}); иначе значение, зажатое в 1..72 (тот же
     *  диапазон, что у спиннера «Размер шрифта…»). */
    public static Integer normalize(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return Math.min(value, 72);
    }
}
