package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PhysicalPart;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Окно «Разбить на блоки»: начинается с одной коробки позиции, блоки добавляются, дублируются и убираются. */
class RoomPartsDialogTest {

    private static JButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container sub) {
                JButton f = button(sub, text);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static JList<?> list(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JList<?> l) {
                return l;
            }
            if (c instanceof Container sub) {
                JList<?> f = list(sub);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    @Test
    void startsFromTheBoxOfTheItemAndEditsBlocks() throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            RoomPartsDialog d = RoomPartsDialog.create(null, null,
                    new PhysicalPart("Корпус", 600, 500, 140, 0, 0, 0, 8.0));
            JList<?> l = list(d.getContentPane());
            assertEquals(1, l.getModel().getSize(), "начало — одна коробка позиции");
            button(d.getContentPane(), "+ Колонна").doClick();
            button(d.getContentPane(), "+ Основание").doClick();
            assertEquals(3, l.getModel().getSize());
            button(d.getContentPane(), "Дублировать").doClick();
            assertEquals(4, l.getModel().getSize());
            button(d.getContentPane(), "Убрать").doClick();
            assertEquals(3, l.getModel().getSize());
            button(d.getContentPane(), "Сохранить").doClick();
            assertEquals(3, d.result().size());
            assertEquals("Корпус", d.result().get(0).getName());
            d.dispose();
        });
    }

    @Test
    void oneBoxButtonReturnsAnEmptyListAndCancelReturnsNull() throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            RoomPartsDialog d = RoomPartsDialog.create(null,
                    java.util.List.of(new PhysicalPart("Панель", 1000, 70, 600, 0, 0, 0, null)), null);
            button(d.getContentPane(), "Одной коробкой").doClick();
            assertTrue(d.result().isEmpty(), "пустой список — вернуть одну коробку");
            d.dispose();
            RoomPartsDialog c = RoomPartsDialog.create(null, null, new PhysicalPart("Корпус", 1, 1, 1, 0, 0, 0, null));
            button(c.getContentPane(), "Отмена").doClick();
            assertNull(c.result());
            c.dispose();
        });
    }
}
