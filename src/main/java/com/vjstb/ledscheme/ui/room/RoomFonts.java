package com.vjstb.ledscheme.ui.room;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

/**
 * Кэш производных шрифтов. Отрисовка плана и стоек подписывает десятки объектов на каждый кадр, а
 * {@code Font.deriveFont} при каждом вызове создаёт новый объект и ищет метрики заново — при перетаскивании это заметная
 * доля кадра. Кэш не даёт расти без предела: при переполнении сбрасывается целиком.
 */
final class RoomFonts {

    private record Key(Font base, int style, float size) {
    }

    private static final Map<Key, Font> CACHE = new HashMap<>();

    private RoomFonts() {
    }

    static Font derive(Font base, int style, float size) {
        Key k = new Key(base, style, size);
        Font f = CACHE.get(k);
        if (f == null) {
            if (CACHE.size() > 256) {
                CACHE.clear();
            }
            f = base.deriveFont(style, size);
            CACHE.put(k, f);
        }
        return f;
    }
}
