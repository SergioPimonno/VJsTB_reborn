package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.service.StructureCurveMath.Curve;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.Rect;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Геометрия изогнутых экранов и раздельных башен {@link StructureCurveMath} (запрос
 * пользователя 2026-10-01). Главное — инвариант «башня не проходит сквозь экран»: каждая
 * вершина (и точки рёбер) footprint'а каждой башни лежит вне объёма экрана — и вне плоских
 * кабинетов, и вне кольцевой полосы R…R±d из постановки — на широкой сетке радиусов, глубин,
 * зазоров и числа башен. Плюс коллизии оснований соседних башен на выпуклом экране (ориентир
 * пользователя: R = 4 м, башни вплотную — пересекаются уже при одной секции 500 мм) и
 * конверсия радиус ↔ угол между кабинетами.
 */
class StructureCurveMathTest {

    private static final TowerSpec TOWER_1000 = new TowerSpec(1000, 51, 500, 1000);

    @Test
    void radiusAndCabinetAngleConvertBothWays() {
        // R = w / (2·sin(θ/2)): кабинет 500 мм под 10° -> 2868,4 мм
        double r = StructureCurveMath.radiusFromCabinetAngle(500, 10);
        assertEquals(500 / (2 * Math.sin(Math.toRadians(5))), r, 1e-9);
        assertEquals(2868.4, r, 0.1);
        assertEquals(10.0, StructureCurveMath.cabinetAngleDeg(500, r), 1e-9);
        for (double w : new double[]{250, 500, 1000}) {
            for (double deg = 0.5; deg < 120; deg += 3.7) {
                double back = StructureCurveMath.cabinetAngleDeg(w, StructureCurveMath.radiusFromCabinetAngle(w, deg));
                assertEquals(deg, back, 1e-9, "w=" + w + " θ=" + deg);
            }
        }
        assertTrue(Double.isNaN(StructureCurveMath.radiusFromCabinetAngle(500, 0)));
        assertTrue(Double.isNaN(StructureCurveMath.radiusFromCabinetAngle(500, 180)));
        assertTrue(Double.isNaN(StructureCurveMath.cabinetAngleDeg(500, 200)), "R < w/2 невозможен");
    }

    @Test
    void derivedArcValuesOfCurve() {
        Curve c = new Curve(ScreenCurveType.CONCAVE, 5000, 500, 100, 12);
        double theta = 2 * Math.asin(250.0 / 5000);
        assertEquals(theta, c.cabinetAngleRad(), 1e-12);
        assertEquals(12 * theta, c.arcAngleRad(), 1e-12);
        assertEquals(6000, c.screenWidthMm(), 1e-9, "развёрнутая ширина от изгиба не зависит");
        assertEquals(2 * 5000 * Math.sin(6 * theta), c.chordMm(), 1e-6);
        assertEquals(5000 * (1 - Math.cos(6 * theta)), c.sagittaMm(), 1e-6);
        assertEquals(5100, c.backRadiusMm(), 1e-9, "вогнутый: Rb = R + d");
        Curve v = new Curve(ScreenCurveType.CONVEX, 5000, 500, 100, 12);
        assertEquals(Math.sqrt(5000.0 * 5000 - 250 * 250) - 100, v.backRadiusMm(), 1e-9,
                "выпуклый: по середине тыльной грани плоского кабинета, чуть меньше R − d");
        assertTrue(v.backRadiusMm() < 4900);
        assertEquals(0, new Curve(ScreenCurveType.FLAT, 5000, 500, 100, 12).sagittaMm());
    }

    @Test
    void cabinetColumnsFormPolygonInscribedInFrontCircle() {
        for (ScreenCurveType type : new ScreenCurveType[]{ScreenCurveType.CONCAVE, ScreenCurveType.CONVEX}) {
            Curve c = new Curve(type, 4000, 500, 100, 10);
            List<Placement> cols = StructureCurveMath.cabinetColumns(c);
            for (int i = 0; i < cols.size(); i++) {
                for (double xc : new double[]{-250, 250}) {
                    double[] p = cols.get(i).toWorld(xc, 0);
                    assertEquals(4000, Math.hypot(p[0] - c.centerX(), p[1] - c.centerZ()), 1e-6,
                            type + ": угол лицевой грани кабинета лежит на окружности R");
                }
                if (i + 1 < cols.size()) {
                    double[] right = cols.get(i).toWorld(250, 0);
                    double[] left = cols.get(i + 1).toWorld(-250, 0);
                    assertEquals(right[0], left[0], 1e-6, "соседние кабинеты смыкаются лицевыми углами");
                    assertEquals(right[1], left[1], 1e-6);
                }
            }
            // середина экрана -- в (W/2, 0) при чётном числе колонок
            double[] mid = cols.get(4).toWorld(250, 0);
            assertEquals(2500, mid[0], 1e-6);
            assertEquals(0, mid[1], 1e-6);
        }
    }

    @Test
    void placementWorldAndLocalAreInverse() {
        Placement p = new Placement(0, 1234, -567, 23.5);
        double[] w = p.toWorld(300, -800);
        double[] l = p.toLocal(w[0], w[1]);
        assertEquals(300, l[0], 1e-9);
        assertEquals(-800, l[1], 1e-9);
    }

    @Test
    void towersAreSymmetricAboutScreenCenterAndFacingTheScreen() {
        for (ScreenCurveType type : ScreenCurveType.values()) {
            Curve c = new Curve(type, 6000, 500, 100, 16);
            List<Placement> ps = StructureCurveMath.placements(c, TOWER_1000, 4, 600);
            for (int k = 0; k < 2; k++) {
                Placement a = ps.get(k);
                Placement b = ps.get(3 - k);
                assertEquals(c.screenWidthMm() - a.anchorX(), b.anchorX(), 1e-6, type + " симметрия по x");
                assertEquals(a.anchorZ(), b.anchorZ(), 1e-6, type + " симметрия по z");
                assertEquals(-a.yawDeg(), b.yawDeg(), 1e-9, type + " поворот зеркальный");
            }
            if (type == ScreenCurveType.FLAT) {
                assertEquals(-400, ps.get(0).anchorZ(), 1e-9, "прямой: та же передняя плоскость, что у стены");
                continue;
            }
            // передняя плоскость перпендикулярна радиусу: направление «вглубь» (локальный −z)
            // смотрит от экрана -- у вогнутого от центра кривизны, у выпуклого к нему
            for (Placement p : ps) {
                double[] front = p.toWorld(0, 0);
                double[] deep = p.toWorld(0, -1000);
                double rFront = Math.hypot(front[0] - c.centerX(), front[1] - c.centerZ());
                double rDeep = Math.hypot(deep[0] - c.centerX(), deep[1] - c.centerZ());
                if (type == ScreenCurveType.CONCAVE) {
                    assertEquals(rFront + 1000, rDeep, 1e-6);
                } else {
                    assertEquals(rFront - 1000, rDeep, 1e-6);
                }
            }
        }
    }

    @Test
    void pitchAlongBackSurfaceIsOuterWidthPlusGap() {
        Curve c = new Curve(ScreenCurveType.CONCAVE, 5000, 500, 100, 20);
        double gap = 700;
        List<Placement> ps = StructureCurveMath.placements(c, TOWER_1000, 3, gap);
        double dPhi = Math.toRadians(ps.get(1).yawDeg() - ps.get(0).yawDeg());
        assertEquals((1051 + gap) / c.backRadiusMm(), Math.abs(dPhi), 1e-9);
        Curve flat = new Curve(ScreenCurveType.FLAT, 0, 500, 100, 20);
        List<Placement> fp = StructureCurveMath.placements(flat, TOWER_1000, 3, gap);
        assertEquals(1051 + gap, fp.get(1).anchorX() - fp.get(0).anchorX(), 1e-9);
    }

    @Test
    void convexTowerIsShiftedBackBySagittaSoCornersStayBehindBackSurface() {
        Curve c = new Curve(ScreenCurveType.CONVEX, 4000, 500, 100, 12);
        double rb = c.backRadiusMm();
        double half = TOWER_1000.outerWidthMm() / 2;
        double e = StructureCurveMath.convexShiftMm(c, TOWER_1000);
        assertEquals(rb - Math.sqrt(rb * rb - half * half), e, 1e-9);
        Placement p = StructureCurveMath.placement(c, TOWER_1000, 1, 500, 0);
        double[] corner = p.toWorld(half, 0);
        double r = Math.hypot(corner[0] - c.centerX(), corner[1] - c.centerZ());
        assertTrue(r < rb, "угол передней плоскости внутри тыльной окружности");
        // без сдвига e угол касательной плоскости вылез бы за тыльную поверхность
        assertTrue(Math.hypot(rb, half) > rb);
        // передняя плоскость = Rb − e − зазор до экрана от центра кривизны
        assertEquals(rb - e - c.backClearanceMm(), Math.hypot(p.anchorX() - c.centerX(), p.anchorZ() - c.centerZ()),
                1e-6);
    }

    /** Инвариант на сетке параметров постановки: R 2,5…20 м, углы между кабинетами, зазоры,
     *  глубины 0,5…3 м, число башен 1…7 — ни одна точка footprint'а ни одной башни не в объёме
     *  экрана (плоские кабинеты + кольцевая полоса). Проверяются вершины и точки рёбер. */
    @Test
    void noTowerPointEverInsideScreenVolumeAcrossParameterGrid() {
        double[] radii = {2500, 3000, 4000, 5000, 7500, 10000, 15000, 20000};
        double[] cabinetWidths = {500, 1000};
        double[] cabinetDepths = {60, 100, 150, 450};
        int[] colsList = {4, 9, 16, 30};
        double[] depths = {500, 1000, 1500, 2000, 3000};
        double[] gaps = {500, 800, 1500};
        int[] towers = {1, 2, 3, 7};
        int checked = 0;
        for (ScreenCurveType type : ScreenCurveType.values()) {
            for (double r : type.isCurved() ? radii : new double[]{0}) {
                for (double w : cabinetWidths) {
                    for (double d : cabinetDepths) {
                        for (int cols : colsList) {
                            Curve c = new Curve(type, r, w, d, cols);
                            if (!c.valid()) {
                                continue;
                            }
                            for (double depth : depths) {
                                TowerSpec t = TOWER_1000.withDepth(depth);
                                for (double gap : gaps) {
                                    for (int n : towers) {
                                        assertClear(c, t, n, gap);
                                        checked++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 3000, "сетка действительно широкая: " + checked);
    }

    /** То же для изгиба, заданного углом между кабинетами (1…15°). */
    @Test
    void noTowerPointInsideScreenWhenCurveGivenByCabinetAngle() {
        for (ScreenCurveType type : new ScreenCurveType[]{ScreenCurveType.CONCAVE, ScreenCurveType.CONVEX}) {
            for (double deg = 1; deg <= 15; deg += 2) {
                double r = StructureCurveMath.radiusFromCabinetAngle(500, deg);
                Curve c = new Curve(type, r, 500, 100, 12);
                if (!c.valid() || (type == ScreenCurveType.CONVEX && c.backRadiusMm() <= 526)) {
                    continue;
                }
                for (double depth : new double[]{500, 1500, 3000}) {
                    assertClear(c, TOWER_1000.withDepth(depth), 3, 500);
                }
            }
        }
    }

    private static void assertClear(Curve c, TowerSpec t, int n, double gap) {
        assertTrue(StructureCurveMath.towersClearOfScreen(c, t, n, gap),
                () -> "вершина башни в экране: " + c + " " + t + " n=" + n + " gap=" + gap);
        for (Placement p : StructureCurveMath.placements(c, t, n, gap)) {
            for (Rect rect : StructureCurveMath.footprintLocal(t)) {
                double[][] k = rect.corners();
                for (int e = 0; e < 4; e++) {
                    double[] a = k[e];
                    double[] b = k[(e + 1) % 4];
                    for (int s = 0; s <= 12; s++) {
                        double f = s / 12.0;
                        double[] wpt = p.toWorld(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f);
                        assertFalse(StructureCurveMath.insideScreenVolume(c, wpt[0], wpt[1]),
                                () -> "точка ребра башни в кабинете: " + c + " " + t + " n=" + n + " gap=" + gap);
                        assertFalse(StructureCurveMath.insideScreenAnnulus(c, wpt[0], wpt[1]),
                                () -> "точка ребра башни в полосе экрана: " + c + " " + t + " n=" + n);
                    }
                }
            }
        }
    }

    @Test
    void screenVolumeTestRecognisesPointsInsideCabinets() {
        Curve c = new Curve(ScreenCurveType.CONVEX, 4000, 500, 100, 12);
        Placement col = StructureCurveMath.cabinetColumns(c).get(3);
        double[] inside = col.toWorld(0, -50);
        assertTrue(StructureCurveMath.insideScreenVolume(c, inside[0], inside[1]));
        assertTrue(StructureCurveMath.insideScreenAnnulus(c, inside[0], inside[1]));
        double[] behind = col.toWorld(0, -400);
        assertFalse(StructureCurveMath.insideScreenVolume(c, behind[0], behind[1]));
        assertFalse(StructureCurveMath.insideScreenAnnulus(c, behind[0], behind[1]));
    }

    @Test
    void footprintIsTwoPostsPlusEverySectionAndCoversOuterRectangle() {
        List<Rect> rects = StructureCurveMath.footprintLocal(new TowerSpec(1000, 51, 500, 1750));
        assertEquals(2 + 4, rects.size(), "2 столба + 4 секции (последняя неполная)");
        assertEquals(-525.5, rects.get(0).x0(), 1e-9);
        assertEquals(525.5, rects.get(1).x1(), 1e-9);
        assertEquals(-1750, rects.get(rects.size() - 1).z0(), 1e-9);
    }

    @Test
    void convexFlushTowersCollideAtOneSectionUpToAboutTenMetres() {
        // ориентир пользователя: выпуклый, башни вплотную (зазор 0) -- основания соседних башен
        // пересекаются уже при одной секции 500 мм для R примерно до 10 м
        for (double r : new double[]{2500, 4000, 6000, 8000, 10000}) {
            Curve c = new Curve(ScreenCurveType.CONVEX, r, 500, 100, 24);
            assertFalse(StructureCurveMath.collisions(c, TOWER_1000.withDepth(500), 3, 0).isEmpty(), "R=" + r);
        }
        // с подобранным зазором конфликта нет
        Curve c4 = new Curve(ScreenCurveType.CONVEX, 4000, 500, 100, 24);
        TowerSpec deep = TOWER_1000.withDepth(1500);
        double minGap = StructureCurveMath.minimalGapMm(c4, deep, 3);
        assertTrue(minGap >= StructureCurveMath.MIN_TOWER_GAP_MM);
        assertTrue(StructureCurveMath.collisions(c4, deep, 3, minGap).isEmpty());
        if (minGap > StructureCurveMath.MIN_TOWER_GAP_MM) {
            assertFalse(StructureCurveMath.collisions(c4, deep, 3, minGap - 20).isEmpty(),
                    "зазор — действительно минимальный (бисекция, шаг 10 мм)");
        }
    }

    /** R = 4 м, глубина 2 м, зазор 0,5 м: оси соседних башен сходятся к центру кривизны под
     *  углом ≈ 0,4 рад, внутренние боковины встречаются в ≈ 2,6 м от центра (по оси башни), а башня
     *  доходит до ≈ 1,56 м — пересечение. Минимальный зазор ≈ 1,49 м, допустимая глубина ≈ 0,95 м. */
    @Test
    void convexSmallRadiusDeepBaseCollidesAndReportsMinGapAndMaxDepth() {
        Curve c = new Curve(ScreenCurveType.CONVEX, 4000, 500, 100, 16);
        TowerSpec deep = TOWER_1000.withDepth(2000);
        assertFalse(StructureCurveMath.collisions(c, deep, 3, 500).isEmpty(), "R 4 м, глубина 2 м, зазор 0,5 м");
        double minGap = StructureCurveMath.minimalGapMm(c, deep, 3);
        assertEquals(1490, minGap, 20, "оценка по геометрии осей: tg(Δ/2) = 525,5 / 1556,6");
        double maxDepth = StructureCurveMath.maxDepthMm(c, deep, 3, 500);
        assertTrue(maxDepth > 0 && maxDepth < 2000, "допустимая глубина: " + maxDepth);
        assertEquals(953, maxDepth, 20, "оценка по геометрии осей: 3556,6 − 525,5 / tg(0,1993)");
        assertTrue(StructureCurveMath.collisions(c, deep.withDepth(maxDepth), 3, 500).isEmpty());
        assertFalse(StructureCurveMath.collisions(c, deep.withDepth(maxDepth + 5), 3, 500).isEmpty());

        StructureCurveMath.Report report = StructureCurveMath.analyze(
                new StructureCurveMath.Setup(true, c, deep, 3, 500));
        assertFalse(report.collisions().isEmpty());
        assertTrue(report.minGapMm() > 500);
        assertEquals(maxDepth, report.maxDepthMm(), 1e-9);
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("пересекаются")
                && w.contains("Минимальный зазор")), report.warnings().toString());
        assertTrue(report.clearOfScreen());
    }

    /** Башня глубже расстояния до центра кривизны (R 2,5 м, глубина 3 м): все башни проходят
     *  через окрестность центра и пересекаются при ЛЮБОМ зазоре — зазор не найден (NaN), но
     *  допустимая глубина при заданном зазоре всё равно посчитана. */
    @Test
    void towersDeeperThanCurvatureCenterCannotBeFixedByGap() {
        Curve c = new Curve(ScreenCurveType.CONVEX, 2500, 500, 100, 16);
        TowerSpec deep = TOWER_1000.withDepth(3000);
        assertTrue(Double.isNaN(StructureCurveMath.minimalGapMm(c, deep, 3)));
        StructureCurveMath.Report report = StructureCurveMath.analyze(new StructureCurveMath.Setup(true, c, deep, 3, 500));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("Подходящий зазор не найден")),
                report.warnings().toString());
        assertTrue(report.maxDepthMm() > 0 && report.maxDepthMm() < 3000);
    }

    @Test
    void concaveAndFlatTowersNeverCollide() {
        for (double r : new double[]{2500, 4000, 10000}) {
            Curve c = new Curve(ScreenCurveType.CONCAVE, r, 500, 100, 20);
            for (double depth : new double[]{500, 3000}) {
                for (double gap : new double[]{0, 500}) {
                    assertTrue(StructureCurveMath.collisions(c, TOWER_1000.withDepth(depth), 5, gap).isEmpty(),
                            "вогнутый: башни расходятся веером, R=" + r + " depth=" + depth + " gap=" + gap);
                }
            }
            assertEquals(Double.POSITIVE_INFINITY, StructureCurveMath.maxDepthMm(c, TOWER_1000, 3, 500));
        }
        Curve flat = new Curve(ScreenCurveType.FLAT, 0, 500, 100, 20);
        assertTrue(StructureCurveMath.collisions(flat, TOWER_1000.withDepth(3000), 5, 500).isEmpty());
    }

    @Test
    void satDetectsOverlapAndTreatsTouchingAsSeparate() {
        double[][] a = {{0, 0}, {10, 0}, {10, 10}, {0, 10}};
        double[][] b = {{5, 5}, {15, 5}, {15, 15}, {5, 15}};
        double[][] touching = {{10, 0}, {20, 0}, {20, 10}, {10, 10}};
        double[][] rotated = {{12, 5}, {17, 0}, {22, 5}, {17, 10}};
        double[][] rotatedIn = {{8, 5}, {13, 0}, {18, 5}, {13, 10}};
        assertTrue(StructureCurveMath.intersects(a, b));
        assertFalse(StructureCurveMath.intersects(a, touching));
        assertFalse(StructureCurveMath.intersects(a, rotated));
        assertTrue(StructureCurveMath.intersects(a, rotatedIn));
    }

    @Test
    void separateRuleAndEffectiveGap() {
        assertFalse(StructureCurveMath.separateTowers(ScreenCurveType.FLAT, 0), "прямой, зазор 0 — стена");
        assertTrue(StructureCurveMath.separateTowers(ScreenCurveType.FLAT, 500));
        assertTrue(StructureCurveMath.separateTowers(ScreenCurveType.CONCAVE, 0), "изогнутый — всегда раздельно");
        assertTrue(StructureCurveMath.separateTowers(ScreenCurveType.CONVEX, 0));
        assertEquals(0, StructureCurveMath.effectiveGapMm(ScreenCurveType.FLAT, 0));
        assertEquals(500, StructureCurveMath.effectiveGapMm(ScreenCurveType.CONCAVE, 0), "не меньше 0,5 м");
        assertEquals(500, StructureCurveMath.effectiveGapMm(ScreenCurveType.FLAT, 200));
        assertEquals(900, StructureCurveMath.effectiveGapMm(ScreenCurveType.CONVEX, 900));
    }

    @Test
    void suggestedTowerCountFitsAlongBackSurfaceAndEdgeMarginIsReported() {
        Curve flat = new Curve(ScreenCurveType.FLAT, 0, 500, 100, 12); // 6 м
        assertEquals(4, StructureCurveMath.suggestTowerCount(flat, TOWER_1000, 500), "(6000+500)/(1051+500)");
        assertEquals(1, StructureCurveMath.suggestTowerCount(new Curve(ScreenCurveType.FLAT, 0, 500, 100, 1),
                TOWER_1000, 500), "не меньше одной башни");
        double margin = StructureCurveMath.edgeMarginMm(flat, TOWER_1000, 4, 500);
        assertEquals((6000 - (4 * 1051 + 3 * 500)) / 2.0, margin, 1e-9);
        assertTrue(StructureCurveMath.edgeMarginMm(flat, TOWER_1000, 6, 500) < 0, "6 башен вылезают за край");
        double[] span = StructureCurveMath.towerScreenSpanMm(flat, TOWER_1000, 1, 500, 0);
        assertEquals(3000 - 525.5, span[0], 1e-9);
        assertEquals(3000 + 525.5, span[1], 1e-9);
    }
}
