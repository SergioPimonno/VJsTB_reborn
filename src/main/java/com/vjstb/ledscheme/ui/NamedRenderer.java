package com.vjstb.ledscheme.ui;

import java.awt.Component;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

/** Рендерер элемента списка: жирное название + серая мелкая строка-пояснение. */
public class NamedRenderer<T> extends DefaultListCellRenderer {

    private final Function<T, String> title;
    private final Function<T, String> meta;
    private final Function<T, Boolean> shared;
    private int fixedWidth = -1;

    public NamedRenderer(Function<T, String> title, Function<T, String> meta) {
        this(title, meta, null);
    }

    /** {@code shared} — если задан и возвращает true для элемента, заголовок
     *  красится в {@link Palette#LIBRARY_SHARED} вместо обычного цвета текста, вместо
     *  прежнего текстового префикса "[Общая] " в самом имени (только визуальная
     *  метка, имя остаётся чистым). */
    public NamedRenderer(Function<T, String> title, Function<T, String> meta, Function<T, Boolean> shared) {
        this.title = title;
        this.meta = meta;
        this.shared = shared;
    }

    /** Задаёт ширину содержимого ЯВНО, вместо чтения {@code list.getWidth()} —
     *  нужно вызывающим, которые сами живьём отслеживают и пересчитывают ширину
     *  секции (см. LibrariesStagePanel.applyContentWidth): иначе получается
     *  порочный круг — JList.getScrollableTracksViewportWidth() перестаёт
     *  подгонять список под вьюпорт, как только его preferredSize однажды
     *  оказался ШИРЕ вьюпорта (например, на первой отрисовке до первого resize-
     *  события, когда ширина ещё не известна и HTML рендерится нешироко
     *  ограниченным), а сам this.getWidth() после этого больше НИКОГДА не
     *  уменьшается сам по себе — список навсегда остаётся с лишним горизонтальным
     *  скроллбаром (баг-репорт: "оффсет окошка библиотеки кабинетов, раздражает").
     *  -1 (по умолчанию) — старое поведение, list.getWidth(). */
    public void setFixedWidth(int fixedWidth) {
        this.fixedWidth = fixedWidth;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value != null) {
            T item = (T) value;
            String metaText = meta != null ? meta.apply(item) : "";
            int w = fixedWidth > 0 ? fixedWidth : list.getWidth();
            boolean isShared = shared != null && Boolean.TRUE.equals(shared.apply(item));
            String titleHtml = "<b>" + escape(title.apply(item)) + "</b>";
            if (isShared) {
                titleHtml = "<font color='" + toHex(Palette.LIBRARY_SHARED) + "'>" + titleHtml + "</font>";
            }
            setText("<html><body>" + titleHtml
                    + (metaText.isEmpty() ? "" : "<br><span style='font-size:9px;color:#7d8590;'>" + escape(metaText) + "</span>")
                    + "</body></html>");
            // CSS "width" на <body> сам по себе НЕ ограничивает preferred-размер,
            // возвращаемый JLabel.getPreferredSize() (проверено эмпирически — HTML
            // рендерится в естественную ширину независимо от style) — единственный
            // надёжный способ заставить перенос строк на конкретную ширину и получить
            // ПРАВИЛЬНУЮ (не заниженную) высоту под перенесённый текст — явно задать
            // размер закешированному Swing HTML-view через View.setSize(...) ДО того,
            // как JList спросит getPreferredSize() у этого же компонента-рендерера
            // (см. BasicHTML — тот же View-объект отдаётся по ключу propertyKey и
            // используется JLabel.getPreferredSize() под капотом). Без этого список
            // молча остаётся шире вьюпорта и получает лишний горизонтальный скролл
            // (баг-репорт: "оффсет окошка библиотеки кабинетов, раздражает").
            if (w > 60) {
                Object htmlView = getClientProperty(BasicHTML.propertyKey);
                if (htmlView instanceof View view) {
                    view.setSize(w - 36, 0);
                }
            }
        }
        return this;
    }

    private static String toHex(java.awt.Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
