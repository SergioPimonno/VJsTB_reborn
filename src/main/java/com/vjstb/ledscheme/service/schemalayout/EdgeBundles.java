package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.NodeSide;
import java.util.List;

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

    /** Допуск совпадения точек маршрутов в {@link #commonPrefix} — сознательно НЕ
     *  математический (1e-6), а в пикселях экрана: {@code OrthogonalRouter} считает
     *  маршрут каждой связи пучка НЕЗАВИСИМЫМ запуском поиска по сетке, точка
     *  поворота у которого зависит от границ узла-ПОЛУЧАТЕЛЯ этой конкретной связи
     *  (см. {@code SchemaCanvasPanel#autoRoutePoints}). На реальном проекте узлы
     *  часто не выровнены по X пиксель-в-пиксель (авто-подгонка ширины блока под
     *  чуть разный текст, см. {@code AppModel#autoFitNodeToPorts}) — воспроизведено
     *  на проекте пользователя "Циммер": 4 получателя на X = 573/573/575/576,
     *  из-за чего роутер у двух связей поворачивает на X=551, у других двух на
     *  X=549 — 2px расхождения при визуально одной и той же линии. Значение
     *  подобрано с запасом над этим измеренным случаем (баг-репорт пользователя
     *  2026-09-30: чип общей подписи стоял прямо у гнезда вместо реальной
     *  развилки — прежний допуск 1.5 такой разницы уже не поглощал). */
    private static final double EPS = 4.0;

    /** Общий начальный отрезок (полилиния) нескольких маршрутов, идущих из ОДНОГО
     *  общего гнезда, — все точки, в которых маршруты ЕЩЁ совпадают, по порядку
     *  (запрос пользователя 2026-09-30: индикацию шины — синюю линию/пунктир —
     *  видно до точки первого отсечения, не только точкой, а как реальную ломаную,
     *  если между гнездом и расхождением есть общий излом). В отличие от {@link
     *  #bundleFor} — не геометрическая конструкция по стороне гнезда, а РЕАЛЬНОЕ
     *  сравнение уже посчитанных маршрутов (после орто-трассировки/изломов),
     *  поэтому корректна при подходе под любым углом (там, где {@link #bundleFor}
     *  сознательно не используется самим холстом — см. его javadoc и {@code
     *  SchemaCanvasPanel#drawEdgeBundleMarkers}).
     *
     * @param routes маршруты (полилинии) всех связей пучка — КАЖДЫЙ должен
     *               начинаться (индекс 0) В ОДНОМ И ТОМ ЖЕ гнезде (вызывающий код
     *               разворачивает маршрут, если связь заходит в это гнездо с
     *               ДРУГОГО конца — см. {@code SchemaCanvasPanel#normalizedRouteFromNode}).
     * @return общий префикс маршрутов, минимум одна точка (само гнездо, index 0),
     *         если хотя бы одна пара уже расходится на первом же шаге; пустой
     *         список, если {@code routes} пуст или первый маршрут пуст.
     */
    public static List<double[]> commonPrefix(List<List<double[]>> routes) {
        if (routes.isEmpty() || routes.get(0).isEmpty()) {
            return List.of();
        }
        int minLen = Integer.MAX_VALUE;
        for (List<double[]> r : routes) {
            minLen = Math.min(minLen, r.size());
        }
        List<double[]> prefix = new java.util.ArrayList<>();
        for (int i = 0; i < minLen; i++) {
            double[] p0 = routes.get(0).get(i);
            boolean allSame = true;
            for (List<double[]> r : routes) {
                double[] p = r.get(i);
                if (Math.abs(p[0] - p0[0]) > EPS || Math.abs(p[1] - p0[1]) > EPS) {
                    allSame = false;
                    break;
                }
            }
            if (!allSame) {
                break;
            }
            prefix.add(p0);
        }
        return prefix;
    }

    /** Последняя точка {@link #commonPrefix} — где РЕАЛЬНО расходятся маршруты
     *  пучка (см. его javadoc). Само гнездо (index 0), если список маршрутов пуст. */
    public static double[] divergencePoint(List<List<double[]>> routes) {
        List<double[]> prefix = commonPrefix(routes);
        if (!prefix.isEmpty()) {
            return prefix.get(prefix.size() - 1);
        }
        return routes.isEmpty() || routes.get(0).isEmpty() ? new double[]{0, 0} : routes.get(0).get(0);
    }
}
