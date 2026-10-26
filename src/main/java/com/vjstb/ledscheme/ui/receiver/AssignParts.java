package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.ui.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ListCellRenderer;

/** Мелкие общие детали правых панелей обоих видов: строка типа «имя — параметры», блок «радио + пояснение». */
final class AssignParts {

    private AssignParts() {
    }

    /** Строка списка типов: имя слева, краткие параметры справа серым («12 вых. · 600 мм» / «не указано»). */
    static <T> ListCellRenderer<T> typeRenderer(Function<T, String> name, Function<T, String> params) {
        return new ListCellRenderer<T>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends T> list, T t, int index, boolean sel,
                                                          boolean focus) {
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(true);
                row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                JLabel left = new JLabel(name.apply(t));
                JLabel right = new JLabel(params.apply(t));
                Color fg = sel ? list.getSelectionForeground() : list.getForeground();
                left.setForeground(fg);
                right.setForeground(sel ? fg : Palette.MUTED);
                right.setFont(right.getFont().deriveFont(right.getFont().getSize2D() - 1f));
                row.add(left, BorderLayout.CENTER);
                row.add(right, BorderLayout.EAST);
                row.setBackground(sel ? list.getSelectionBackground() : list.getBackground());
                return row;
            }
        };
    }

    /** Переключатель «Вручную / Автоматически»: жирный заголовок + мелкое пояснение под ним. */
    static JPanel optionRow(JRadioButton radio, String title, String description) {
        radio.setText(title);
        radio.setFont(radio.getFont().deriveFont(Font.BOLD));
        radio.setOpaque(false);
        JLabel d = new JLabel("<html><div style='width:210px'>" + description + "</div></html>");
        d.setForeground(Palette.MUTED);
        d.setFont(d.getFont().deriveFont(d.getFont().getSize2D() - 1f));
        d.setBorder(BorderFactory.createEmptyBorder(0, 24, 2, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(radio, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }
}
