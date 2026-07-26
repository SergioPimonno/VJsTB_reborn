package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.Screen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Шаблоны серпантинной прописи прямоугольной области кабинетов — как в
 * инструменте «Быстрая прописка» NovaLCT: начальный угол области (4 варианта) ×
 * основное направление обхода (по строкам/по столбцам) = 8 шаблонов. Пропущенные
 * (скрытые/уже занятые другой цепочкой) кабинеты просто выпадают из
 * последовательности, не прерывая и не сдвигая сам маршрут — соседняя по
 * шаблону ячейка становится следующей.
 */
public final class ChainPatterns {

    private ChainPatterns() {
    }

    public enum Pattern {
        TOP_LEFT_HORIZONTAL("Слева-сверху, по строкам"),
        TOP_LEFT_VERTICAL("Слева-сверху, по столбцам"),
        TOP_RIGHT_HORIZONTAL("Справа-сверху, по строкам"),
        TOP_RIGHT_VERTICAL("Справа-сверху, по столбцам"),
        BOTTOM_LEFT_HORIZONTAL("Слева-снизу, по строкам"),
        BOTTOM_LEFT_VERTICAL("Слева-снизу, по столбцам"),
        BOTTOM_RIGHT_HORIZONTAL("Справа-снизу, по строкам"),
        BOTTOM_RIGHT_VERTICAL("Справа-снизу, по столбцам");

        private final String label;

        Pattern(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        boolean startBottom() {
            return this == BOTTOM_LEFT_HORIZONTAL || this == BOTTOM_LEFT_VERTICAL
                    || this == BOTTOM_RIGHT_HORIZONTAL || this == BOTTOM_RIGHT_VERTICAL;
        }

        boolean startRight() {
            return this == TOP_RIGHT_HORIZONTAL || this == TOP_RIGHT_VERTICAL
                    || this == BOTTOM_RIGHT_HORIZONTAL || this == BOTTOM_RIGHT_VERTICAL;
        }

        boolean horizontalPrimary() {
            return this == TOP_LEFT_HORIZONTAL || this == TOP_RIGHT_HORIZONTAL
                    || this == BOTTOM_LEFT_HORIZONTAL || this == BOTTOM_RIGHT_HORIZONTAL;
        }
    }

    /** Кабинеты прямоугольной области [rowStart..rowEnd] x [colStart..colEnd]
     *  (включительно) в порядке серпантина заданного шаблона — "змейка" идёт по
     *  главной оси (строки или столбцы), на каждой следующей линии направление
     *  обхода разворачивается (boustrophedon), как физически укладывается кабель
     *  по факту смонтированной стены. available — доп. фильтр (например,
     *  "кабинет ещё не занят другой цепочкой") сверх обязательной проверки на
     *  scr.cabinetAt(...) != null и !isHidden(). */
    public static List<String> orderedIds(Screen scr, int rowStart, int rowEnd, int colStart, int colEnd,
                                           Pattern pattern, Predicate<String> available) {
        List<String> result = new ArrayList<>();
        if (scr == null || pattern == null) {
            return result;
        }
        boolean startBottom = pattern.startBottom();
        boolean startRight = pattern.startRight();
        if (pattern.horizontalPrimary()) {
            List<Integer> rows = range(rowStart, rowEnd, startBottom);
            boolean flip = startRight;
            for (int row : rows) {
                for (int col : range(colStart, colEnd, flip)) {
                    addIfAvailable(scr, row, col, result, available);
                }
                flip = !flip;
            }
        } else {
            List<Integer> cols = range(colStart, colEnd, startRight);
            boolean flip = startBottom;
            for (int col : cols) {
                for (int row : range(rowStart, rowEnd, flip)) {
                    addIfAvailable(scr, row, col, result, available);
                }
                flip = !flip;
            }
        }
        return result;
    }

    private static List<Integer> range(int a, int b, boolean reversed) {
        List<Integer> list = new ArrayList<>();
        for (int i = a; i <= b; i++) {
            list.add(i);
        }
        if (reversed) {
            Collections.reverse(list);
        }
        return list;
    }

    private static void addIfAvailable(Screen scr, int row, int col, List<String> result, Predicate<String> available) {
        CabinetInstance cab = scr.cabinetAt(row, col);
        if (cab != null && !cab.isHidden() && (available == null || available.test(cab.getId()))) {
            result.add(cab.getId());
        }
    }
}
