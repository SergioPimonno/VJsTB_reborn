package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.ui.Palette;
import javax.swing.JTextArea;

/** Мелкий поясняющий текст с переносом строк в цвете приглушённого текста приложения (подсказки под рабочей зоной). */
final class RoomText {

    private RoomText() {
    }

    static JTextArea hint(String text) {
        return hint(text, 0);
    }

    /**
     * Подсказка, у которой высота не меньше {@code minLines} строк: от длины текста высота не прыгает, а значит не
     * прыгает и рабочая зона над ней (план пересчитывал масштаб при каждой смене подсказки).
     */
    static JTextArea hint(String text, int minLines) {
        JTextArea a = new JTextArea(text) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension d = super.getPreferredSize();
                if (minLines > 0) {
                    java.awt.Insets in = getInsets();
                    d.height = Math.max(d.height, getFontMetrics(getFont()).getHeight() * minLines + in.top + in.bottom);
                }
                return d;
            }
        };
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setBorder(null);
        a.setForeground(Palette.MUTED);
        a.setFont(a.getFont().deriveFont(RoomTheme.fontSize(11)));
        return a;
    }
}
