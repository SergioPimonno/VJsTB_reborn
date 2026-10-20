package com.vjstb.ledscheme.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Порядок NovaLCT-экранов (Screen1..N) в мультиэкранном .scr и проверка его
 *  допустимости — для шага «Порядок экранов NovaLCT» ({@code NovaLctScreenOrderDialog}).
 *
 * <p><b>Зачем (запрос пользователя)</b>: на одном контроллере в NovaLCT несколько
 * Screen, и пользователю важно самому назначить, какой экран проекта станет Screen1,
 * какой Screen2 и т.д. Раньше порядок вычислялся автоматически по (Sending Card, Port)
 * первой записи экрана и показать/изменить его было нельзя.
 *
 * <p><b>Ограничение NovaLCT</b> (подтверждено ручным экспериментом пользователя, см.
 * {@link NovaLctScrWriter#firstCardPort}): если экран с БОЛЬШИМ номером порта общей
 * Sending Card получает МЕНЬШИЙ индекс, чем экран с МЕНЬШИМ портом той же карты —
 * {@code LoadFromFile} отклоняет файл целиком. Поэтому такой порядок
 * ({@link #violation}) запрещён — чтобы поменять Screen-номера этих экранов, нужно
 * поменять их расключение. Для экранов на РАЗНЫХ картах отклонение порядка от
 * возрастания карты реальной загрузкой не проверялось — {@link #warning} просто
 * предупреждает, файл при этом пишется. */
public final class NovaLctScreenOrder {

    private NovaLctScreenOrder() {
    }

    /** Один NovaLCT-экран файла: подпись для UI + готовый блок (Standard или Complex). */
    public record Entry(String label, NovaLctScrWriter.MixedScreen screen) {
        /** Sending Card первой записи, 0-based. */
        public int card() {
            return screen.firstCard();
        }

        /** Ethernet Port первой записи, 0-based. */
        public int port() {
            return screen.firstPort();
        }

        public boolean isComplex() {
            return !screen.isStandard();
        }
    }

    /** Автоматический порядок — по возрастанию (Sending Card, Port), тот же, что писатель
     *  применял всегда (см. {@code NovaLctCombineHelper.SCREEN_ORDER}); ничья — исходный порядок. */
    public static List<Entry> autoOrder(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(Entry::card).thenComparingInt(Entry::port));
        return sorted;
    }

    /** Причина, по которой порядок {@code ordered} NovaLCT отклонит, либо {@code null}. */
    public static String violation(List<Entry> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                Entry a = ordered.get(i);
                Entry b = ordered.get(j);
                if (a.card() == b.card() && a.port() > b.port()) {
                    return "«" + a.label() + "» (Screen" + (i + 1) + ", порт " + (a.port() + 1)
                            + ") не может идти раньше «" + b.label() + "» (Screen" + (j + 1) + ", порт "
                            + (b.port() + 1) + ") — на одной Sending Card " + (a.card() + 1)
                            + " экран с большим номером порта не должен получать меньший номер Screen,"
                            + " иначе NovaLCT отклонит файл. Измените расключение портов.";
                }
            }
        }
        return null;
    }

    /** Предупреждение для допустимого, но непроверенного порядка (экран с большей картой
     *  стоит раньше экрана с меньшей), либо {@code null}. */
    public static String warning(List<Entry> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                if (ordered.get(i).card() > ordered.get(j).card()) {
                    return "Порядок идёт не по возрастанию Sending Card («" + ordered.get(i).label()
                            + "» на карте " + (ordered.get(i).card() + 1) + " раньше «" + ordered.get(j).label()
                            + "» на карте " + (ordered.get(j).card() + 1)
                            + "). Такой порядок реальной загрузкой в NovaLCT не проверялся — проверьте файл.";
                }
            }
        }
        return null;
    }
}
