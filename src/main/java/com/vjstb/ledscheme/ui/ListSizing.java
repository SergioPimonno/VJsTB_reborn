package com.vjstb.ledscheme.ui;

import java.awt.Dimension;
import javax.swing.JList;
import javax.swing.JScrollPane;

/**
 * Подгоняет высоту списка (в скролл-панели) под количество элементов, в отличие
 * от фиксированного числа видимых строк — короткие списки не занимают лишнее
 * место, длинные ограничиваются максимумом строк и дальше скроллятся.
 */
public final class ListSizing {

    private ListSizing() {
    }

    /** Ширина остаётся безграничной (см. capWidth=false ниже) — прежнее поведение,
     *  для списков, вложенных в колонку ФИКСИРОВАННОЙ ширины (см.
     *  UiKit.vboxFixedWidth в SetupStagePanel): там список и должен заполнять всю
     *  ширину колонки целиком, ограничивает её именно колонка-обёртка снаружи. */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows) {
        fit(list, scroll, minRows, maxRows, false);
    }

    /** capWidth=true — вдобавок к высоте ограничивает и МАКСИМАЛЬНУЮ ШИРИНУ по факту
     *  содержимого, а не оставляет её безграничной — для списков в секциях НА ВСЮ
     *  ширину этапа без своей колонки-обёртки фиксированной ширины (см.
     *  LibrariesStagePanel): без этого BoxLayout/JSplitPane растягивал список на всё
     *  окно, оставляя пустой серый фон справа от реального содержимого (баг-репорт,
     *  Task #95/v1.5: "масштабирование интерфейса растягивается в пустоту"). */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows, boolean capWidth) {
        int count = Math.max(list.getModel().getSize(), minRows);
        int rows = Math.min(count, maxRows);
        int rowH = estimateRowHeight(list);
        int h = rows * rowH + 6;
        int w = Math.max(scroll.getPreferredSize().width, 100);
        scroll.setPreferredSize(new Dimension(w, h));
        scroll.setMaximumSize(new Dimension(capWidth ? w : Integer.MAX_VALUE, h));
        scroll.revalidate();
    }

    /** capWidth=true с ЗАДАННОЙ шириной вместо ширины по факту текущего содержимого —
     *  для панелей, которые сами отслеживают доступную ширину окна (за вычетом
     *  вертикального скроллбара) и пересчитывают её при ресайзе живьём, а не один
     *  раз при первой сборке (см. LibrariesStagePanel — баг-репорт: списки были
     *  зафиксированы на жёстких пиксельных значениях, из-за чего длинный текст
     *  элемента вылезал за ширину и получал СВОЙ горизонтальный скроллбар, а сама
     *  панель не подстраивалась под реальную ширину окна). */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows, int width) {
        int count = Math.max(list.getModel().getSize(), minRows);
        int rows = Math.min(count, maxRows);
        int rowH = estimateRowHeight(list);
        int h = rows * rowH + 6;
        scroll.setPreferredSize(new Dimension(width, h));
        scroll.setMaximumSize(new Dimension(width, h));
        scroll.revalidate();
    }

    private static int estimateRowHeight(JList<?> list) {
        // рендерер списка рисует двухстрочный HTML (заголовок + мелкая мета-строка)
        int base = list.getFontMetrics(list.getFont()).getHeight();
        return (int) Math.round(base * 2.3) + 8;
    }
}
