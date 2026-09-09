package com.vjstb.ledscheme.ui;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Геометрия «мостиков» — обходов в местах пересечения соединительных линий общей
 * схемы, как принято в ГОСТ (см. {@code settings.WireHopStyle}).
 *
 * <p>Вынесено из {@link SchemaCanvasPanel} отдельным классом только ради
 * юнит-тестов: панель Swing в headless-прогоне не поднять, а вся содержательная
 * математика (пересечение отрезков, форма дуги, слияние близких обходов) чистая.
 * Приём тот же, что у соседнего {@link SnapMath}. Форма дуги описана локальным
 * {@link ArcShape}, чтобы геометрия не зависела от пакета настроек —
 * {@code SchemaCanvasPanel} отображает {@code WireHopStyle} в него одно в одно.</p>
 */
public final class WireHopGeometry {

    private WireHopGeometry() {
    }

    /** Форма дуги-обхода. {@link #CUBIC} — полукруглая (одна кубическая Безье,
     *  компактная, но у широкого слитого пролёта просвет тает к краям);
     *  {@link #FLAT_TOP} — «усечённая дуга»: подъём на радиус, горизонтальная
     *  полка на высоте радиуса, спуск — просвет держится по всей ширине пролёта. */
    public enum ArcShape {
        CUBIC,
        FLAT_TOP
    }

    /** Слитый пролёт дуги на сегменте {@code segIndex} ломаной маршрута:
     *  {@code [alongStart, alongEnd]} — расстояния вдоль сегмента от его начала
     *  (в логических px). Один пролёт может накрывать несколько близких
     *  пересечений сразу — см. {@link #hopSpans}. Границы пролёта и есть те две
     *  «неинтерактивные точки излома», в начале/конце которых рисуются стрелки
     *  направления (на самой дуге — нет). */
    public record HopSpan(int segIndex, double alongStart, double alongEnd) {
    }

    /** Расширенная ломаная для отрисовки стрелок: {@code points} — исходные точки
     *  маршрута плюс по две точки на границах каждого пролёта дуги;
     *  {@code arcSegment[i]} — под-сегмент {@code points[i]..points[i+1]} лежит
     *  ПОД дугой, стрелку направления на нём рисовать не надо. Длина
     *  {@code arcSegment} равна {@code points.size() - 1}. */
    public record RenderRoute(List<double[]> points, boolean[] arcSegment) {
    }

    /** Ниже этого синуса угла между сегментами считаем их «почти параллельными»:
     *  точка пересечения вырожденная (или сегменты идут одним коридором — см.
     *  {@code SchemaCanvasPanel#edgeSlot}), мостик не ставим. ≈11.5°. */
    private static final double MIN_ANGLE_SIN = 0.2;

    /** Пересечение ближе этого расстояния (в логических px — тех же единицах, что
     *  координаты маршрута) к концу ЛЮБОГО из двух сегментов не даёт мостика: там
     *  либо общий узел/гнездо (реальное соединение, а не перекрёст), либо излом
     *  ломаной, у которого дуга смотрелась бы кашей. */
    public static final double END_CLEARANCE = 6.0;

    /** Соседние обходы на одном сегменте, чьи центры ближе {@code 2*r + MERGE_GAP},
     *  сливаются в один расширенный пролёт: две близкие линии тогда проходят под
     *  общей дугой, а не остаются без обхода (второй просто не влезал между первой
     *  дугой и собой). Небольшой зазор сверх диаметра — чтобы не оставлять
     *  почти-нулевой плоский промежуток между двумя дугами. */
    public static final double MERGE_GAP = 4.0;

    /** Параметры точки пересечения отрезков {@code a1->a2} и {@code b1->b2} как
     *  {@code {ta, tb}} (доля вдоль каждого, 0..1), либо {@code null} — если
     *  отрезки не пересекаются, почти параллельны, или пересечение слишком близко
     *  к концу одного из них (см. {@link #END_CLEARANCE}). */
    public static double[] crossParams(double ax1, double ay1, double ax2, double ay2,
                                       double bx1, double by1, double bx2, double by2) {
        double rx = ax2 - ax1, ry = ay2 - ay1;
        double sx = bx2 - bx1, sy = by2 - by1;
        double rLen = Math.hypot(rx, ry), sLen = Math.hypot(sx, sy);
        if (rLen < 1e-6 || sLen < 1e-6) {
            return null;
        }
        double denom = rx * sy - ry * sx;
        if (Math.abs(denom) / (rLen * sLen) < MIN_ANGLE_SIN) {
            return null; // почти параллельны
        }
        double qx = bx1 - ax1, qy = by1 - ay1;
        double ta = (qx * sy - qy * sx) / denom;
        double tb = (qx * ry - qy * rx) / denom;
        if (ta <= 0 || ta >= 1 || tb <= 0 || tb >= 1) {
            return null;
        }
        if (ta * rLen < END_CLEARANCE || (1 - ta) * rLen < END_CLEARANCE) {
            return null;
        }
        if (tb * sLen < END_CLEARANCE || (1 - tb) * sLen < END_CLEARANCE) {
            return null;
        }
        return new double[]{ta, tb};
    }

    /** Длина сегмента — правило «сверху лежит линия, чей сегмент в точке
     *  пересечения длиннее» (см. {@link #aIsOnTop}). */
    public static double segLen(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    /** {@code true} — при пересечении «сверху» (рисует дугу-обход) лежит сегмент
     *  A: его длина не меньше длины сегмента B. При равенстве — тоже A, это
     *  детерминированный tie-break; вызывающий обязан передавать сегменты пары в
     *  стабильном порядке (напр. по индексу связи), иначе выбор «верхней» будет
     *  дёргаться между кадрами. */
    public static boolean aIsOnTop(double aLen, double bLen) {
        return aLen >= bLen;
    }

    /** Пролёты дуг для ломаной {@code pts} по списку обходов {@code hops}
     *  ({@code {segIndex, t}}, отсортирован по {@code segIndex}, затем по
     *  {@code t}). Обходы у самого края сегмента (одиночная дуга радиуса {@code r}
     *  не влезает) отбрасываются; уцелевшие соседние на одном сегменте сливаются
     *  по правилу {@link #MERGE_GAP}. Результат отсортирован по {@code segIndex},
     *  затем по {@code alongStart}. */
    public static List<HopSpan> hopSpans(List<double[]> pts, List<double[]> hops, double r) {
        List<HopSpan> spans = new ArrayList<>();
        if (pts == null || pts.size() < 2 || hops == null || hops.isEmpty()) {
            return spans;
        }
        int hi = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double ax = pts.get(i)[0], ay = pts.get(i)[1];
            double bx = pts.get(i + 1)[0], by = pts.get(i + 1)[1];
            double len = Math.hypot(bx - ax, by - ay);
            List<Double> along = new ArrayList<>();
            while (hi < hops.size() && (int) hops.get(hi)[0] == i) {
                double a = hops.get(hi)[1] * len;
                hi++;
                if (len < 1e-6 || a - r < 0 || a + r > len) {
                    continue; // у края сегмента — стык/гнездо, одиночная дуга не влезает
                }
                along.add(a);
            }
            while (hi < hops.size() && (int) hops.get(hi)[0] < i) {
                hi++; // хвосты обходов уже пройденных сегментов, если indices разъехались
            }
            if (along.isEmpty()) {
                continue;
            }
            along.sort(Double::compare);
            double groupFirst = along.get(0);
            double groupLast = along.get(0);
            for (int k = 1; k < along.size(); k++) {
                double a = along.get(k);
                if (a - groupLast < 2 * r + MERGE_GAP) {
                    groupLast = a;
                } else {
                    spans.add(makeSpan(i, groupFirst, groupLast, r, len));
                    groupFirst = a;
                    groupLast = a;
                }
            }
            spans.add(makeSpan(i, groupFirst, groupLast, r, len));
        }
        return spans;
    }

    private static HopSpan makeSpan(int seg, double first, double last, double r, double len) {
        double start = Math.max(0, first - r);
        double end = Math.min(len, last + r);
        return new HopSpan(seg, start, end);
    }

    /** Строит путь ломаной {@code pts}, заменяя проход через места обходов
     *  {@code hops} дугами радиуса {@code r} формы {@code shape}. Слишком краевые
     *  и слишком близкие друг к другу обходы обрабатываются как в
     *  {@link #hopSpans} (последние — сливаются в одну расширенную дугу). */
    public static Path2D.Double hoppedPath(List<double[]> pts, List<double[]> hops, double r, ArcShape shape) {
        return hoppedPathFromSpans(pts, hopSpans(pts, hops, r), r, shape);
    }

    /** 3-арг перегрузка — форма {@link ArcShape#CUBIC}, для существующих вызовов
     *  и тестов. */
    public static Path2D.Double hoppedPath(List<double[]> pts, List<double[]> hops, double r) {
        return hoppedPath(pts, hops, r, ArcShape.CUBIC);
    }

    /** Путь ломаной {@code pts} с уже посчитанными пролётами {@code spans}
     *  (отсортированы по {@code segIndex}, затем {@code alongStart}). Дуга формы
     *  {@link ArcShape#CUBIC} — одна кубическая Безье с плечами {@code r*4/3} (на
     *  глаз неотличимо от полукруга при пролёте {@code ≈2r}); формы
     *  {@link ArcShape#FLAT_TOP} — подъём кубикой на высоту {@code r},
     *  горизонтальная полка, спуск кубикой. Дуга выпуклая «вверх» экрана (для
     *  вертикального сегмента — влево). */
    public static Path2D.Double hoppedPathFromSpans(List<double[]> pts, List<HopSpan> spans,
                                                    double r, ArcShape shape) {
        Path2D.Double path = new Path2D.Double();
        if (pts == null || pts.size() < 2) {
            return path;
        }
        path.moveTo(pts.get(0)[0], pts.get(0)[1]);
        int si = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double ax = pts.get(i)[0], ay = pts.get(i)[1];
            double bx = pts.get(i + 1)[0], by = pts.get(i + 1)[1];
            double dx = bx - ax, dy = by - ay;
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) {
                path.lineTo(bx, by);
                continue;
            }
            double ux = dx / len, uy = dy / len;
            // нормаль «вверх экрана» (меньший Y); для вертикального сегмента —
            // влево (меньший X), чтобы сторона обхода не зависела от направления
            // рисования сегмента
            double nx = uy, ny = -ux;
            if (ny > 1e-9 || (Math.abs(ny) <= 1e-9 && nx > 0)) {
                nx = -nx;
                ny = -ny;
            }
            while (si < spans.size() && spans.get(si).segIndex() == i) {
                HopSpan sp = spans.get(si++);
                appendArc(path, ax, ay, ux, uy, nx, ny, sp.alongStart(), sp.alongEnd(), r, shape);
            }
            while (si < spans.size() && spans.get(si).segIndex() < i) {
                si++;
            }
            path.lineTo(bx, by);
        }
        return path;
    }

    private static void appendArc(Path2D.Double path, double ax, double ay,
                                  double ux, double uy, double nx, double ny,
                                  double s, double e, double r, ArcShape shape) {
        double sx = ax + ux * s, sy = ay + uy * s;
        double ex = ax + ux * e, ey = ay + uy * e;
        path.lineTo(sx, sy);
        if (shape == ArcShape.FLAT_TOP) {
            double w = e - s;
            double ramp = Math.min(r, w / 2.0);
            double c = 0.5523; // ≈ kappa для четвертькруга — плечи кубик подъёма/спуска
            double p1x = sx + ux * ramp + nx * r, p1y = sy + uy * ramp + ny * r;
            path.curveTo(sx + nx * r * c, sy + ny * r * c,
                    p1x - ux * ramp * c, p1y - uy * ramp * c,
                    p1x, p1y);
            double p2x = ex - ux * ramp + nx * r, p2y = ey - uy * ramp + ny * r;
            path.lineTo(p2x, p2y);
            path.curveTo(p2x + ux * ramp * c, p2y + uy * ramp * c,
                    ex + nx * r * c, ey + ny * r * c,
                    ex, ey);
        } else {
            double k = r * 4.0 / 3.0;
            path.curveTo(sx + nx * k, sy + ny * k, ex + nx * k, ey + ny * k, ex, ey);
        }
    }

    /** Исходные точки маршрута {@code pts} плюс по две неинтерактивные точки на
     *  границах каждого пролёта {@code spans}, с пометкой под-сегментов, лежащих
     *  под дугой. Стрелку направления рисуют по каждому НЕ помеченному
     *  под-сегменту — так стрелки оказываются до и после дуги, но не на ней. Без
     *  пролётов возвращает {@code pts} как есть (по стрелке на сегмент, как было). */
    public static RenderRoute renderPoints(List<double[]> pts, List<HopSpan> spans) {
        List<double[]> out = new ArrayList<>();
        List<Boolean> arc = new ArrayList<>();
        if (pts == null || pts.isEmpty()) {
            return new RenderRoute(out, new boolean[0]);
        }
        out.add(pts.get(0).clone());
        int si = 0;
        List<HopSpan> safeSpans = spans == null ? List.of() : spans;
        for (int i = 0; i < pts.size() - 1; i++) {
            double ax = pts.get(i)[0], ay = pts.get(i)[1];
            double bx = pts.get(i + 1)[0], by = pts.get(i + 1)[1];
            double len = Math.hypot(bx - ax, by - ay);
            double ux = len < 1e-6 ? 0 : (bx - ax) / len;
            double uy = len < 1e-6 ? 0 : (by - ay) / len;
            while (si < safeSpans.size() && safeSpans.get(si).segIndex() == i) {
                HopSpan sp = safeSpans.get(si++);
                out.add(new double[]{ax + ux * sp.alongStart(), ay + uy * sp.alongStart()});
                arc.add(false); // отрезок до дуги
                out.add(new double[]{ax + ux * sp.alongEnd(), ay + uy * sp.alongEnd()});
                arc.add(true);  // сам под-сегмент дуги — стрелку не рисуем
            }
            while (si < safeSpans.size() && safeSpans.get(si).segIndex() < i) {
                si++;
            }
            out.add(new double[]{bx, by});
            arc.add(false); // отрезок после дуги (или весь сегмент, если дуг не было)
        }
        boolean[] arcArr = new boolean[arc.size()];
        for (int k = 0; k < arcArr.length; k++) {
            arcArr[k] = arc.get(k);
        }
        return new RenderRoute(out, arcArr);
    }
}
