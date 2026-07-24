package com.vjstb.ledscheme.settings;

import java.awt.event.KeyEvent;

/**
 * Реестр переназначаемых действий (горячих клавиш) — id для хранения в
 * {@link UserProfile#getKeyBindings()}, подпись для UI и биндинг по умолчанию
 * (совпадает с прежним жёстко заданным поведением, чтобы для всех, кто ничего
 * не переназначал, ничего не изменилось).
 */
public enum HotkeyAction {
    UNDO("undo", "Отменить последнее действие", KeyCombo.ofKey(KeyEvent.VK_Z, true, false, false)),
    FINISH_CHAIN("finishChain", "Завершить построение цепочки", KeyCombo.ofKey(KeyEvent.VK_ESCAPE, false, false, false)),
    MOVE_UP("moveUp", "Курсор строящейся цепочки: вверх", KeyCombo.ofKey(KeyEvent.VK_UP, false, false, false)),
    MOVE_DOWN("moveDown", "Курсор строящейся цепочки: вниз", KeyCombo.ofKey(KeyEvent.VK_DOWN, false, false, false)),
    MOVE_LEFT("moveLeft", "Курсор строящейся цепочки: влево", KeyCombo.ofKey(KeyEvent.VK_LEFT, false, false, false)),
    MOVE_RIGHT("moveRight", "Курсор строящейся цепочки: вправо", KeyCombo.ofKey(KeyEvent.VK_RIGHT, false, false, false)),
    TOGGLE_HIDDEN("toggleHidden", "Скрыть/показать кабинет под курсором",
            KeyCombo.ofKey(KeyEvent.VK_DELETE, false, false, false));

    private final String id;
    private final String label;
    private final KeyCombo defaultCombo;

    HotkeyAction(String id, String label, KeyCombo defaultCombo) {
        this.id = id;
        this.label = label;
        this.defaultCombo = defaultCombo;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public KeyCombo getDefaultCombo() {
        return defaultCombo;
    }

    public static HotkeyAction byId(String id) {
        for (HotkeyAction a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
