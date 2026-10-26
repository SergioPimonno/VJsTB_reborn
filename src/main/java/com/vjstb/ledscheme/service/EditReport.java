package com.vjstb.ledscheme.service;

import java.util.List;

/**
 * Итог одной правки карт/блоков (docs/receiver-cards/PLAN.md, D10, D18): что заменено, сколько модулей осталось без
 * карты/питания, какие звенья исчезли (их id нужно убрать из цепочек расключения — {@link ModuleIntegrity#dropFromChains}
 * вызывает это сам внутри {@code AppModel}) и какие появились. {@link #message()} — готовая строка для строки замен под
 * холстом («Заменено карт: 2, модулей без карты: 5»).
 *
 * @param noop          правка ничего не изменила (повторная протяжка тем же типом по той же карте, D10) — в историю
 *                      отмены такая запись не попадает
 * @param replaced      сколько существующих карт/блоков задето и заменено
 * @param orphaned      сколько модулей этих карт/блоков осталось ВНЕ выделения — они теперь «без карты»/«без питания»
 * @param removedIds    id удалённых карт/блоков
 * @param createdIds    id созданных
 * @param autoWidth     для автоматического разбиения блоков питания: подобранный размер (модулей в ширину), иначе 0
 * @param autoHeight    то же по высоте
 * @param autoOk        подобранный размер прошёл все проверки (иначе взят запасной 1×1, D18)
 * @param message       строка для пользователя (может быть пустой)
 */
public record EditReport(boolean noop, int replaced, int orphaned, List<String> removedIds, List<String> createdIds,
                         int autoWidth, int autoHeight, boolean autoOk, String message) {

    static EditReport nothing() {
        return new EditReport(true, 0, 0, List.of(), List.of(), 0, 0, true, "");
    }

    /** Правка выполнена, но звеньев не создавала и не удаляла (смена места, порядка, шаблона). */
    static EditReport done() {
        return new EditReport(false, 0, 0, List.of(), List.of(), 0, 0, true, "");
    }

    /** «Подобрано: 4×2 мод. на блок, блоков: 9» (+ предупреждение, если проверки не прошли даже для 1×1); для
     *  остальных правок — пустая строка. */
    public String autoInfo() {
        if (autoWidth <= 0) {
            return "";
        }
        return "Подобрано: " + autoWidth + "×" + autoHeight + " мод. на блок, блоков: " + createdIds.size()
                + (autoOk ? "" : " — проверки не прошли даже для 1×1");
    }
}
