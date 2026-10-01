package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ScreenCurveType;
import org.junit.jupiter.api.Test;

/**
 * Маппинг ползунков 3D-редактора конструктива (запрос пользователя 2026-10-01: радиус, зазор и
 * число башен «как в виджете ползунками») — {@link StructureSliderMath}: границы и шаги,
 * зажим зазора по правилу «изогнутый — от 500 мм, прямой 0 = стена», максимум числа башен и
 * подписи с производными величинами. Без Swing/GL.
 */
class StructureSliderMathTest {

    @Test
    void radiusSliderCovers2point5To30MetresInHalfMetreSteps() {
        assertEquals(55, StructureSliderMath.radiusTickCount());
        assertEquals(2_500, StructureSliderMath.tickToRadiusMm(0));
        assertEquals(30_000, StructureSliderMath.tickToRadiusMm(55));
        assertEquals(15, StructureSliderMath.radiusToTick(10_000), "R = 10 м экрана пользователя");
        for (int t = 0; t <= 55; t++) {
            assertEquals(t, StructureSliderMath.radiusToTick(StructureSliderMath.tickToRadiusMm(t)), "тик " + t);
        }
        assertEquals(0, StructureSliderMath.radiusToTick(1_000), "меньше диапазона — к краю");
        assertEquals(55, StructureSliderMath.radiusToTick(50_000), "больше диапазона — к краю");
        assertEquals(0, StructureSliderMath.radiusToTick(Double.NaN));
        assertEquals(15, StructureSliderMath.radiusToTick(10_200), "не кратное — к ближайшему шагу");
        assertEquals(30_000, StructureSliderMath.tickToRadiusMm(99), "тик за пределами зажат");
    }

    @Test
    void gapIsClampedTo500ForCurvedAndZeroMeansWallOnlyForFlat() {
        assertEquals(10, StructureSliderMath.gapTickCount(), "0…5 м шагом 500 мм");
        assertEquals(0, StructureSliderMath.minGapTick(ScreenCurveType.FLAT));
        assertEquals(1, StructureSliderMath.minGapTick(ScreenCurveType.CONCAVE));
        assertEquals(1, StructureSliderMath.minGapTick(ScreenCurveType.CONVEX));
        assertEquals(0, StructureSliderMath.tickToGapMm(0, ScreenCurveType.FLAT), "прямой, 0 — стена");
        assertEquals(500, StructureSliderMath.tickToGapMm(0, ScreenCurveType.CONCAVE), "изогнутый — не меньше 500");
        assertEquals(1500, StructureSliderMath.tickToGapMm(3, ScreenCurveType.CONVEX));
        assertEquals(5000, StructureSliderMath.tickToGapMm(42, ScreenCurveType.FLAT));
        assertEquals(500, StructureSliderMath.clampGapMm(ScreenCurveType.FLAT, 300), "прямой с зазором — от 500");
        assertEquals(0, StructureSliderMath.clampGapMm(ScreenCurveType.FLAT, 0));
        assertEquals(500, StructureSliderMath.clampGapMm(ScreenCurveType.CONCAVE, 0));
        assertEquals(1, StructureSliderMath.gapToTick(500));
        assertEquals(1, StructureSliderMath.gapToTick(600), "введённое в «Сетапе» не кратное — ближайший шаг");
        assertEquals(10, StructureSliderMath.gapToTick(20_000));
    }

    @Test
    void towerCountSliderMaxIsTwiceAutoButAtLeastTwelveAndAtMost200() {
        assertEquals(18, StructureSliderMath.maxTowerCount(9));
        assertEquals(12, StructureSliderMath.maxTowerCount(2));
        assertEquals(12, StructureSliderMath.maxTowerCount(0));
        assertEquals(200, StructureSliderMath.maxTowerCount(150));
        assertEquals(0, StructureSliderMath.clampTowerCount(-3, 18));
        assertEquals(18, StructureSliderMath.clampTowerCount(40, 18));
    }

    @Test
    void labelsShowDerivedAnglesAndAutoTowerCount() {
        String byRadius = StructureSliderMath.radiusLabel(true, false, 10_000, 2.87, 80.2);
        assertTrue(byRadius.startsWith("Радиус"), byRadius);
        assertTrue(byRadius.contains("между кабинетами") && byRadius.contains("дуга"), byRadius);
        String byAngle = StructureSliderMath.radiusLabel(true, true, 10_000, 2.87, 80.2);
        assertTrue(byAngle.startsWith("Угол"), "экран задан углом — угол первым: " + byAngle);
        assertTrue(byAngle.contains("R "), byAngle);
        assertTrue(StructureSliderMath.radiusLabel(false, false, 10_000, 0, 0).contains("прямой"));
        assertTrue(StructureSliderMath.gapLabel(false, 0).contains("стена"));
        assertTrue(StructureSliderMath.gapLabel(true, 500).contains("500"));
        assertTrue(StructureSliderMath.towerLabel(true, 0, 9).contains("авто (9)"));
        assertEquals("Башен: 7", StructureSliderMath.towerLabel(true, 7, 7));
        assertTrue(StructureSliderMath.towerLabel(false, 0, 15).contains("15 столбов"));
        assertFalse(StructureSliderMath.radiusLabel(true, false, 200, Double.NaN, Double.NaN).contains("NaN"));
    }
}
