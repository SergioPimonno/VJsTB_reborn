package com.vjstb.ledscheme.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * {@link FlowLayout}, который переносит компоненты на следующую строку, когда не хватает ширины, и сообщает
 * родителю высоту ПО ЭТОЙ ширине (обычный {@code FlowLayout} всегда просит одну строку — в узкой панели кнопки
 * вылезали за край, запрос пользователя 2026-10-22: «поля и подписи вылезли за пределы окон»). Ширина берётся у
 * ближайшего предка с известным размером — так ряд кнопок в панели, чья ширина задаётся окном, переносится сам.
 */
final class WrapLayout extends FlowLayout {

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container holder = target;
            while (holder.getSize().width == 0 && holder.getParent() != null) {
                holder = holder.getParent();
            }
            int width = holder.getSize().width;
            if (width == 0) {
                width = Integer.MAX_VALUE;
            }
            Insets in = target.getInsets();
            int sideSpace = in.left + in.right + getHgap() * 2;
            int maxRow = width - sideSpace;

            int totalW = 0;
            int totalH = 0;
            int rowW = 0;
            int rowH = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowW + d.width > maxRow && rowW > 0) {
                    totalW = Math.max(totalW, rowW);
                    totalH += rowH + getVgap();
                    rowW = 0;
                    rowH = 0;
                }
                if (rowW > 0) {
                    rowW += getHgap();
                }
                rowW += d.width;
                rowH = Math.max(rowH, d.height);
            }
            totalW = Math.max(totalW, rowW);
            totalH += rowH;
            return new Dimension(totalW + sideSpace, totalH + in.top + in.bottom + getVgap() * 2);
        }
    }
}
