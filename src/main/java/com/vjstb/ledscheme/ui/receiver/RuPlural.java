package com.vjstb.ledscheme.ui.receiver;

/** Склонение существительных после числа («1 карта», «3 карты», «5 карт») — для сообщений видов и подтверждений. */
public final class RuPlural {

    private RuPlural() {
    }

    /** Форма слова для {@code n}: {@code one} (1, 21…), {@code few} (2–4, 22–24…), {@code many} (0, 5–20, 25…). */
    public static String of(long n, String one, String few, String many) {
        long a = Math.abs(n) % 10;
        long b = Math.abs(n) % 100;
        if (a == 1 && b != 11) {
            return one;
        }
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) {
            return few;
        }
        return many;
    }

    /** «3 карты». */
    public static String count(long n, String one, String few, String many) {
        return n + " " + of(n, one, few, many);
    }
}
