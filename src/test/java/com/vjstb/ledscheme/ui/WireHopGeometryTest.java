package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Покрытие геометрии «мостиков» на пересечениях линий связи общей схемы
 * (переключатель {@code UserProfile#schemaWireHops}). Сам {@code SchemaCanvasPanel}
 * в headless-тесте не поднять, поэтому вся содержательная математика вынесена в
 * {@link WireHopGeometry} и проверяется здесь: правильность отсева пересечений
 * (почти параллельные, слишком близко к концу — там реальное соединение, а не
 * перекрёст), детерминированное правило «сверху та линия, чей сегмент длиннее», и
 * что дуга-обход действительно выгибается в сторону от линии.
 */
class WireHopGeometryTest {

    @Test
    void perpendicularCrossReturnsMidParams() {
        double[] p = WireHopGeometry.crossParams(0, 0, 100, 0, 50, -50, 50, 50);
        assertNotNull(p);
        assertTrue(Math.abs(p[0] - 0.5) < 1e-9, "ta");
        assertTrue(Math.abs(p[1] - 0.5) < 1e-9, "tb");
    }

    @Test
    void nonCrossingSegmentsReturnNull() {
        assertNull(WireHopGeometry.crossParams(0, 0, 100, 0, 0, 20, 100, 20));
    }

    @Test
    void almostParallelSegmentsReturnNull() {
        // ~3° между отрезками — вырожденное пересечение, мостик не ставим
        assertNull(WireHopGeometry.crossParams(0, 0, 200, 0, -100, 3, 100, -3));
    }

    @Test
    void crossingTooCloseToAnEndpointReturnsNull() {
        // вертикальный отрезок пересекает горизонтальный в 2px от его левого конца —
        // это стык/гнездо, а не перекрёст
        assertNull(WireHopGeometry.crossParams(0, 0, 100, 0, 2, -20, 2, 20));
    }

    @Test
    void longerSegmentIsOnTopWithDeterministicTieBreak() {
        assertTrue(WireHopGeometry.aIsOnTop(120, 40));
        assertFalse(WireHopGeometry.aIsOnTop(40, 120));
        assertTrue(WireHopGeometry.aIsOnTop(80, 80), "равные длины — верхняя A (стабильный tie-break)");
    }

    @Test
    void hoppedPathBulgesAwayFromAHorizontalLine() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        var path = WireHopGeometry.hoppedPath(pts, List.of(new double[]{0, 0.5}), 5);

        double[] c = new double[6];
        boolean sawCubic = false;
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            if (it.currentSegment(c) == PathIterator.SEG_CUBICTO) {
                sawCubic = true;
            }
        }
        assertTrue(sawCubic, "в пути должна появиться кубическая кривая-обход");

        double minY = 0;
        for (PathIterator it = path.getPathIterator(null, 0.2); !it.isDone(); it.next()) {
            it.currentSegment(c);
            minY = Math.min(minY, c[1]); // после сглаживания дуга — цепочка SEG_LINETO
        }
        assertTrue(minY < -3, "дуга должна выгибаться вверх экрана (Y < 0), было minY=" + minY);
    }

    @Test
    void hoppedPathWithoutHopsIsPlainPolyline() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{50, 0}, new double[]{50, 40});
        var path = WireHopGeometry.hoppedPath(pts, List.of(), 5);

        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            assertTrue(type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO,
                    "без обходов путь — чистая ломаная, без кривых");
        }
    }

    @Test
    void hopTooCloseToSegmentEndIsSkipped() {
        // обход в 1px от начала 100px-сегмента при радиусе 5 — влезть некуда,
        // линия идёт насквозь (путь остаётся ломаной)
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        var path = WireHopGeometry.hoppedPath(pts, List.of(new double[]{0, 0.01}), 5);

        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            assertTrue(it.currentSegment(c) != PathIterator.SEG_CUBICTO,
                    "слишком краевой обход должен быть пропущен");
        }
    }

    // ---- слияние близких обходов в один пролёт ------------------------------

    @Test
    void twoCloseHopsMergeIntoOneWiderSpan() {
        // пересечения в 20px друг от друга при r=9 (2r+MERGE_GAP = 22 > 20) — сливаются
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        var spans = WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.4}, new double[]{0, 0.6}), 9);

        assertEquals(1, spans.size());
        assertEquals(0, spans.get(0).segIndex());
        assertEquals(40 - 9, spans.get(0).alongStart(), 1e-6, "начало = первый обход − r");
        assertEquals(60 + 9, spans.get(0).alongEnd(), 1e-6, "конец = последний обход + r");
    }

    @Test
    void farApartHopsStaySeparateSpans() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        var spans = WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.2}, new double[]{0, 0.8}), 9);
        assertEquals(2, spans.size());
    }

    @Test
    void hopSpansDropsHopTooCloseToSegmentStart() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        assertTrue(WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.01}), 9).isEmpty());
    }

    // ---- неинтерактивные точки излома от дуги + классификация под-сегментов --

    @Test
    void renderPointsInsertsTwoBreakPointsAndMarksArcSegment() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{100, 0});
        var spans = WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.5}), 9);
        var rr = WireHopGeometry.renderPoints(pts, spans);

        assertEquals(4, rr.points().size(), "начало + 2 точки границ дуги + конец");
        assertEquals(3, rr.arcSegment().length);
        assertFalse(rr.arcSegment()[0], "до дуги — обычный сегмент (стрелка рисуется)");
        assertTrue(rr.arcSegment()[1], "сам под-сегмент дуги — без стрелки");
        assertFalse(rr.arcSegment()[2], "после дуги — обычный сегмент");
        assertEquals(41, rr.points().get(1)[0], 1e-6);
        assertEquals(59, rr.points().get(2)[0], 1e-6);
    }

    @Test
    void renderPointsWithoutSpansIsOriginalPolylineAllArrowable() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{50, 0}, new double[]{50, 40});
        var rr = WireHopGeometry.renderPoints(pts, List.of());

        assertEquals(3, rr.points().size());
        assertEquals(2, rr.arcSegment().length);
        assertFalse(rr.arcSegment()[0]);
        assertFalse(rr.arcSegment()[1]);
    }

    // ---- форма дуги: усечённая держит просвет там, где кубика проседает ------

    @Test
    void flatTopHoldsClearanceAtCrossingsWhereCubicSags() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{200, 0});
        double r = 9;
        // x=40 и x=60 — сливаются (20 < 22), общий пролёт 31..69, плоская вершина [40,60]
        var spans = WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.2}, new double[]{0, 0.3}), r);
        assertEquals(1, spans.size());

        var flat = WireHopGeometry.hoppedPathFromSpans(pts, spans, r, WireHopGeometry.ArcShape.FLAT_TOP);
        assertTrue(sampleY(flat, 40) <= -r + 0.8, "плоская вершина: просвет ≈ r над первой линией");
        assertTrue(sampleY(flat, 60) <= -r + 0.8, "плоская вершина: просвет ≈ r над второй линией");

        var cubic = WireHopGeometry.hoppedPathFromSpans(pts, spans, r, WireHopGeometry.ArcShape.CUBIC);
        assertTrue(sampleY(cubic, 40) > -r + 0.8,
                "кубика: у края слитого пролёта просвет заметно меньше r");
    }

    @Test
    void cubicSpanIsSingleCurveFlatTopIsTwo() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{200, 0});
        var spans = WireHopGeometry.hopSpans(pts, List.of(new double[]{0, 0.2}, new double[]{0, 0.3}), 9);
        assertEquals(1, cubicCount(WireHopGeometry.hoppedPathFromSpans(
                pts, spans, 9, WireHopGeometry.ArcShape.CUBIC)));
        assertEquals(2, cubicCount(WireHopGeometry.hoppedPathFromSpans(
                pts, spans, 9, WireHopGeometry.ArcShape.FLAT_TOP)));
    }

    /** Y ближайшей к {@code targetX} точки сглаженного пути. */
    private static double sampleY(Path2D.Double path, double targetX) {
        double bestDx = Double.MAX_VALUE;
        double bestY = 0;
        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null, 0.1); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                double dx = Math.abs(c[0] - targetX);
                if (dx < bestDx) {
                    bestDx = dx;
                    bestY = c[1];
                }
            }
        }
        return bestY;
    }

    private static int cubicCount(Path2D.Double path) {
        int n = 0;
        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            if (it.currentSegment(c) == PathIterator.SEG_CUBICTO) {
                n++;
            }
        }
        return n;
    }
}
