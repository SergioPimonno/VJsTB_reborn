package com.vjstb.ledscheme.ui;

import javax.swing.JTextField;

/**
 * Разбор необязательных числовых полей диалогов библиотеки: пустое поле — «не указано, не проверять»
 * ({@code null}), а НЕ ноль. Для приёмных карт и блоков питания это принципиально: ноль в «длине шлейфа»
 * означал бы «ни один модуль не достанет» и заливал бы экран предупреждениями, тогда как пустое поле
 * проверку просто отключает (docs/receiver-cards/PLAN.md, раздел 4.1). Поэтому вместо
 * {@code JSpinner} (у него всегда есть значение) здесь текстовые поля.
 */
final class OptionalNumberFields {

    private OptionalNumberFields() {
    }

    /** Текст для поля: число без лишнего «.0», {@code null} — пустая строка. */
    static String text(Number v) {
        if (v == null) {
            return "";
        }
        double d = v.doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) Math.rint(d)) : String.valueOf(d);
    }

    /** Неотрицательное число или {@code null} для пустого поля; иначе {@link IllegalArgumentException}
     *  с текстом для пользователя. */
    static Double parse(JTextField field, String what) {
        String t = field.getText().trim().replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        double d;
        try {
            d = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + ": «" + field.getText().trim() + "» — не число");
        }
        if (!Double.isFinite(d) || d < 0) {
            throw new IllegalArgumentException(what + ": число не может быть отрицательным");
        }
        return d;
    }

    /** Неотрицательное целое или {@code null} для пустого поля. */
    static Integer parseInt(JTextField field, String what) {
        Double d = parse(field, what);
        if (d == null) {
            return null;
        }
        if (d != Math.rint(d) || d > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + ": нужно целое число, а не «" + field.getText().trim() + "»");
        }
        return (int) Math.rint(d);
    }
}
