package com.vjstb.ledscheme.service.schemalayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Разносит СОВПАДАЮЩИЕ (коллинеарные и перекрывающиеся по диапазону) участки РАЗНЫХ
 * связей на небольшой канал, чтобы они не сливались в одну линию на экране (docs/
 * schema-ports-rework/PLAN.md, задача T4.2/§2.5) — иначе на схеме, где несколько
 * {@link OrthogonalRouter} маршрутов идут через один и тот же "коридор" между
 * препятствиями, их не отличить друг от друга. Концы отрезков НА ГНЁЗДАХ (первый и
 * последний отрезок каждого маршрута — усы {@link OrthogonalRouter}, обязаны остаться
 * перпендикулярны стороне пина) не трогаются вовсе — разносятся только ВНУТРЕННИЕ
 * отрезки маршрута.
 *
 * <p>Связи одного пучка (D8, задача T4.3) сюда не передаются вовсе — у них общий
 * ствол от точки слияния до гнезда, который не должен разноситься сам от себя;
 * вызывающий код ({@code SchemaCanvasPanel}/будущая T4.4-интеграция) прогоняет через
 * {@link #nudge} только "хвосты" до точки слияния, если раскладка их вообще даёт.
 *
 * <p><b>Как:</b> внутренние отрезки группируются по прямой, на которой лежат
 * (ориентация + общая координата, с допуском {@link #EPS}); внутри группы, если хотя
 * бы у ДВУХ РАЗНЫХ маршрутов есть перекрывающийся по диапазону отрезок, каждому
 * ЗАДЕЙСТВОВАННОМУ В ГРУППЕ маршруту достаётся свой канал (кратный {@link
 * #LANE_STEP}, симметрично вокруг исходной линии) — отрезок группы у этого маршрута
 * заменяется на "уступ": из отрезка {@code A-B} получаются точки {@code A, A', B', B},
 * где {@code A'}/{@code B'} — проекции {@code A}/{@code B} в канал; сосед по маршруту
 * ДО {@code A} и ПОСЛЕ {@code B} остаётся на месте, стык — новый перпендикулярный
 * отрезок ({@code A-A'}), а не разрыв/диагональ.
 */
public final class LaneNudger {

    private LaneNudger() {
    }

    private static final double LANE_STEP = 8;
    private static final double EPS = 0.5;

    private record Seg(int routeIdx, int pointIdx, boolean horizontal, double line, double from, double to) {
    }

    /** @param routes один маршрут (список точек, как из {@link OrthogonalRouter.RouteResult#points()})
     *                на связь, В ТОМ ЖЕ ПОРЯДКЕ, что и связи вызывающего кода — индекс
     *                в списке служит идентичностью "разных связей" для группировки.
     *                Маршруты короче 4 точек (меньше одного ВНУТРЕННЕГО отрезка между
     *                усами) не дают внутренних отрезков — разносить нечего. */
    public static List<List<double[]>> nudge(List<List<double[]>> routes) {
        Map<String, List<Seg>> groups = new LinkedHashMap<>();
        for (int r = 0; r < routes.size(); r++) {
            List<double[]> pts = routes.get(r);
            // Внутренние отрезки — все, КРОМЕ первого (ус у источника) и последнего
            // (ус у приёмника): начало отрезка — индексы точек 1..pts.size()-3.
            for (int i = 1; i + 2 < pts.size(); i++) {
                double[] a = pts.get(i), b = pts.get(i + 1);
                boolean horizontal = Math.abs(a[1] - b[1]) < 1e-6;
                boolean vertical = Math.abs(a[0] - b[0]) < 1e-6;
                if (!horizontal && !vertical) {
                    continue;
                }
                double line = horizontal ? a[1] : a[0];
                double from = horizontal ? Math.min(a[0], b[0]) : Math.min(a[1], b[1]);
                double to = horizontal ? Math.max(a[0], b[0]) : Math.max(a[1], b[1]);
                String key = (horizontal ? "H:" : "V:") + Math.round(line / EPS);
                groups.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Seg(r, i, horizontal, line, from, to));
            }
        }

        // offset, ключ по (routeIdx, pointIdx исходного отрезка) — считаем ДО перестройки
        // маршрутов, т.к. индексы точек относятся к ИСХОДНЫМ спискам.
        Map<Long, Double> offsetBySegment = new LinkedHashMap<>();
        for (List<Seg> group : groups.values()) {
            TreeSet<Integer> distinctRoutes = new TreeSet<>();
            for (Seg s : group) {
                distinctRoutes.add(s.routeIdx());
            }
            if (distinctRoutes.size() < 2 || !hasCrossRouteOverlap(group)) {
                continue;
            }
            List<Integer> laneOrder = new ArrayList<>(distinctRoutes);
            Map<Integer, Double> offsetByRoute = new LinkedHashMap<>();
            for (int k = 0; k < laneOrder.size(); k++) {
                offsetByRoute.put(laneOrder.get(k), (k - (laneOrder.size() - 1) / 2.0) * LANE_STEP);
            }
            for (Seg s : group) {
                double offset = offsetByRoute.get(s.routeIdx());
                if (Math.abs(offset) >= EPS) {
                    offsetBySegment.put(segKey(s.routeIdx(), s.pointIdx()), offset);
                }
            }
        }

        List<List<double[]>> out = new ArrayList<>();
        for (int r = 0; r < routes.size(); r++) {
            out.add(rebuildRoute(routes.get(r), r, offsetBySegment));
        }
        return out;
    }

    private static long segKey(int routeIdx, int pointIdx) {
        return ((long) routeIdx << 32) | (pointIdx & 0xffffffffL);
    }

    /** Хотя бы одна пара отрезков из РАЗНЫХ маршрутов с перекрывающимся диапазоном
     *  (не просто соприкасающимся — {@link #EPS} отсекает касание торцами). */
    private static boolean hasCrossRouteOverlap(List<Seg> group) {
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                Seg a = group.get(i), b = group.get(j);
                if (a.routeIdx() == b.routeIdx()) {
                    continue;
                }
                double overlap = Math.min(a.to(), b.to()) - Math.max(a.from(), b.from());
                if (overlap > EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Строит новый маршрут: точки без изменений, кроме отрезков, у которых в {@code
     *  offsetBySegment} есть ключ (routeIdx этого маршрута, индекс начала отрезка) —
     *  такой отрезок {@code A-B} превращается в {@code A, A', B', B} со сдвигом
     *  перпендикулярно направлению отрезка на {@code offset}. */
    private static List<double[]> rebuildRoute(List<double[]> original, int routeIdx,
                                                Map<Long, Double> offsetBySegment) {
        List<double[]> out = new ArrayList<>();
        out.add(original.get(0).clone());
        for (int i = 0; i + 1 < original.size(); i++) {
            Double offset = offsetBySegment.get(segKey(routeIdx, i));
            double[] a = original.get(i);
            double[] b = original.get(i + 1);
            if (offset != null) {
                boolean horizontal = Math.abs(a[1] - b[1]) < 1e-6;
                double[] aPrime = horizontal ? new double[]{a[0], a[1] + offset} : new double[]{a[0] + offset, a[1]};
                double[] bPrime = horizontal ? new double[]{b[0], b[1] + offset} : new double[]{b[0] + offset, b[1]};
                out.add(aPrime);
                out.add(bPrime);
            }
            out.add(b.clone());
        }
        return out;
    }
}
