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
}
