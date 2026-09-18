package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T4.2 — {@link LaneNudger}: разносит
 *  коллинеарные перекрывающиеся отрезки РАЗНЫХ маршрутов на канал в 8px, не трогая
 *  усы (первый/последний отрезок каждого маршрута). */
class LaneNudgerTest {

    private static double[] p(double x, double y) {
        return new double[]{x, y};
    }

    @Test
    void overlappingCollinearSegmentsOfDifferentRoutesEndUpOnDifferentLines() {
        List<double[]> routeA = List.of(p(0, 50), p(20, 50), p(200, 50), p(220, 50));
        List<double[]> routeB = List.of(p(300, 50), p(280, 50), p(100, 50), p(80, 50));

        List<List<double[]>> nudged = LaneNudger.nudge(List.of(routeA, routeB));

        double lineA = internalSegmentLine(nudged.get(0));
        double lineB = internalSegmentLine(nudged.get(1));
        assertTrue(Math.abs(lineA - lineB) > 0.5, "после разноса линии должны отличаться: " + lineA + " vs " + lineB);
        assertTrue(Math.abs(lineA - 50) > 0.5, "маршрут A должен сдвинуться от исходной линии y=50");
        assertTrue(Math.abs(lineB - 50) > 0.5, "маршрут B должен сдвинуться от исходной линии y=50");
    }

    private static double internalSegmentLine(List<double[]> pts) {
        // После разноса внутренний участок — это средние точки уступа (индексы 2..-3);
        // берём Y любой из них (весь уступ — на одной линии по построению).
        return pts.get(2)[1];
    }

    @Test
    void pinEndpointsAreNeverMoved() {
        List<double[]> routeA = List.of(p(0, 50), p(20, 50), p(200, 50), p(220, 50));
        List<double[]> routeB = List.of(p(300, 50), p(280, 50), p(100, 50), p(80, 50));

        List<List<double[]>> nudged = LaneNudger.nudge(List.of(routeA, routeB));

        assertArrayEquals(routeA.get(0), nudged.get(0).get(0), 1e-9);
        assertArrayEquals(routeA.get(routeA.size() - 1), nudged.get(0).get(nudged.get(0).size() - 1), 1e-9);
        assertArrayEquals(routeB.get(0), nudged.get(1).get(0), 1e-9);
        assertArrayEquals(routeB.get(routeB.size() - 1), nudged.get(1).get(nudged.get(1).size() - 1), 1e-9);
    }

    @Test
    void allSegmentsRemainAxisAlignedAfterNudging() {
        List<double[]> routeA = List.of(p(0, 50), p(20, 50), p(200, 50), p(220, 50));
        List<double[]> routeB = List.of(p(300, 50), p(280, 50), p(100, 50), p(80, 50));

        List<List<double[]>> nudged = LaneNudger.nudge(List.of(routeA, routeB));

        for (List<double[]> route : nudged) {
            for (int i = 0; i + 1 < route.size(); i++) {
                double[] a = route.get(i), b = route.get(i + 1);
                boolean horizontal = Math.abs(a[1] - b[1]) < 1e-6;
                boolean vertical = Math.abs(a[0] - b[0]) < 1e-6;
                assertTrue(horizontal || vertical, "разрыв ортогональности на отрезке " + i);
            }
        }
    }

    @Test
    void nonOverlappingCollinearSegmentsAreLeftUntouched() {
        // Обе линии на y=50, но по X НЕ пересекаются — разносить нечего.
        List<double[]> routeA = List.of(p(0, 50), p(20, 50), p(90, 50), p(100, 50));
        List<double[]> routeB = List.of(p(300, 50), p(280, 50), p(200, 50), p(180, 50));

        List<List<double[]>> nudged = LaneNudger.nudge(List.of(routeA, routeB));

        for (int r = 0; r < 2; r++) {
            List<double[]> before = r == 0 ? routeA : routeB;
            List<double[]> after = nudged.get(r);
            assertEquals(before.size(), after.size(), "без перекрытия — точки не добавляются");
            for (int i = 0; i < before.size(); i++) {
                assertArrayEquals(before.get(i), after.get(i), 1e-9);
            }
        }
    }

    @Test
    void routesWithoutAnyInternalSegmentPassThroughUnchanged() {
        // Только ус-в-ус (2 точки на маршрут после симплификации, как у OrthogonalRouter
        // без препятствий) — внутренних отрезков нет вовсе.
        List<double[]> straight = List.of(p(0, 0), p(100, 0));
        List<List<double[]>> nudged = LaneNudger.nudge(List.of(straight));
        assertEquals(1, nudged.size());
        assertArrayEquals(straight.get(0), nudged.get(0).get(0), 1e-9);
        assertArrayEquals(straight.get(1), nudged.get(0).get(1), 1e-9);
    }

    @Test
    void resultIsDeterministicAndDoesNotMutateInput() {
        List<double[]> routeA = List.of(p(0, 50), p(20, 50), p(200, 50), p(220, 50));
        List<double[]> routeB = List.of(p(300, 50), p(280, 50), p(100, 50), p(80, 50));
        double[] beforeMutationCheck = routeA.get(1).clone();

        LaneNudger.nudge(List.of(routeA, routeB));

        assertArrayEquals(beforeMutationCheck, routeA.get(1), 1e-9, "исходный маршрут не должен меняться");

        List<List<double[]>> first = LaneNudger.nudge(List.of(routeA, routeB));
        List<List<double[]>> second = LaneNudger.nudge(List.of(routeA, routeB));
        assertEquals(first.get(0).size(), second.get(0).size());
        for (int i = 0; i < first.get(0).size(); i++) {
            assertArrayEquals(first.get(0).get(i), second.get(0).get(i), 1e-9);
        }
    }
}
