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
            setText("<html><b>" + escape(title.apply(item)) + "</b>"
                    + (metaText.isEmpty() ? "" : "<br><span style='font-size:9px;color:#7d8590;'>" + escape(metaText) + "</span>")
                    + "</html>");
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
