package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.ui.Palette;
import javax.swing.JTextArea;

/** Мелкий поясняющий текст с переносом строк в цвете приглушённого текста приложения (подсказки под рабочей зоной). */
final class RoomText {

    private RoomText() {
    }

    static JTextArea hint(String text) {
        JTextArea a = new JTextArea(text);
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
