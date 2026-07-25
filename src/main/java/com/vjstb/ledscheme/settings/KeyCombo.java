package com.vjstb.ledscheme.settings;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Назначаемая пользователем горячая клавиша действия (см. ui.HotkeysDialog) —
 * клавиша клавиатуры ИЛИ кнопка мыши, плюс модификаторы
 * Ctrl/Shift/Alt (например, Shift+ЛКМ). Ровно одно из keyCode/mouseButton задано.
 */
public class KeyCombo {

    private Integer keyCode;
    private Integer mouseButton; // MouseEvent.BUTTON1/2/3
    private boolean ctrl;
    private boolean shift;
    private boolean alt;

    public KeyCombo() {
    }

    public static KeyCombo ofKey(int keyCode, boolean ctrl, boolean shift, boolean alt) {
        KeyCombo c = new KeyCombo();
        c.keyCode = keyCode;
        c.ctrl = ctrl;
        c.shift = shift;
        c.alt = alt;
        return c;
    }

    public static KeyCombo ofMouse(int mouseButton, boolean ctrl, boolean shift, boolean alt) {
        KeyCombo c = new KeyCombo();
        c.mouseButton = mouseButton;
        c.ctrl = ctrl;
        c.shift = shift;
        c.alt = alt;
        return c;
    }

    public Integer getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(Integer keyCode) {
        this.keyCode = keyCode;
    }

    public Integer getMouseButton() {
        return mouseButton;
    }

    public void setMouseButton(Integer mouseButton) {
        this.mouseButton = mouseButton;
    }

    public boolean isCtrl() {
        return ctrl;
    }

    public void setCtrl(boolean ctrl) {
        this.ctrl = ctrl;
    }

    public boolean isShift() {
        return shift;
    }

    public void setShift(boolean shift) {
        this.shift = shift;
    }

    public boolean isAlt() {
        return alt;
    }

    public void setAlt(boolean alt) {
        this.alt = alt;
    }

    public boolean matchesKey(KeyEvent e) {
        return keyCode != null && e.getKeyCode() == keyCode
                && e.isControlDown() == ctrl && e.isShiftDown() == shift && e.isAltDown() == alt;
    }

    public boolean matchesMouse(MouseEvent e, int button) {
        return mouseButton != null && mouseButton == button
                && e.isControlDown() == ctrl && e.isShiftDown() == shift && e.isAltDown() == alt;
    }

    /** Человекочитаемая подпись комбинации для UI, например «Ctrl+Z» или «Shift+ЛКМ». */
    public String label() {
        StringBuilder sb = new StringBuilder();
        if (ctrl) {
            sb.append("Ctrl+");
        }
        if (shift) {
            sb.append("Shift+");
        }
        if (alt) {
            sb.append("Alt+");
        }
        if (keyCode != null) {
            sb.append(KeyEvent.getKeyText(keyCode));
        } else if (mouseButton != null) {
            sb.append(switch (mouseButton) {
                case MouseEvent.BUTTON1 -> "ЛКМ";
                case MouseEvent.BUTTON2 -> "СКМ";
                case MouseEvent.BUTTON3 -> "ПКМ";
                default -> "Кнопка мыши " + mouseButton;
            });
        } else {
            sb.append("—");
        }
        return sb.toString();
    }

    public KeyCombo copy() {
        KeyCombo c = new KeyCombo();
        c.keyCode = keyCode;
        c.mouseButton = mouseButton;
        c.ctrl = ctrl;
        c.shift = shift;
        c.alt = alt;
        return c;
    }
}
