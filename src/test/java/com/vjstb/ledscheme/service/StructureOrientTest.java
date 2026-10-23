package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureGizmoMath.Handle;
import com.vjstb.ledscheme.service.StructureOrient.OrientedBox;
import com.vjstb.ledscheme.service.StructureOrient.TowerPose;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.service.StructurePickMath.YawTransform;
import org.junit.jupiter.api.Test;

/**
 * Башня по всем осям (запрос 2026-10-22): матрица {@code Ry·Rx·Rz}, эйлеровы углы туда-обратно, поворот вокруг оси
 * мира через опорную точку, 3D-коробки (касание не пересечение, стык с допуском, наклон и высота), общее
 * преобразование picking/рендера и 3D-хит-тест гизмо (оси, кольца наклона и крена, ось высоты).
 */
class StructureOrientTest {

    private static final double EPS = 1e-6;
    private static final TowerSpec SPEC = new TowerSpec(1000, 51, 500, 1000);

    @Test
    void zeroPitchAndRollKeepTheOldPlanarYawTransform() {
        YawTransform old = new YawTransform(1500, -300, 27, 500, -400);
        Vec3 w = old.toWorld(new Vec3(10, 20, -30));
        double a = Math.toRadians(27);
        double x = 10 - 500;
        double z = -30 + 400;
        assertEquals(1500 + x * Math.cos(a) + z * Math.sin(a), w.x(), EPS);
        assertEquals(20, w.y(), EPS);
        assertEquals(-300 - x * Math.sin(a) + z * Math.cos(a), w.z(), EPS);
        Vec3 back = old.toLocal(w);
        assertEquals(10, back.x(), EPS);
        assertEquals(20, back.y(), EPS);
        assertEquals(-30, back.z(), EPS);
    }

    @Test
    void fullTransformRoundTripsAndLiftsByTheElevation() {
        YawTransform t = new YawTransform(100, -50, 33, 0, 0, 800, 40, -25);
        Vec3 local = new Vec3(120, 340, -210);
        Vec3 w = t.toWorld(local);
        Vec3 back = t.toLocal(w);
        assertEquals(local.x(), back.x(), 1e-6);
        assertEquals(local.y(), back.y(), 1e-6);
        assertEquals(local.z(), back.z(), 1e-6);
        assertEquals(800, t.toWorld(new Vec3(0, 0, 0)).y(), EPS, "начало башни — на высоте привязки");
        assertFalse(t.isIdentity());
    }

    @Test
    void quarterRollLaysTheTowerAlongTheScreenAndPitchAlongTheDepth() {
        // крен 90° вокруг оси Z: ось «вверх» (Y) уходит вдоль X мира — башня лежит вдоль экрана
        TowerPose roll = new TowerPose(0, 1000, 0, 0, 0, 90);
        double[] top = roll.toWorld(0, 3000, 0);
        assertEquals(-3000, top[0], 1e-6, "верх башни — на 3 м в сторону от привязки");
        assertEquals(1000, top[1], 1e-6, "на той же высоте: башня лежит горизонтально");
        // наклон 90° вокруг X: «вверх» уходит вдоль Z мира — вглубь/к зрителю
        TowerPose pitch = new TowerPose(0, 1000, 0, 0, 90, 0);
        double[] t2 = pitch.toWorld(0, 3000, 0);
        assertEquals(1000, t2[1], 1e-6);
        assertEquals(3000, Math.abs(t2[2]), 1e-6);
        assertTrue(roll.upright() == false && pitch.upright() == false);
        assertTrue(new TowerPose(1, 2, 3, 45, 0, 0).upright());
    }

    @Test
    void eulerAnglesRoundTripThroughTheMatrix() {
        for (double[] a : new double[][]{{0, 0, 0}, {30, 20, 10}, {-120, 45, -70}, {170, -60, 135}, {90, 80, -20}}) {
            double[][] m = new YawTransform(0, 0, a[0], 0, 0, 0, a[1], a[2]).rotation();
            double[] e = StructureOrient.euler(m);
            double[][] m2 = new YawTransform(0, 0, e[0], 0, 0, 0, e[1], e[2]).rotation();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(m[i][j], m2[i][j], 1e-9, "углы " + java.util.Arrays.toString(a));
                }
            }
        }
        // на полюсе наклона вырождение даёт нулевой крен, но поворот тот же
        double[][] pole = new YawTransform(0, 0, 25, 0, 0, 0, 90, 40).rotation();
        double[] e = StructureOrient.euler(pole);
        assertEquals(90, e[1], 1e-6);
        double[][] back = new YawTransform(0, 0, e[0], 0, 0, 0, e[1], e[2]).rotation();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(pole[i][j], back[i][j], 1e-9);
            }
        }
    }

    @Test
    void rotatingAboutAWorldAxisChangesOnlyTheMatchingAngleAndKeepsThePivotStill() {
        TowerPose p = new TowerPose(500, 300, -700, 20, 10, 5);
        double[] pivot = StructureGizmoMath.pivotLocal(SPEC);
        double[] before = p.toWorld(pivot[0], pivot[1], pivot[2]);

        TowerPose yawed = p.rotatedAbout(new double[]{0, 1, 0}, 30, pivot);
        assertEquals(50, yawed.yawDeg(), 1e-9);
        assertEquals(10, yawed.pitchDeg(), 1e-9);
        assertEquals(5, yawed.rollDeg(), 1e-9);

        TowerPose pitched = p.rotatedAbout(StructureGizmoMath.ringAxis(p, Handle.RING_PITCH), 25, pivot);
        assertEquals(20, pitched.yawDeg(), 1e-9);
        assertEquals(35, pitched.pitchDeg(), 1e-9);
        assertEquals(5, pitched.rollDeg(), 1e-9);

        TowerPose rolled = p.rotatedAbout(StructureGizmoMath.ringAxis(p, Handle.RING_ROLL), -40, pivot);
        assertEquals(20, rolled.yawDeg(), 1e-9);
        assertEquals(10, rolled.pitchDeg(), 1e-9);
        assertEquals(-35, rolled.rollDeg(), 1e-9);

        for (TowerPose r : new TowerPose[]{yawed, pitched, rolled}) {
            double[] after = r.toWorld(pivot[0], pivot[1], pivot[2]);
            assertEquals(before[0], after[0], 1e-6, "центр основания не сдвинулся");
            assertEquals(before[1], after[1], 1e-6);
            assertEquals(before[2], after[2], 1e-6);
        }
        TowerPose set = p.withAnglesKeepingPivot(pivot, 90, 0, 0);
        double[] after = set.toWorld(pivot[0], pivot[1], pivot[2]);
        assertEquals(before[2], after[2], 1e-6);
        assertEquals(90, set.yawDeg(), 1e-9);
    }

    @Test
    void boxesTouchWithoutOverlappingAndRespectHeightAndTilt() {
        OrientedBox a = OrientedBox.ofTower(new TowerPose(0, 0, 0, 0, 0, 0), 1051, 3000, 1000);
        OrientedBox flush = OrientedBox.ofTower(new TowerPose(1051, 0, 0, 0, 0, 0), 1051, 3000, 1000);
        assertFalse(a.overlaps(flush), "вплотную — касание, не пересечение");
        assertTrue(a.touches(flush, 15), "и это стык для стяжек");
        assertTrue(a.overlaps(OrientedBox.ofTower(new TowerPose(1000, 0, 0, 0, 0, 0), 1051, 3000, 1000)));
        // та же башня, поднятая выше верха первой: не мешает
        assertFalse(a.overlaps(OrientedBox.ofTower(new TowerPose(0, 3100, 0, 0, 0, 0), 1051, 3000, 1000)));
        assertFalse(a.touches(OrientedBox.ofTower(new TowerPose(0, 3100, 0, 0, 0, 0), 1051, 3000, 1000), 15));
        // лежащая на боку башня поверх: ложится на крышу первой (крен 90°, 3 м вдоль X), не пересекает её
        TowerPose lying = new TowerPose(1500, 3000 + 525, -500, 0, 0, 90);
        OrientedBox beam = OrientedBox.ofTower(lying, 1051, 3000, 1000);
        assertNotNull(beam);
        assertFalse(a.overlaps(beam), "лежит на уровне крыши — вплотную сверху");
        TowerPose sunk = new TowerPose(1500, 2800, -500, 0, 0, 90);
        assertTrue(a.overlaps(OrientedBox.ofTower(sunk, 1051, 3000, 1000)), "опустили ниже крыши — пересекает");
    }

    @Test
    void gizmoHandlesAreFoundInThreeDimensionsForATiltedTowerToo() {
        TowerPose pose = new TowerPose(0, 0, 0, 0, 0, 0);
        double[] p = StructureGizmoMath.pivotWorld(pose, SPEC); // (0, 0, −500)
        // луч сверху в центр основания — центр
        assertEquals(Handle.CENTER, StructureGizmoMath.hit3D(pose, SPEC, down(p[0], p[2])));
        // вдоль оси X и Z на уровне основания
        assertEquals(Handle.AXIS_X, StructureGizmoMath.hit3D(pose, SPEC, down(p[0] + 800, p[2])));
        assertEquals(Handle.AXIS_Z, StructureGizmoMath.hit3D(pose, SPEC, down(p[0], p[2] + 900)));
        // боковой луч сквозь ось «вверх» на высоте 1,5 м
        assertEquals(Handle.AXIS_Y, StructureGizmoMath.hit3D(pose, SPEC,
                new Ray(new Vec3(p[0] + 5000, p[1] + 1500, p[2]), new Vec3(-1, 0, 0))));
        double r = StructureGizmoMath.ringRadius(SPEC);
        // кольцо наклона — в вертикальной плоскости ⟂ оси X: точка (0, r, 0) относительно центра; луч вдоль X сквозь неё
        assertEquals(Handle.RING_PITCH, StructureGizmoMath.hit3D(pose, SPEC,
                new Ray(new Vec3(p[0] + 5000, p[1] + r * 0.7, p[2] + r * 0.7), new Vec3(-1, 0, 0))));
        // кольцо крена — в плоскости ⟂ оси Z: луч вдоль Z сквозь точку (r·0,7; r·0,7; 0)
        assertEquals(Handle.RING_ROLL, StructureGizmoMath.hit3D(pose, SPEC,
                new Ray(new Vec3(p[0] + r * 0.7, p[1] + r * 0.7, p[2] + 5000), new Vec3(0, 0, -1))));
        // кольцо рыскания — горизонтальное, 45° между осями
        double d = r / Math.sqrt(2);
        assertEquals(Handle.RING, StructureGizmoMath.hit3D(pose, SPEC, down(p[0] + d, p[2] + d)));
        assertEquals(Handle.NONE, StructureGizmoMath.hit3D(pose, SPEC, down(p[0] + 4000, p[2] + 4000)));

        // та же башня на боку и выше: гизмо переехало вместе с центром основания
        TowerPose lying = new TowerPose(2000, 1500, 0, 30, 0, 90);
        double[] c = StructureGizmoMath.pivotWorld(lying, SPEC);
        assertEquals(Handle.CENTER, StructureGizmoMath.hit3D(lying, SPEC,
                new Ray(new Vec3(c[0], c[1] + 5000, c[2]), new Vec3(0, -1, 0))));
    }

    @Test
    void planeAndAngleHelpersMeasureTheRotationDrag() {
        Ray ray = new Ray(new Vec3(100, 5000, 200), new Vec3(0, -1, 0));
        double[] hit = StructureGizmoMath.rayPlane(ray, new double[]{0, 0, 0}, new double[]{0, 1, 0});
        assertEquals(100, hit[0], EPS);
        assertEquals(0, hit[1], EPS);
        assertEquals(200, hit[2], EPS);
        assertTrue(StructureGizmoMath.rayPlane(new Ray(new Vec3(0, 5, 0), new Vec3(1, 0, 0)), new double[]{0, 0, 0},
                new double[]{0, 1, 0}) == null);
        // правая тройка вокруг +Y: x → −z
        assertEquals(90, StructureGizmoMath.angleAbout(new double[]{0, 1, 0}, new double[]{1, 0, 0},
                new double[]{0, 0, -1}), 1e-9);
        // высота на вертикальной прямой: луч сбоку на высоте 700 → 700
        Double h = StructureGizmoMath.heightOnVerticalLine(new Ray(new Vec3(3000, 700, 0), new Vec3(-1, 0, 0)), 0, 0);
        assertEquals(700, h, 1e-6);
    }

    private static Ray down(double x, double z) {
        return new Ray(new Vec3(x, 20_000, z), new Vec3(0, -1, 0));
    }
}
