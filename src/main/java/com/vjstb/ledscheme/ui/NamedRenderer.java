package com.vjstb.ledscheme.ui;

import java.awt.Component;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

/** Рендерер элемента списка: жирное название + серая мелкая строка-пояснение. */
public class NamedRenderer<T> extends DefaultListCellRenderer {

    private final Function<T, String> title;
    private final Function<T, String> meta;

    public NamedRenderer(Function<T, String> title, Function<T, String> meta) {
        this.title = title;
        this.meta = meta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value != null) {
            T item = (T) value;
            String metaText = meta != null ? meta.apply(item) : "";
            // Без ограничения ширины длинная строка-пояснение (например, у контроллера
            // со сводкой по картам/вх.портам) рендерится ОДНОЙ строкой на всю её
            // естественную ширину — если список уже эту ширину не даёт, JScrollPane
            // получает СВОЙ горизонтальный скроллбар вместо переноса текста на
            // следующую строку (баг-репорт: список "уезжал" вбок вместо адаптации
            // под окно). list.getWidth() ещё 0 до первой раскладки — тогда не
            // ограничиваем, всё равно перерисуется после неё.
            int w = list.getWidth();
            String widthStyle = w > 60 ? " style='width:" + (w - 36) + "px'" : "";
            setText("<html><body" + widthStyle + "><b>" + escape(title.apply(item)) + "</b>"
                    + (metaText.isEmpty() ? "" : "<br><span style='font-size:9px;color:#7d8590;'>" + escape(metaText) + "</span>")
                    + "</body></html>");
        }
        return this;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
