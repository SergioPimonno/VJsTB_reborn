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

    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows) {
        int count = Math.max(list.getModel().getSize(), minRows);
        int rows = Math.min(count, maxRows);
        int rowH = estimateRowHeight(list);
        int h = rows * rowH + 6;
        int w = Math.max(scroll.getPreferredSize().width, 100);
        scroll.setPreferredSize(new Dimension(w, h));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        scroll.revalidate();
    }

    private static int estimateRowHeight(JList<?> list) {
        // рендерер списка рисует двухстрочный HTML (заголовок + мелкая мета-строка)
        int base = list.getFontMetrics(list.getFont()).getHeight();
        return (int) Math.round(base * 2.3) + 8;
    }
}
