package com.vjstb.ledscheme.ui;

import java.text.ParseException;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * Разрешает вводить в числовой {@link JSpinner} не только готовое число, но и
 * простое арифметическое выражение (см. {@link MathExpr}) — например «128*40»
 * вместо счёта в уме, для полей вроде числа кабинетов/оффсетов/разрешения.
 * Оборачивает СУЩЕСТВУЮЩИЙ {@link NumberFormatter} спиннера (не подменяет
 * поведение при потере фокуса своим слушателем — иначе штатный ревёрт формы
 * при некорректном тексте срабатывает раньше и текст не доходит до вычисления).
 */
public final class MathFields {

    private MathFields() {
    }

    public static void enableExpressions(JSpinner spinner) {
        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            return;
        }
        JFormattedTextField field = editor.getTextField();
        if (!(field.getFormatter() instanceof NumberFormatter original)) {
            return;
        }
        NumberFormatter wrapped = new NumberFormatter((java.text.NumberFormat) original.getFormat()) {
            @Override
            public Object stringToValue(String text) throws ParseException {
                try {
                    return super.stringToValue(text);
                } catch (ParseException ex) {
                    Double value = MathExpr.tryEval(text);
                    if (value == null) {
                        throw ex;
                    }
                    Object converted = convert(value);
                    Comparable<Object> min = uncheckedComparable(getMinimum());
                    Comparable<Object> max = uncheckedComparable(getMaximum());
                    if ((min != null && min.compareTo(converted) > 0) || (max != null && max.compareTo(converted) < 0)) {
                        throw ex;
                    }
                    return converted;
                }
            }

            private Object convert(double value) {
                Class<?> type = getValueClass() != null ? getValueClass() : Double.class;
                if (type == Integer.class) {
                    return (int) Math.round(value);
                }
                if (type == Long.class) {
                    return Math.round(value);
                }
                if (type == Float.class) {
                    return (float) value;
                }
                return value;
            }

            @SuppressWarnings("unchecked")
            private Comparable<Object> uncheckedComparable(Object o) {
                return (Comparable<Object>) o;
            }
        };
        wrapped.setValueClass(original.getValueClass());
        wrapped.setMinimum(original.getMinimum());
        wrapped.setMaximum(original.getMaximum());
        wrapped.setAllowsInvalid(original.getAllowsInvalid());
        wrapped.setCommitsOnValidEdit(original.getCommitsOnValidEdit());
        field.setFormatterFactory(new DefaultFormatterFactory(wrapped));
    }
}
