package com.vjstb.ledscheme.ui;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.text.ParseException;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

/**
 * Значение числового поля меняется колесом мыши — как в 3D-пакетах (запрос пользователя 2026-10-22: «если
 * человек нажал в поле и начал крутить колесико, то параметр будет меняться — очень удобная фича»). Условие —
 * в поле стоит курсор ввода (кликнули в него): колесо над неактивным полем по-прежнему прокручивает
 * панель. Вверх — больше, вниз — меньше, на шаг модели поля; {@code Shift} — шаг ×10, {@code Ctrl} — ×0,1.
 * Значение зажимается границами модели; для целочисленной модели остаётся целым; необработанный ввод
 * в поле (напечатали, не нажав Enter) применяется перед шагом.
 *
 * <p>{@link #installGlobally()} подписывается на появление ЛЮБОГО {@code JSpinner} в приложении (событие
 * иерархии) и ставит ему обработчик один раз — отдельно по полям ходить не нужно, работает и в диалогах.
 */
public final class SpinnerWheel {

    private static final String INSTALLED = "spinnerWheel.installed";
    private static boolean globalInstalled;

    private SpinnerWheel() {
    }

    /** Шаг колеса для поля {@code spinner}: {@code ticks} щелчков вверх (положительное — увеличить),
     *  {@code modifiers} — {@link InputEvent#getModifiersEx()}. Возвращает, изменилось ли значение. */
    public static boolean adjust(JSpinner spinner, int ticks, int modifiers) {
        if (ticks == 0 || !spinner.isEnabled()) {
            return false;
        }
        try {
            spinner.commitEdit();
        } catch (ParseException ignore) {
            // недописанный ввод — берём последнее принятое значение
        }
        SpinnerModel model = spinner.getModel();
        Object before = model.getValue();
        if (model instanceof SpinnerNumberModel nm) {
            double step = nm.getStepSize().doubleValue();
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
                step *= 10;
            }
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                step *= 0.1;
            }
            double value = nm.getNumber().doubleValue() + ticks * step;
            if (nm.getMinimum() instanceof Number min) {
                value = Math.max(value, min.doubleValue());
            }
            if (nm.getMaximum() instanceof Number max) {
                value = Math.min(value, max.doubleValue());
            }
            // убираем накопление погрешности вида 0.30000000000000004
            value = Math.round(value * 1e6) / 1e6;
            nm.setValue(nm.getValue() instanceof Integer ? (Object) (int) Math.round(value) : (Object) value);
        } else {
            for (int i = 0; i < Math.abs(ticks); i++) {
                Object next = ticks > 0 ? model.getNextValue() : model.getPreviousValue();
                if (next == null) {
                    break;
                }
                model.setValue(next);
            }
        }
        return !before.equals(model.getValue());
    }

    /** Ставит поле обработчик колеса (повторный вызов — без эффекта). */
    public static void install(JSpinner spinner) {
        if (Boolean.TRUE.equals(spinner.getClientProperty(INSTALLED))) {
            return;
        }
        spinner.putClientProperty(INSTALLED, Boolean.TRUE);
        java.awt.event.MouseWheelListener listener = e -> {
            if (inFocus(spinner)) {
                // вращение «от себя» (отрицательное) увеличивает значение
                adjust(spinner, -e.getWheelRotation(), e.getModifiersEx());
                e.consume();
            }
        };
        spinner.addMouseWheelListener(listener);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().addMouseWheelListener(listener);
            editor.addMouseWheelListener(listener);
        }
    }

    private static boolean inFocus(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor && editor.getTextField().hasFocus()) {
            return true;
        }
        return spinner.hasFocus();
    }

    /** Все поля приложения: как только {@code JSpinner} появляется в иерархии, ему ставится обработчик. */
    public static synchronized void installGlobally() {
        if (globalInstalled) {
            return;
        }
        globalInstalled = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof HierarchyEvent he && he.getComponent() instanceof JSpinner spinner) {
                install(spinner);
            }
        }, AWTEvent.HIERARCHY_EVENT_MASK);
    }

    /** Установка «задним числом» для уже построенного дерева компонентов. */
    public static void installAll(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof JSpinner s) {
                install(s);
            }
            if (c instanceof java.awt.Container inner) {
                installAll(inner);
            }
        }
    }

    /** Для тестов: событие колеса, как его присылает Swing. */
    static MouseWheelEvent wheelEvent(java.awt.Component source, int rotation, int modifiersEx) {
        return new MouseWheelEvent(source, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiersEx, 1, 1,
                0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }
}
