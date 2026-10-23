package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

/**
 * Колесо мыши в числовом поле меняет значение, как в 3D-пакетах (запрос пользователя 2026-10-22): вверх — больше,
 * вниз — меньше, на шаг модели; Shift ×10, Ctrl ×0,1; границы модели, целые модели остаются целыми. Без фокуса
 * в поле колесо поле не трогает (прокручивает панель).
 */
class SpinnerWheelTest {

    @Test
    void ticksStepTheValueUpAndDownWithModifiers() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(100.0, 0.0, 1000.0, 10.0));
        assertTrue(SpinnerWheel.adjust(s, 1, 0));
        assertEquals(110.0, ((Number) s.getValue()).doubleValue(), 1e-9);
        SpinnerWheel.adjust(s, -3, 0);
        assertEquals(80.0, ((Number) s.getValue()).doubleValue(), 1e-9);
        SpinnerWheel.adjust(s, 1, InputEvent.SHIFT_DOWN_MASK);
        assertEquals(180.0, ((Number) s.getValue()).doubleValue(), 1e-9, "Shift — шаг ×10");
        SpinnerWheel.adjust(s, 1, InputEvent.CTRL_DOWN_MASK);
        assertEquals(181.0, ((Number) s.getValue()).doubleValue(), 1e-9, "Ctrl — шаг ×0,1");
    }

    @Test
    void valueIsClampedByTheModelAndIntegerModelsStayIntegers() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(5, 0, 8, 1));
        SpinnerWheel.adjust(s, 10, 0);
        assertEquals(8, s.getValue(), "верхняя граница");
        assertTrue(s.getValue() instanceof Integer);
        SpinnerWheel.adjust(s, -50, 0);
        assertEquals(0, s.getValue(), "нижняя граница");
        assertFalse(SpinnerWheel.adjust(s, -1, 0), "на границе ничего не меняется");
    }

    @Test
    void noFloatingPointDriftAndDisabledFieldsAreIgnored() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 10.0, 0.1));
        for (int i = 0; i < 3; i++) {
            SpinnerWheel.adjust(s, 1, 0);
        }
        assertEquals(0.3, ((Number) s.getValue()).doubleValue(), 1e-12);
        s.setEnabled(false);
        assertFalse(SpinnerWheel.adjust(s, 1, 0));
    }

    @Test
    void wheelOverAFieldWithoutFocusIsLeftAlone() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
        SpinnerWheel.install(s);
        SpinnerWheel.install(s); // повторно — без дублей
        var event = SpinnerWheel.wheelEvent(s, -1, 0);
        for (var l : s.getMouseWheelListeners()) {
            l.mouseWheelMoved(event);
        }
        assertEquals(10.0, ((Number) s.getValue()).doubleValue(), 1e-9, "без курсора ввода в поле — не меняется");
        assertFalse(event.isConsumed(), "событие остаётся панели (прокрутка)");
        assertEquals(1, s.getMouseWheelListeners().length);
    }
}
