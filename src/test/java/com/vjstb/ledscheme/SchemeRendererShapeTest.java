package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Task #91/v1.5: треугольный кабинет теперь рисуется реальным прямоугольным
 *  треугольником (не декоративной меткой в углу) — прямой угол стоит в одном из
 *  4 углов ячейки по CabinetType.rotationDeg (0°=левый нижний, далее по часовой
 *  стрелке). Проверяем, что вершины полигона совпадают ровно с 3 из 4 углов
 *  прямоугольника ячейки, и что прямой угол оказывается там, где заявлено. */
class SchemeRendererShapeTest {

    private static Set<String> pointSet(Polygon p) {
        Set<String> s = new java.util.HashSet<>();
        for (int i = 0; i < p.npoints; i++) {
            s.add(p.xpoints[i] + "," + p.ypoints[i]);
        }
        return s;
    }

    @Test
    void zeroDegreesPutsRightAngleAtBottomLeft() {
        // Ячейка (10,20)-(110,120): TL(10,20) TR(110,20) BR(110,120) BL(10,120).
        // Прямой угол внизу слева => полигон = {TL, BR, BL} (без TR).
        Polygon p = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 0);
        assertTrue(p.npoints == 3);
        Set<String> pts = pointSet(p);
        assertTrue(pts.contains("10,20"));   // TL
        assertTrue(pts.contains("110,120")); // BR
        assertTrue(pts.contains("10,120"));  // BL
        assertTrue(!pts.contains("110,20")); // TR omitted
    }

    @Test
    void ninetyDegreesPutsRightAngleAtTopLeft() {
        // Прямой угол сверху слева => полигон = {BL, TR, TL} (без BR).
        Polygon p = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 90);
        Set<String> pts = pointSet(p);
        assertTrue(pts.contains("10,120"));  // BL
        assertTrue(pts.contains("110,20"));  // TR
        assertTrue(pts.contains("10,20"));   // TL
        assertTrue(!pts.contains("110,120")); // BR omitted
    }

    @Test
    void oneEightyDegreesPutsRightAngleAtTopRight() {
        // Прямой угол сверху справа => полигон = {TL, TR, BR} (без BL).
        Polygon p = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 180);
        Set<String> pts = pointSet(p);
        assertTrue(pts.contains("10,20"));   // TL
        assertTrue(pts.contains("110,20"));  // TR
        assertTrue(pts.contains("110,120")); // BR
        assertTrue(!pts.contains("10,120")); // BL omitted
    }

    @Test
    void twoSeventyDegreesPutsRightAngleAtBottomRight() {
        // Прямой угол снизу справа => полигон = {TR, BL, BR} (без TL).
        Polygon p = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 270);
        Set<String> pts = pointSet(p);
        assertTrue(pts.contains("110,20"));  // TR
        assertTrue(pts.contains("10,120"));  // BL
        assertTrue(pts.contains("110,120")); // BR
        assertTrue(!pts.contains("10,20"));  // TL omitted
    }

    @Test
    void arbitraryAngleSnapsToNearestQuarterTurn() {
        // 100° ближе к 90°, чем к 0°/180° — должен дать ту же геометрию, что и 90°.
        Polygon p90 = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 90);
        Polygon p100 = SchemeRenderer.trianglePolygon(10, 20, 100, 100, 100);
        assertTrue(pointSet(p90).equals(pointSet(p100)));
    }

    @Test
    void perCabinetRotationOverrideWinsOverTypeRotation() {
        // Task #92/v1.5: угол правится не только на уровне типа в библиотеке, но и
        // точечно по ячейке (радиальное меню "Угол") — переопределение должно
        // побеждать угол типа, а не наоборот.
        CabinetType type = new CabinetType();
        type.setRotationDeg(90);
        CabinetInstance cab = new CabinetInstance();
        assertEquals(90.0, SchemeRenderer.effectiveRotationDeg(cab, type));
        cab.setRotationOverride(270);
        assertEquals(270.0, SchemeRenderer.effectiveRotationDeg(cab, type));
        cab.setRotationOverride(null);
        assertEquals(90.0, SchemeRenderer.effectiveRotationDeg(cab, type));
    }

    @Test
    void connectionAnchorForTriangleIsItsCentroidNotRectCenter() {
        // Task #93/v1.5: точка привязки коммутации для треугольника — среднее его
        // трёх вершин, а НЕ геометрический центр прямоугольника ячейки (который
        // может лежать вне самого треугольника, если тот сильно скошен).
        java.awt.Point anchor = SchemeRenderer.cabinetConnectionAnchor(0, 0, 90, 90, CabinetShape.TRIANGLE, 0);
        // 0°: вершины (0,0),(90,90),(0,90) -> центроид (30,60).
        assertEquals(30, anchor.x);
        assertEquals(60, anchor.y);
        // Геометрический центр прямоугольника был бы (45,45) — заметно другая точка.
        assertTrue(anchor.x != 45 || anchor.y != 45);
    }

    @Test
    void connectionAnchorForRectangleIsCellCenter() {
        java.awt.Point anchor = SchemeRenderer.cabinetConnectionAnchor(10, 20, 80, 60, CabinetShape.RECTANGLE, 0);
        assertEquals(50, anchor.x);
        assertEquals(50, anchor.y);
    }

    @Test
    void nonSquareCellPreservesAspectRatioRegardlessOfRotation() {
        // Портретная ячейка (узкая, высокая) — треугольник любой ориентации должен
        // оставаться ВПИСАННЫМ РОВНО в эти же 30×90 границы, а не превращаться в
        // геометрию 90×30 (что случилось бы при буквальном повороте полигона).
        Polygon p = SchemeRenderer.trianglePolygon(0, 0, 30, 90, 90);
        Rectangle bounds = p.getBounds();
        assertTrue(bounds.width == 30 && bounds.height == 90);
    }
}
