package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import org.junit.jupiter.api.Test;

class StructurePickMathTest {

    @Test
    void centerPixelRayPointsFromEyeToCenter() {
        Vec3 eye = new Vec3(0, 0, 10);
        Vec3 center = new Vec3(0, 0, 0);
        Ray ray = StructurePickMath.cameraRay(eye, center, new Vec3(0, 1, 0), 45, 1.0,
                400, 300, 800, 600);
        Vec3 expected = center.minus(eye).normalized();
        assertEquals(expected.x(), ray.direction().x(), 1e-9);
        assertEquals(expected.y(), ray.direction().y(), 1e-9);
        assertEquals(expected.z(), ray.direction().z(), 1e-9);
    }

    @Test
    void topLeftPixelRayPointsLeftAndUp() {
        Vec3 eye = new Vec3(0, 0, 10);
        Vec3 center = new Vec3(0, 0, 0);
        Ray ray = StructurePickMath.cameraRay(eye, center, new Vec3(0, 1, 0), 90, 1.0,
                0, 0, 800, 600);
        // Смотрим вдоль -Z; левый-верхний пиксель (AWT y=0 -- верх экрана) должен дать луч
        // с отрицательным X (влево) и положительным Y (вверх).
        assertTrue(ray.direction().x() < 0);
        assertTrue(ray.direction().y() > 0);
    }

    @Test
    void rayHitsBoxDirectlyInFront() {
        Ray ray = new Ray(new Vec3(0, 0, 10), new Vec3(0, 0, -1));
        Double t = StructurePickMath.intersectAabb(ray, new Vec3(-1, -1, -1), new Vec3(1, 1, 1));
        assertNotNull(t);
        assertEquals(9.0, t, 1e-9);
    }

    @Test
    void rayMissesBoxOffToTheSide() {
        Ray ray = new Ray(new Vec3(100, 100, 10), new Vec3(0, 0, -1));
        Double t = StructurePickMath.intersectAabb(ray, new Vec3(-1, -1, -1), new Vec3(1, 1, 1));
        assertNull(t);
    }

    @Test
    void rayPicksNearerOfTwoOverlappingBoxesAlongItsPath() {
        Ray ray = new Ray(new Vec3(0, 0, 20), new Vec3(0, 0, -1));
        Vec3 nearMin = new Vec3(-1, -1, 4);
        Vec3 nearMax = new Vec3(1, 1, 6);
        Vec3 farMin = new Vec3(-1, -1, -6);
        Vec3 farMax = new Vec3(1, 1, -4);
        Double tNear = StructurePickMath.intersectAabb(ray, nearMin, nearMax);
        Double tFar = StructurePickMath.intersectAabb(ray, farMin, farMax);
        assertNotNull(tNear);
        assertNotNull(tFar);
        assertTrue(tNear < tFar, "ближний бокс должен давать меньшее t");
    }

    @Test
    void boxBehindRayOriginIsNotHit() {
        Ray ray = new Ray(new Vec3(0, 0, 0), new Vec3(0, 0, -1));
        Double t = StructurePickMath.intersectAabb(ray, new Vec3(-1, -1, 5), new Vec3(1, 1, 7));
        assertNull(t);
    }

    // ---- OBB: повёрнутые башни изогнутого экрана (запрос 2026-10-01) ----

    @Test
    void identityTransformIsExactlyAabb() {
        Ray ray = new Ray(new Vec3(0.3, 0.2, 10), new Vec3(0.01, 0, -1).normalized());
        Vec3 min = new Vec3(-1, -1, -1);
        Vec3 max = new Vec3(1, 1, 1);
        assertEquals(StructurePickMath.intersectAabb(ray, min, max),
                StructurePickMath.intersectObb(ray, min, max, StructurePickMath.YawTransform.IDENTITY));
        assertEquals(StructurePickMath.intersectAabb(ray, min, max),
                StructurePickMath.intersectObb(ray, min, max, null));
    }

    @Test
    void transformRoundTripsPointsAndKeepsRayLength() {
        StructurePickMath.YawTransform xf = new StructurePickMath.YawTransform(1500, -300, 27, 500, -400);
        Vec3 p = new Vec3(123, 45, -678);
        Vec3 back = xf.toLocal(xf.toWorld(p));
        assertEquals(p.x(), back.x(), 1e-9);
        assertEquals(p.y(), back.y(), 1e-9);
        assertEquals(p.z(), back.z(), 1e-9);
        // точка localOffset попадает ровно в anchor
        Vec3 anchor = xf.toWorld(new Vec3(500, 0, -400));
        assertEquals(1500, anchor.x(), 1e-9);
        assertEquals(-300, anchor.z(), 1e-9);
        Ray local = xf.toLocal(new Ray(new Vec3(0, 0, 0), new Vec3(0.6, 0, -0.8)));
        assertEquals(1.0, local.direction().length(), 1e-12, "поворот ортонормированный — t не искажается");
    }

    /** Клик по повёрнутой раме: тонкая стойка, повёрнутая на 30°, попадает по лучу, который
     *  проходит через её мировое положение, хотя её НЕповёрнутая AABB (как у прямого экрана) в
     *  этом месте луч бы не задела, — и наоборот. */
    @Test
    void rayHitsRotatedThinPostOnlyWhereItReallyIs() {
        StructurePickMath.YawTransform xf = new StructurePickMath.YawTransform(0, 0, 30, 0, 0);
        // локальная стойка: тонкая по x (50), длинная по z (1000) — как рама 500 мм "в глубину"
        Vec3 min = new Vec3(-25, 0, -1000);
        Vec3 max = new Vec3(25, 2000, 0);
        // середина стойки в мире: локальная (0, 1000, -500)
        Vec3 worldMid = xf.toWorld(new Vec3(0, 1000, -500));
        assertEquals(-250, worldMid.x(), 1e-9, "Rot(30°): x' = z·sin30 = −250");
        Ray down = new Ray(new Vec3(worldMid.x(), 5000, worldMid.z()), new Vec3(0, -1, 0));
        Double hit = StructurePickMath.intersectObb(down, min, max, xf);
        assertNotNull(hit, "луч сверху через мировую середину повёрнутой стойки");
        assertEquals(3000, hit, 1e-6);
        assertNull(StructurePickMath.intersectAabb(down, min, max), "без поворота стойка была бы в другом месте");
        Ray unrotatedSpot = new Ray(new Vec3(0, 5000, -500), new Vec3(0, -1, 0));
        assertNotNull(StructurePickMath.intersectAabb(unrotatedSpot, min, max));
        assertNull(StructurePickMath.intersectObb(unrotatedSpot, min, max, xf),
                "там, где стойка стояла бы у прямого экрана, повёрнутой стойки нет");
    }
}
