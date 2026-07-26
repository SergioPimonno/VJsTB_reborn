package com.vjstb.ledscheme.ui;

/**
 * Простой вычислитель арифметических выражений (+ - * / скобки, унарный минус,
 * дробные числа с точкой ИЛИ запятой) — используется в числовых полях вроде
 * разрешения/оффсетов/размеров, куда удобно ввести «128*40» вместо счёта в уме
 * (запрос пользователя: математические выражения в полях подобного типа).
 */
public final class MathExpr {

    private final String s;
    private int pos;

    private MathExpr(String s) {
        this.s = s;
    }

    /** Пытается разобрать текст как выражение; null — если это не похоже на
     *  корректное число/выражение (лишние символы, незакрытая скобка и т.п.). */
    public static Double tryEval(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            MathExpr e = new MathExpr(t.replace(',', '.'));
            double v = e.parseExpr();
            e.skipSpaces();
            if (e.pos != e.s.length()) {
                return null;
            }
            return v;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Как {@link #tryEval}, но бросает NumberFormatException при неудаче — для
     *  дроп-ин замены существующих {@code Double.parseDouble(...)} вызовов. */
    public static double eval(String text) {
        Double v = tryEval(text);
        if (v == null) {
            throw new NumberFormatException("Не удалось разобрать выражение: " + text);
        }
        return v;
    }

    private double parseExpr() {
        double v = parseTerm();
        while (true) {
            skipSpaces();
            if (peek('+')) {
                pos++;
                v += parseTerm();
            } else if (peek('-')) {
                pos++;
                v -= parseTerm();
            } else {
                break;
            }
        }
        return v;
    }

    private double parseTerm() {
        double v = parseFactor();
        while (true) {
            skipSpaces();
            if (peek('*')) {
                pos++;
                v *= parseFactor();
            } else if (peek('/')) {
                pos++;
                double d = parseFactor();
                if (d == 0) {
                    throw new ArithmeticException("Деление на ноль");
                }
                v /= d;
            } else {
                break;
            }
        }
        return v;
    }

    private double parseFactor() {
        skipSpaces();
        if (peek('+')) {
            pos++;
            return parseFactor();
        }
        if (peek('-')) {
            pos++;
            return -parseFactor();
        }
        if (peek('(')) {
            pos++;
            double v = parseExpr();
            skipSpaces();
            if (!peek(')')) {
                throw new IllegalArgumentException("Не хватает закрывающей скобки");
            }
            pos++;
            return v;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipSpaces();
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Ожидалось число на позиции " + pos);
        }
        return Double.parseDouble(s.substring(start, pos));
    }

    private boolean peek(char c) {
        return pos < s.length() && s.charAt(pos) == c;
    }

    private void skipSpaces() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }
}
