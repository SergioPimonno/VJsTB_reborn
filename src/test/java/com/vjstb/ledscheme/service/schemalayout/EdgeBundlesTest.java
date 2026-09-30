package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NodeSide;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T4.3 (D8) — геометрия ствола пучка и
 *  подпись количества, чистая функция без Swing. */
class EdgeBundlesTest {

    @Test
    void trunkRunsStrictlyAlongTheOutwardNormalOfTheSide() {
        EdgeBundles.Bundle right = EdgeBundles.bundleFor(100, 50, NodeSide.RIGHT, 3);
        assertEquals(100, right.pin()[0], 1e-9);
        assertEquals(50, right.pin()[1], 1e-9);
        assertEquals(100 + EdgeBundles.TRUNK_LENGTH, right.mergePoint()[0], 1e-9);
        assertEquals(50, right.mergePoint()[1], 1e-9, "RIGHT — ствол строго горизонтален, Y не меняется");

        EdgeBundles.Bundle top = EdgeBundles.bundleFor(100, 50, NodeSide.TOP, 3);
        assertEquals(100, top.mergePoint()[0], 1e-9, "TOP — ствол строго вертикален, X не меняется");
        assertEquals(50 - EdgeBundles.TRUNK_LENGTH, top.mergePoint()[1], 1e-9);
    }

    @Test
    void trunkLengthIsFixedRegardlessOfMemberCount() {
        EdgeBundles.Bundle two = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 2);
        EdgeBundles.Bundle ten = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 10);
        double lenTwo = Math.hypot(two.mergePoint()[0] - two.pin()[0], two.mergePoint()[1] - two.pin()[1]);
        double lenTen = Math.hypot(ten.mergePoint()[0] - ten.pin()[0], ten.mergePoint()[1] - ten.pin()[1]);
        assertEquals(lenTwo, lenTen, 1e-9);
        assertEquals(EdgeBundles.TRUNK_LENGTH, lenTwo, 1e-9);
    }

    @Test
    void trunkSegmentGoesFromMergePointToPin() {
        EdgeBundles.Bundle b = EdgeBundles.bundleFor(10, 20, NodeSide.BOTTOM, 4);
        double[][] trunk = b.trunk();
        assertEquals(b.mergePoint()[0], trunk[0][0], 1e-9);
        assertEquals(b.mergePoint()[1], trunk[0][1], 1e-9);
        assertEquals(b.pin()[0], trunk[1][0], 1e-9);
        assertEquals(b.pin()[1], trunk[1][1], 1e-9);
    }

    @Test
    void labelShowsTheMemberCount() {
        assertEquals("×2", EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 2).label());
        assertEquals("×7", EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 7).label());
    }

    @Test
    void bundleOfFewerThanTwoMembersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 1));
        assertThrows(IllegalArgumentException.class, () -> EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 0));
    }

    @Test
    void divergencePointIsLastPointSharedByAllRoutes() {
        // Общий пин (0,0) -> общий изгиб (20,0) -> дальше расходятся в 3 разные
        // точки — граница пучка должна остановиться ровно на (20,0), а не на
        // самом пине и не где-то ещё.
        List<double[]> a = List.of(new double[]{0, 0}, new double[]{20, 0}, new double[]{20, 30});
        List<double[]> b = List.of(new double[]{0, 0}, new double[]{20, 0}, new double[]{20, -30});
        List<double[]> c = List.of(new double[]{0, 0}, new double[]{20, 0}, new double[]{40, 0});
        double[] p = EdgeBundles.divergencePoint(List.of(a, b, c));
        assertEquals(20, p[0], 1e-9);
        assertEquals(0, p[1], 1e-9);
    }

    @Test
    void divergencePointIsThePinItselfWhenRoutesDivergeImmediately() {
        List<double[]> a = List.of(new double[]{5, 5}, new double[]{5, 20});
        List<double[]> b = List.of(new double[]{5, 5}, new double[]{30, 5});
        double[] p = EdgeBundles.divergencePoint(List.of(a, b));
        assertEquals(5, p[0], 1e-9);
        assertEquals(5, p[1], 1e-9);
    }

    @Test
    void divergencePointFollowsTheLongerCommonPrefixEvenWithUnequalRouteLengths() {
        List<double[]> a = List.of(new double[]{0, 0}, new double[]{10, 0}, new double[]{10, 10});
        List<double[]> b = List.of(new double[]{0, 0}, new double[]{10, 0});
        double[] p = EdgeBundles.divergencePoint(List.of(a, b));
        assertEquals(10, p[0], 1e-9);
        assertEquals(0, p[1], 1e-9, "короткий маршрут заканчивается ровно там, где ещё совпадает с длинным");
    }

    @Test
    void commonPrefixIncludesEveryPointStillSharedNotJustTheLast() {
        // Два общих излома (0,0)->(20,0)->(20,20), потом расходятся -- индикация
        // шины должна идти ломаной через ОБА общих излома, не срезать напрямую.
        List<double[]> a = List.of(new double[]{0, 0}, new double[]{20, 0}, new double[]{20, 20}, new double[]{40, 20});
        List<double[]> b = List.of(new double[]{0, 0}, new double[]{20, 0}, new double[]{20, 20}, new double[]{20, 40});
        List<double[]> prefix = EdgeBundles.commonPrefix(List.of(a, b));
        assertEquals(3, prefix.size());
        assertEquals(0, prefix.get(0)[0], 1e-9);
        assertEquals(20, prefix.get(1)[0], 1e-9);
        assertEquals(0, prefix.get(1)[1], 1e-9);
        assertEquals(20, prefix.get(2)[0], 1e-9);
        assertEquals(20, prefix.get(2)[1], 1e-9);
    }

    /** Баг-репорт пользователя 2026-09-30: на плотной реальной схеме чип общей
     *  подписи стоял прямо у гнезда, хотя линии визуально шли одним местом заметно
     *  дальше. Причина — {@code OrthogonalRouter} считает маршрут каждой связи
     *  НЕЗАВИСИМЫМ запуском поиска по сетке (свой список препятствий на связь, см.
     *  {@code SchemaCanvasPanel#autoRoutePoints}), из-за чего одна и та же
     *  "визуально общая" точка поворота может прийти с разницей в доли пикселя
     *  между связями пучка — математически точное сравнение (было {@code 1e-6})
     *  считало это расхождением уже на первой точке. Допуск — пиксельный, а не
     *  геометрический: точки в пределах него должны СЧИТАТЬСЯ общими. */
    @Test
    void commonPrefixToleratesSubPixelJitterBetweenIndependentlyRoutedEdges() {
        List<double[]> a = List.of(new double[]{0, 0}, new double[]{300.0, 200.0}, new double[]{300, 220});
        List<double[]> b = List.of(new double[]{0, 0}, new double[]{300.6, 199.7}, new double[]{300, 260});
        List<double[]> prefix = EdgeBundles.commonPrefix(List.of(a, b));
        assertEquals(2, prefix.size(),
                "точки поворота отличаются меньше чем на пиксель -- должны считаться общей точкой пучка");
    }

    /** Симметричный случай — расхождение ЗАМЕТНО больше допуска (не числовой шум
     *  независимой трассировки, а реально разные маршруты) по-прежнему должно
     *  считаться расхождением, а не общей точкой. */
    @Test
    void commonPrefixStillDetectsRealDivergenceBeyondTolerance() {
        List<double[]> a = List.of(new double[]{0, 0}, new double[]{300, 200}, new double[]{300, 220});
        List<double[]> b = List.of(new double[]{0, 0}, new double[]{340, 205}, new double[]{300, 260});
        List<double[]> prefix = EdgeBundles.commonPrefix(List.of(a, b));
        assertEquals(1, prefix.size(), "40px расхождения -- это реально разные маршруты, не числовой шум");
    }

    /** Регрессия на РЕАЛЬНЫХ координатах из проекта пользователя "Циммер"
     *  (2026-09-30): 4 связи пучка "AlpenBox 125A->4xSocapex", у получателей
     *  чуть разная ширина блока (авто-подгонка под текст) — две связи поворачивают
     *  на X=551, две другие на X=549, разница ровно 2px. С прежним допуском (1.5)
     *  это уже считалось расхождением на первой же точке после гнезда — чип общей
     *  подписи вставал прямо у гнезда вместо реальной развилки (баг-репорт,
     *  скриншот). */
    @Test
    void commonPrefixMergesRealWorldTwoPixelTurnPointDriftFromZimmerProject() {
        List<double[]> e1 = List.of(new double[]{325, 387.6}, new double[]{551, 387.6},
                new double[]{551, 160.6}, new double[]{576, 160.6});
        List<double[]> e2 = List.of(new double[]{325, 387.6}, new double[]{551, 387.6},
                new double[]{551, 245.6}, new double[]{575, 245.6});
        List<double[]> e3 = List.of(new double[]{325, 387.6}, new double[]{549, 387.6},
                new double[]{549, 542.6}, new double[]{573, 542.6});
        List<double[]> e4 = List.of(new double[]{325, 387.6}, new double[]{549, 387.6},
                new double[]{549, 639.6}, new double[]{573, 639.6});
        List<double[]> prefix = EdgeBundles.commonPrefix(List.of(e1, e2, e3, e4));
        assertEquals(2, prefix.size(),
                "гнездо + общая точка поворота (X=549..551, в пределах допуска) -- не должно схлопываться в одну точку");
        assertEquals(325, prefix.get(0)[0], 1e-9);
        assertEquals(387.6, prefix.get(0)[1], 1e-9);
    }

    @Test
    void allFourSidesProduceDistinctOutwardDirections() {
        double[] r = EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 2).mergePoint();
        double[] l = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 2).mergePoint();
        double[] t = EdgeBundles.bundleFor(0, 0, NodeSide.TOP, 2).mergePoint();
        double[] btm = EdgeBundles.bundleFor(0, 0, NodeSide.BOTTOM, 2).mergePoint();
        assertTrue(r[0] > 0 && Math.abs(r[1]) < 1e-9);
        assertTrue(l[0] < 0 && Math.abs(l[1]) < 1e-9);
        assertTrue(t[1] < 0 && Math.abs(t[0]) < 1e-9);
        assertTrue(btm[1] > 0 && Math.abs(btm[0]) < 1e-9);
    }
}
