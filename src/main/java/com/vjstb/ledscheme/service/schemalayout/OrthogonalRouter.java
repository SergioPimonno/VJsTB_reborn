package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.NodeSide;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Ортогональная трассировка одной связи между двумя гнёздами (docs/schema-ports-
 * rework/PLAN.md, задача T4.1/§2.5) — чистая функция без Swing: вход — точки-гнёзда
 * со сторонами и список препятствий (прямоугольники ДРУГИХ блоков схемы, УЖЕ
 * расширенные вызывающим кодом на {@code margin}, см. {@link #route}), выход —
 * ломаная линия, все отрезки которой строго горизонтальны или вертикальны.
 *
 * <p><b>Метод</b> — сетка видимости (стандартный приём ортогональной трассировки,
 * Wybrow et al., 2009, ссылка в DIALOG.md): кандидаты на координаты X — левые/правые
 * границы препятствий плюс X обеих точек-"усов" (см. ниже); кандидаты Y — аналогично
 * по верху/низу. Узел сетки существует в каждой точке пересечения кандидатных линий,
 * не попадающей ВНУТРЬ ни одного препятствия; ребро — между соседними по сетке узлами,
 * если соединяющий их отрезок не проходит через внутренность препятствия. Кратчайший
 * путь — Дейкстра по РАСШИРЕННЫМ состояниям (узел, направление прихода), чтобы штрафовать
 * повороты ({@code cost = длина + 40 × количество поворотов}), а не только длину.
 *
 * <p><b>"Усы" (stubs).</b> Первый и последний отрезок маршрута обязаны быть
 * перпендикулярны стороне соответствующего гнезда (иначе линия визуально выходила
 * бы из блока по диагонали) — это гарантируется НЕ поиском по сетке, а тем, что перед
 * поиском от каждой точки-гнезда откладывается фиксированный отрезок длиной {@link
 * #STUB} строго по нормали стороны; сеточный поиск строит путь МЕЖДУ концами этих
 * усов, а сами усы дописываются к результату как есть, без штрафа за поворот на
 * стыке (первый поворот пути и так не бесплатный — направление уса становится
 * начальным направлением для подсчёта дальнейших поворотов).
 *
 * <p><b>Область поиска.</b> Из всех переданных препятствий в сетку идут только те,
 * чей прямоугольник пересекается с ограничивающим прямоугольником усов, раздутым на
 * {@link #CORRIDOR_PADDING} — обходить препятствия, лежащие далеко в стороне от прямого
 * пути, всё равно почти никогда не нужно на схеме оборудования (в отличие от
 * абстрактного графа), а без этого ограничения сетка на сцене из T4.1 "фикстура ×3"
 * (~90 блоков) давала бы {@code O(n²)} узлов на КАЖДУЮ из 200 связей. Если после
 * фильтрации подходящего пути всё равно нет (усы упёрлись в препятствие или сетка
 * несвязна) — {@link #fallbackZRoute} без обхода препятствий, с флагом {@link
 * RouteResult#fallback()} для теста производительности/приёмки.
 */
public final class OrthogonalRouter {

    private OrthogonalRouter() {
    }

    /** Прямоугольник-препятствие — УЖЕ расширенный вызывающим кодом на {@code margin}
     *  (PLAN.md §2.5: "margin = 10"); эта граница трактуется как ОТКРЫТОЕ множество —
     *  сеточный узел/отрезок, лежащий РОВНО на границе (что обычно и бывает: границы
     *  препятствий и есть источник кандидатных координат сетки), препятствием не
     *  считается, только строго внутренняя область. */
    public record Obstacle(double x, double y, double w, double h) {
        double left() {
            return x;
        }

        double right() {
            return x + w;
        }

        double top() {
            return y;
        }

        double bottom() {
            return y + h;
        }

        boolean containsInterior(double px, double py) {
            return px > left() && px < right() && py > top() && py < bottom();
        }

        boolean intersects(Obstacle other) {
            return left() < other.right() && right() > other.left()
                    && top() < other.bottom() && bottom() > other.top();
        }
    }

    /** @param points   узловые точки маршрута по порядку (начало гнезда → … → конец
     *                  гнезда), соседние точки коллинеарны только на СТЫКЕ отрезков
     *                  разного направления — идущие подряд точки одного направления
     *                  уже слиты в один отрезок (см. {@link #simplify}).
     *  @param fallback true — сетка не дала пути (усы упёрлись в препятствие или
     *                  граф несвязен), маршрут простой ("Z") и НЕ гарантирует обход
     *                  препятствий. */
    public record RouteResult(List<double[]> points, boolean fallback) {
    }

    private static final double STUB = 12;
    private static final double TURN_PENALTY = 40;
    private static final double CORRIDOR_PADDING = 120;
    /** Точки/линии сетки считаются совпадающими в пределах этого допуска — защита от
     *  дублей сетки из-за накопленной погрешности double по разным источникам координат
     *  (границы препятствий, точки усов). */
    private static final double EPS = 0.5;

    public static RouteResult route(double sourceX, double sourceY, NodeSide sourceSide,
                                     double targetX, double targetY, NodeSide targetSide,
                                     List<Obstacle> obstacles) {
        double[] sourceDir = outward(sourceSide);
        double[] targetDir = outward(targetSide);
        double sourceStubX = sourceX + sourceDir[0] * STUB, sourceStubY = sourceY + sourceDir[1] * STUB;
        double targetStubX = targetX + targetDir[0] * STUB, targetStubY = targetY + targetDir[1] * STUB;

        double corrX0 = Math.min(sourceStubX, targetStubX) - CORRIDOR_PADDING;
        double corrX1 = Math.max(sourceStubX, targetStubX) + CORRIDOR_PADDING;
        double corrY0 = Math.min(sourceStubY, targetStubY) - CORRIDOR_PADDING;
        double corrY1 = Math.max(sourceStubY, targetStubY) + CORRIDOR_PADDING;
        Obstacle corridor = new Obstacle(corrX0, corrY0, corrX1 - corrX0, corrY1 - corrY0);
        List<Obstacle> relevant = new ArrayList<>();
        for (Obstacle o : obstacles) {
            if (o.intersects(corridor)) {
                relevant.add(o);
            }
        }

        List<double[]> middle = gridRoute(sourceStubX, sourceStubY, initialDir(sourceSide),
                targetStubX, targetStubY, relevant);
        boolean fallback = middle == null;
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{sourceX, sourceY});
        pts.add(new double[]{sourceStubX, sourceStubY});
        if (fallback) {
            pts.addAll(fallbackZRoute(sourceStubX, sourceStubY, targetStubX, targetStubY));
        } else {
            pts.addAll(middle);
        }
        pts.add(new double[]{targetStubX, targetStubY});
        pts.add(new double[]{targetX, targetY});
        return new RouteResult(simplify(pts), fallback);
    }

    /** Единичный вектор "наружу от блока" для стороны {@code side} — тот же приём
     *  нужен {@link EdgeBundles} для направления ствола пучка (та же геометрическая
     *  нормаль, что и для уса связи), поэтому не {@code private}. */
    static double[] outward(NodeSide side) {
        return switch (side) {
            case LEFT -> new double[]{-1, 0};
            case RIGHT -> new double[]{1, 0};
            case TOP -> new double[]{0, -1};
            case BOTTOM -> new double[]{0, 1};
        };
    }

    private static int initialDir(NodeSide side) {
        return switch (side) {
            case LEFT -> DIR_LEFT;
            case RIGHT -> DIR_RIGHT;
            case TOP -> DIR_UP;
            case BOTTOM -> DIR_DOWN;
        };
    }

    private static final int DIR_RIGHT = 0, DIR_LEFT = 1, DIR_DOWN = 2, DIR_UP = 3;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** {@code null}, если сетка построена, но связного пути между усами нет (граф
     *  несвязен из-за препятствий) — тогда вызывающий код переходит на {@link
     *  #fallbackZRoute}. Иначе — точки маршрута МЕЖДУ усами, включая сами точки усов
     *  на обоих концах (упрощаются вместе с остальными в {@link #simplify}). */
    private static List<double[]> gridRoute(double sx, double sy, int startDir, double tx, double ty,
                                             List<Obstacle> obstacles) {
        double[] xs = sortedUnique(collectXs(sx, tx, obstacles));
        double[] ys = sortedUnique(collectYs(sy, ty, obstacles));
        int nx = xs.length, ny = ys.length;
        int sIdx = indexOf(xs, sx), sJdx = indexOf(ys, sy);
        int tIdx = indexOf(xs, tx), tJdx = indexOf(ys, ty);
        if (sIdx < 0 || sJdx < 0 || tIdx < 0 || tJdx < 0) {
            return null;
        }

        // Состояние = (i, j, направление прихода); направление входит в id узла, чтобы
        // Дейкстра штрафовала повороты, а не только длину пройденного пути.
        int gridSize = nx * ny;
        int stateCount = gridSize * 4;
        double[] dist = new double[stateCount];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        int[] prevState = new int[stateCount];
        Arrays.fill(prevState, -1);

        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Double.compare(
                Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));
        int startNode = sJdx * nx + sIdx;
        int startState = startNode * 4 + startDir;
        dist[startState] = 0;
        pq.add(new long[]{startState, Double.doubleToLongBits(0)});

        int targetNode = tJdx * nx + tIdx;
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int state = (int) top[0];
            double d = Double.longBitsToDouble(top[1]);
            if (d > dist[state] + EPS) {
                continue;
            }
            int node = state / 4, dir = state % 4;
            int i = node % nx, j = node / nx;
            for (int nd = 0; nd < 4; nd++) {
                int ni = i + DX[nd], nj = j + DY[nd];
                if (ni < 0 || ni >= nx || nj < 0 || nj >= ny) {
                    continue;
                }
                if (!segmentClear(xs[i], ys[j], xs[ni], ys[nj], obstacles)) {
                    continue;
                }
                double segLen = Math.hypot(xs[ni] - xs[i], ys[nj] - ys[j]);
                double turn = nd == dir ? 0 : TURN_PENALTY;
                int nNode = nj * nx + ni;
                int nState = nNode * 4 + nd;
                double nd_ = d + segLen + turn;
                if (nd_ < dist[nState] - EPS) {
                    dist[nState] = nd_;
                    prevState[nState] = state;
                    pq.add(new long[]{nState, Double.doubleToLongBits(nd_)});
                }
            }
        }

        int bestState = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int d = 0; d < 4; d++) {
            int state = targetNode * 4 + d;
            if (dist[state] < bestDist) {
                bestDist = dist[state];
                bestState = state;
            }
        }
        if (bestState < 0 || Double.isInfinite(bestDist)) {
            return null;
        }

        Deque<double[]> path = new ArrayDeque<>();
        int state = bestState;
        while (state != -1) {
            int node = state / 4;
            int i = node % nx, j = node / nx;
            path.addFirst(new double[]{xs[i], ys[j]});
            state = prevState[state];
        }
        return new ArrayList<>(path);
    }

    private static double[] collectXs(double sx, double tx, List<Obstacle> obstacles) {
        double[] xs = new double[2 + obstacles.size() * 2];
        xs[0] = sx;
        xs[1] = tx;
        for (int k = 0; k < obstacles.size(); k++) {
            xs[2 + k * 2] = obstacles.get(k).left();
            xs[2 + k * 2 + 1] = obstacles.get(k).right();
        }
        return xs;
    }

    private static double[] collectYs(double sy, double ty, List<Obstacle> obstacles) {
        double[] ys = new double[2 + obstacles.size() * 2];
        ys[0] = sy;
        ys[1] = ty;
        for (int k = 0; k < obstacles.size(); k++) {
            ys[2 + k * 2] = obstacles.get(k).top();
            ys[2 + k * 2 + 1] = obstacles.get(k).bottom();
        }
        return ys;
    }

    private static double[] sortedUnique(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double[] out = new double[sorted.length];
        int n = 0;
        for (double v : sorted) {
            if (n == 0 || v - out[n - 1] > EPS) {
                out[n++] = v;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static int indexOf(double[] sorted, double v) {
        for (int i = 0; i < sorted.length; i++) {
            if (Math.abs(sorted[i] - v) <= EPS) {
                return i;
            }
        }
        return -1;
    }

    /** Отрезок {@code (x1,y1)-(x2,y2)} (заведомо горизонтальный ИЛИ вертикальный —
     *  соседи по сетке) не проходит через внутренность ни одного препятствия. */
    private static boolean segmentClear(double x1, double y1, double x2, double y2, List<Obstacle> obstacles) {
        double midX = (x1 + x2) / 2, midY = (y1 + y2) / 2;
        for (Obstacle o : obstacles) {
            // Отрезок между соседними узлами сетки короче стороны любого препятствия,
            // чьи грани дали эти координаты, либо не пересекает его вовсе — проверки
            // средней точки достаточно (стандартный приём для сетки на границах
            // препятствий: если бы отрезок входил в препятствие, он вошёл бы и на
            // середине, т.к. сами координаты сетки — это как раз границы).
            if (o.containsInterior(midX, midY)) {
                return false;
            }
        }
        return true;
    }

    /** Прямой "Z"-маршрут между усами без обхода препятствий (см. {@link
     *  RouteResult#fallback()}) — один излом посередине по X (или по Y, если усы уже
     *  на одной вертикали — тогда Z вырождается в прямую). */
    private static List<double[]> fallbackZRoute(double sx, double sy, double tx, double ty) {
        List<double[]> pts = new ArrayList<>();
        if (Math.abs(sx - tx) <= EPS || Math.abs(sy - ty) <= EPS) {
            return pts;
        }
        double midX = (sx + tx) / 2;
        pts.add(new double[]{midX, sy});
        pts.add(new double[]{midX, ty});
        return pts;
    }

    /** Убирает промежуточные точки, лежащие на прямой между соседями (слияние подряд
     *  идущих отрезков одного направления в один) — и сеточный поиск, и {@link
     *  #fallbackZRoute} могут дать техническую точку там, где реального излома нет. */
    private static List<double[]> simplify(List<double[]> pts) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : pts) {
            if (out.size() >= 2) {
                double[] a = out.get(out.size() - 2);
                double[] b = out.get(out.size() - 1);
                boolean sameLine = (Math.abs(a[0] - b[0]) <= EPS && Math.abs(b[0] - p[0]) <= EPS)
                        || (Math.abs(a[1] - b[1]) <= EPS && Math.abs(b[1] - p[1]) <= EPS);
                boolean noOp = Math.abs(b[0] - p[0]) <= EPS && Math.abs(b[1] - p[1]) <= EPS;
                if (noOp) {
                    continue;
                }
                if (sameLine) {
                    out.remove(out.size() - 1);
                }
            } else if (!out.isEmpty()) {
                double[] b = out.get(out.size() - 1);
                if (Math.abs(b[0] - p[0]) <= EPS && Math.abs(b[1] - p[1]) <= EPS) {
                    continue;
                }
            }
            out.add(p);
        }
        return out;
    }
}
