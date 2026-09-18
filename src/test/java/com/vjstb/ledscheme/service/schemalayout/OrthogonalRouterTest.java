package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NodeSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T4.1 — {@link OrthogonalRouter}, чистая
 *  ортогональная трассировка одной связи без Swing. Критерии приёмки §2.5: оси
 *  отрезков, перпендикулярный выход из гнёзд, отсутствие пересечения с внутренностью
 *  препятствий (кроме {@code fallback}), детерминированность, не более 4 изломов на
 *  простой раскладке, производительность на крупной фикстуре. */
class OrthogonalRouterTest {

    @Test
    void everySegmentIsHorizontalOrVertical() {
        List<OrthogonalRouter.Obstacle> obstacles = List.of(
                new OrthogonalRouter.Obstacle(150, 0, 60, 200));
        OrthogonalRouter.RouteResult r = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 300, NodeSide.LEFT, obstacles);

        List<double[]> pts = r.points();
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            boolean horizontal = Math.abs(a[1] - b[1]) < 1e-6;
            boolean vertical = Math.abs(a[0] - b[0]) < 1e-6;
            assertTrue(horizontal || vertical, "сегмент " + i + " не горизонтален и не вертикален: "
                    + java.util.Arrays.toString(a) + " -> " + java.util.Arrays.toString(b));
        }
    }

    @Test
    void firstAndLastSegmentsArePerpendicularToPinSideWithMinimumStubLength() {
        OrthogonalRouter.RouteResult r = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 300, NodeSide.LEFT, List.of());

        List<double[]> pts = r.points();
        double[] source = pts.get(0), afterSource = pts.get(1);
        assertEquals(source[1], afterSource[1], 1e-6, "выход RIGHT — сначала строго по горизонтали");
        assertTrue(afterSource[0] - source[0] >= 12 - 1e-6, "длина уса не короче 12");

        double[] target = pts.get(pts.size() - 1), beforeTarget = pts.get(pts.size() - 2);
        assertEquals(target[1], beforeTarget[1], 1e-6, "вход LEFT — перед гнездом строго по горизонтали");
        assertTrue(target[0] - beforeTarget[0] >= 12 - 1e-6, "длина уса не короче 12");
    }

    @Test
    void routeDoesNotCrossObstacleInteriorWhenNotInFallback() {
        // Препятствие ровно на прямой линии между гнёздами — прямой путь невозможен,
        // маршрут обязан обойти его СТОРОНОЙ (не по диагонали через середину).
        OrthogonalRouter.Obstacle wall = new OrthogonalRouter.Obstacle(150, 50, 60, 150);
        OrthogonalRouter.RouteResult r = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 100, NodeSide.LEFT, List.of(wall));

        assertFalse(r.fallback(), "препятствие обходимо — сеточный поиск не должен сдаться");
        List<double[]> pts = r.points();
        for (int i = 0; i + 1 < pts.size(); i++) {
            assertFalse(crossesInterior(pts.get(i), pts.get(i + 1), wall),
                    "сегмент " + i + " проходит через внутренность препятствия");
        }
    }

    private static boolean crossesInterior(double[] a, double[] b, OrthogonalRouter.Obstacle o) {
        double midX = (a[0] + b[0]) / 2, midY = (a[1] + b[1]) / 2;
        boolean midInside = midX > o.x() && midX < o.x() + o.w() && midY > o.y() && midY < o.y() + o.h();
        boolean aInside = a[0] > o.x() && a[0] < o.x() + o.w() && a[1] > o.y() && a[1] < o.y() + o.h();
        boolean bInside = b[0] > o.x() && b[0] < o.x() + o.w() && b[1] > o.y() && b[1] < o.y() + o.h();
        return midInside || aInside || bInside;
    }

    @Test
    void routeIsDeterministicAcrossRepeatedCalls() {
        List<OrthogonalRouter.Obstacle> obstacles = List.of(
                new OrthogonalRouter.Obstacle(150, 0, 60, 200),
                new OrthogonalRouter.Obstacle(250, 150, 40, 200));
        OrthogonalRouter.RouteResult r1 = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 300, NodeSide.LEFT, obstacles);
        OrthogonalRouter.RouteResult r2 = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 300, NodeSide.LEFT, obstacles);

        assertEquals(r1.points().size(), r2.points().size());
        for (int i = 0; i < r1.points().size(); i++) {
            assertEquals(r1.points().get(i)[0], r2.points().get(i)[0], 1e-9);
            assertEquals(r1.points().get(i)[1], r2.points().get(i)[1], 1e-9);
        }
    }

    @Test
    void simpleFacingLayoutWithNoObstaclesUsesAtMostFourBends() {
        // Гнёзда друг напротив друга на одной высоте, без препятствий — прямая линия
        // усы-в-усы, изломов быть не должно вовсе.
        OrthogonalRouter.RouteResult r = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 100, NodeSide.LEFT, List.of());
        assertTrue(bendCount(r.points()) <= 4, "изломов: " + bendCount(r.points()));
    }

    @Test
    void layoutWithOneObstacleUsesAtMostFourBends() {
        OrthogonalRouter.Obstacle wall = new OrthogonalRouter.Obstacle(150, 50, 60, 150);
        OrthogonalRouter.RouteResult r = OrthogonalRouter.route(
                0, 100, NodeSide.RIGHT, 400, 100, NodeSide.LEFT, List.of(wall));
        assertTrue(bendCount(r.points()) <= 4, "изломов: " + bendCount(r.points()));
    }

    private static int bendCount(List<double[]> pts) {
        int bends = 0;
        for (int i = 1; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i), c = pts.get(i + 1);
            boolean dir1Horizontal = Math.abs(a[1] - b[1]) < 1e-6;
            boolean dir2Horizontal = Math.abs(b[1] - c[1]) < 1e-6;
            if (dir1Horizontal != dir2Horizontal) {
                bends++;
            }
        }
        return bends;
    }

    /** PLAN.md §2.5: "фикстура ×3 (≈90 блоков, 200 связей) трассируется целиком
     *  быстрее 200 мс — тест с порогом ×5 от замера, чтобы не флапал" — используем
     *  200мс×5=1000мс как фактический потолок теста (сам замер как база сравнения
     *  зависит от машины CI, абсолютный потолок с большим запасом надёжнее). */
    @Test
    void routesLargeFixtureFastEnough() {
        Random rnd = new Random(42);
        List<OrthogonalRouter.Obstacle> obstacles = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            double x = rnd.nextInt(3000);
            double y = rnd.nextInt(2000);
            obstacles.add(new OrthogonalRouter.Obstacle(x, y, 100, 60));
        }
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            double sx = rnd.nextInt(3000), sy = rnd.nextInt(2000);
            double tx = rnd.nextInt(3000), ty = rnd.nextInt(2000);
            OrthogonalRouter.route(sx, sy, NodeSide.RIGHT, tx, ty, NodeSide.LEFT, obstacles);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1000, "200 связей на фикстуре ×3 заняли " + elapsedMs + " мс (потолок 1000 мс)");
    }
}
