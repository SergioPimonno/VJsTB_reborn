package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.NodeSide;

/**
 * Точка слияния и подпись количества для "пучка" — нескольких связей, приходящих в
 * ОДНО свёрнутое гнездо (docs/schema-ports-rework/PLAN.md, задача T4.3, решение D8:
 * "несколько связей в одно свёрнутое гнездо идут общим стволом с подписью
 * количества"). Чистая геометрия без Swing — как рисовать сам пучок (продолжать ли
 * каждую связь индивидуальным маршрутом {@link OrthogonalRouter} до {@link
 * Bundle#mergePoint()}, а от него общим отрезком до гнезда, вместо N перекрывающихся
 * отрезков до самого гнезда) — задача холста (T4.4), здесь только то, что не зависит
 * от способа отрисовки: где ствол начинается/кончается и что написать на подписи.
 *
 * <p>Ствол — короткий отрезок СТРОГО по нормали стороны гнезда (см. {@link
 * OrthogonalRouter#outward}, тот же приём, что и у "уса" отдельной связи в T4.1) —
 * визуально читается как "все линии слились и одним общим отрезком заходят в гнездо",
 * а не как ещё один непонятный залом.
 */
public final class EdgeBundles {

    private EdgeBundles() {
    }

    /** Длина общего ствола — заметно короче обычного "уса" связи ({@link
     *  OrthogonalRouter} STUB=12), чтобы точка слияния не выглядела отдельным
     *  промежуточным блоком, а читалась как часть самого гнезда. */
    public static final double TRUNK_LENGTH = 16;

    /** @param pin         точка гнезда (центр пина на рамке блока).
     *  @param mergePoint  точка слияния — {@code pin + TRUNK_LENGTH} по нормали
     *                     стороны {@code side}; здесь СХОДЯТСЯ индивидуальные
     *                     маршруты всех связей пучка, дальше до {@code pin} —
     *                     ОДИН общий отрезок (ствол) вместо {@code count} разных.
     *  @param count       число связей в пучке (≥ 2 — пучком из одной связи не
     *                     бывает, вызывающий код не должен создавать {@link Bundle}
     *                     для гнезда с одной связью).
     */
    public record Bundle(double[] pin, double[] mergePoint, int count) {

        /** Ствол как отрезок — из {@link #mergePoint()} в {@link #pin()} (в эту
         *  сторону "текут" все связи пучка, визуально сходясь у гнезда). */
        public double[][] trunk() {
            return new double[][]{mergePoint, pin};
        }

        /** Подпись у ствола — "×N" (тот же идиом умножения, что у названий
         *  свёрнутых групп "N×Тип", см. {@code SchemaCanvasPanel#pinLabel}). */
        public String label() {
            return "×" + count;
        }
    }

    /** {@code count} должно быть ≥ 2 — иначе пучок не нужен (одна связь просто
     *  идёт прямо в гнездо, без общего ствола, см. класс-javadoc). */
    public static Bundle bundleFor(double pinX, double pinY, NodeSide side, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("Пучок из " + count + " связей не имеет смысла (нужно ≥ 2)");
        }
        double[] dir = OrthogonalRouter.outward(side);
        double[] pin = {pinX, pinY};
        double[] merge = {pinX + dir[0] * TRUNK_LENGTH, pinY + dir[1] * TRUNK_LENGTH};
        return new Bundle(pin, merge, count);
    }
}
