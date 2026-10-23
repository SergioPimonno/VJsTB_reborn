package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureGizmoMath.Handle;
import com.vjstb.ledscheme.service.StructureGizmoMath.RefLine;
import com.vjstb.ledscheme.service.StructureGizmoMath.Snap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Гизмо башни и линии привязки (запрос 2026-10-22): ручки (центр, оси X/Z, кольцо вращения) определяются по
 * точке на земле в системе башни — для любого угла; перенос вдоль оси; с Shift кромки и осевые линии башни
 * прилипают к параллельным линиям других башен и экрана, угол — к направлениям линий и шагу 15°.
 */
class StructureGizmoMathTest {

    private static final TowerSpec SPEC = new TowerSpec(1000, 51, 500, 1000);
    private static final double EPS = 1e-6;

    @Test
    void handlesAreRecognisedAroundTheCentreOfTheFootprint() {
        Placement p = new Placement(0, 0, 0, 0); // центр контура (0, −500)
        assertEquals(Handle.CENTER, StructureGizmoMath.hit(p, SPEC, 0, -500));
        assertEquals(Handle.CENTER, StructureGizmoMath.hit(p, SPEC, 100, -450));
        assertEquals(Handle.AXIS_X, StructureGizmoMath.hit(p, SPEC, 900, -500), "ось X — вдоль экрана");
        assertEquals(Handle.AXIS_X, StructureGizmoMath.hit(p, SPEC, -900, -540));
        assertEquals(Handle.AXIS_Z, StructureGizmoMath.hit(p, SPEC, 0, -1500), "ось Z — вглубь башни");
        assertEquals(Handle.AXIS_Z, StructureGizmoMath.hit(p, SPEC, 60, 400));
        double r = StructureGizmoMath.ringRadius(SPEC);
        double d = r / Math.sqrt(2);
        assertEquals(Handle.RING, StructureGizmoMath.hit(p, SPEC, d, -500 + d), "кольцо между осями");
        assertEquals(Handle.NONE, StructureGizmoMath.hit(p, SPEC, d + 600, -500 + d + 600));
        assertEquals(Handle.NONE, StructureGizmoMath.hit(p, SPEC, 400, -500 + 400), "внутри кольца вне осей — не ручка");
    }

    @Test
    void handlesFollowTheTowerWhateverItsAngleAndPosition() {
        for (double yaw : new double[]{0, 37, 90, -120, 180}) {
            Placement p = new Placement(0, 3000, -800, yaw);
            double[] c = StructureGizmoMath.centerWorld(p, SPEC);
            assertEquals(Handle.CENTER, StructureGizmoMath.hit(p, SPEC, c[0], c[1]), "yaw=" + yaw);
            double[] ux = StructureGizmoMath.axisDir(p, true);
            double[] uz = StructureGizmoMath.axisDir(p, false);
            assertEquals(Handle.AXIS_X, StructureGizmoMath.hit(p, SPEC, c[0] + ux[0] * 800, c[1] + ux[1] * 800), "yaw=" + yaw);
            assertEquals(Handle.AXIS_Z, StructureGizmoMath.hit(p, SPEC, c[0] + uz[0] * 900, c[1] + uz[1] * 900), "yaw=" + yaw);
            double r = StructureGizmoMath.ringRadius(SPEC);
            double a = Math.PI / 4;
            double rx = (ux[0] * Math.cos(a) + uz[0] * Math.sin(a)) * r;
            double rz = (ux[1] * Math.cos(a) + uz[1] * Math.sin(a)) * r;
            assertEquals(Handle.RING, StructureGizmoMath.hit(p, SPEC, c[0] + rx, c[1] + rz), "yaw=" + yaw);
        }
    }

    @Test
    void axisDragIsConstrainedToTheAxisInTheWorld() {
        Placement p = new Placement(0, 0, 0, 90);
        double[] ux = StructureGizmoMath.axisDir(p, true);
        assertEquals(0, ux[0], EPS);
        assertEquals(-1, ux[1], EPS, "после поворота на 90° ось X башни смотрит вдоль −Z мира");
        double[] moved = StructureGizmoMath.constrain(p, Handle.AXIS_X, 100, 100);
        assertEquals(0, moved[0], EPS);
        assertEquals(100, moved[1], EPS, "проекция (100, 100) на ось (0, −1) — вектор (0, 100)");
        double[] free = StructureGizmoMath.constrain(p, Handle.CENTER, 100, 100);
        assertEquals(100, free[0], EPS);
        assertEquals(100, free[1], EPS);
        double[] z = StructureGizmoMath.constrain(new Placement(0, 0, 0, 0), Handle.AXIS_Z, 70, 30);
        assertEquals(0, z[0], EPS);
        assertEquals(30, z[1], EPS);
    }

    @Test
    void towerAndScreenProvideTheirEdgeAndCentreLines() {
        assertEquals(6, StructureGizmoMath.towerLines(new Placement(0, 10, 20, 30), SPEC).size());
        List<RefLine> screen = StructureGizmoMath.flatScreenLines(new Placement(0, 0, 0, 0), 6000, 100);
        assertEquals(4, screen.size());
        assertEquals(0, screen.get(0).signedDistance(2000, 0), EPS, "плоскость лица z = 0");
        assertEquals(0, screen.get(1).signedDistance(500, -100), EPS, "тыл z = −100");
        assertEquals(0, screen.get(3).signedDistance(6000, 777), EPS, "правый торец x = W");
    }

    @Test
    void moveSnapsTheEdgeOfTheTowerToAParallelReferenceLine() {
        TowerSpec spec = SPEC;
        RefLine wall = new RefLine(1000, 0, 0, 1); // вертикальная линия x = 1000 (вдоль Z)
        // правая кромка башни стоит на x = 480 + 525,5 = 1005,5 — в 5,5 мм от линии
        Snap s = StructureGizmoMath.snapMove(new Placement(0, 480, 0, 0), spec, List.of(wall), 150);
        assertTrue(s.snapped());
        assertEquals(474.5, s.placement().anchorX(), 1e-6);
        assertEquals(0, s.placement().anchorZ(), EPS);
        // осевая линия по центру тоже притягивается, если она ближе всего
        Snap centre = StructureGizmoMath.snapMove(new Placement(0, 1020, 0, 0), spec, List.of(wall), 150);
        assertEquals(1000, centre.placement().anchorX(), 1e-6, "центр башни встал на линию");
        // далеко — не прилипает
        assertFalse(StructureGizmoMath.snapMove(new Placement(0, 0, 0, 0), spec, List.of(wall), 150).snapped());
        // горизонтальная линия z = −1200 (вдоль X): задняя кромка (−1000 + z) притягивается
        RefLine back = new RefLine(0, -1200, 1, 0);
        Snap z = StructureGizmoMath.snapMove(new Placement(0, 0, -150, 0), spec, List.of(back), 150);
        assertEquals(-200, z.placement().anchorZ(), 1e-6);
        assertEquals(0, z.placement().anchorX(), EPS);
    }

    @Test
    void moveSnapWorksForARotatedTowerAndBothAxesTogether() {
        RefLine x = new RefLine(2000, 0, 0, 1);
        RefLine z = new RefLine(0, 500, 1, 0);
        Placement cand = new Placement(0, 1480, 530, 0); // правая кромка 2005,5; передняя z = 530
        Snap s = StructureGizmoMath.snapMove(cand, SPEC, List.of(x, z), 150);
        assertEquals(2, s.active().size(), "обе оси привязались");
        assertEquals(1474.5, s.placement().anchorX(), 1e-6);
        assertEquals(500, s.placement().anchorZ(), 1e-6);

        // башня повёрнута на 90°: её ось X смотрит вдоль −Z мира, и привязка идёт по ней
        Placement turned = new Placement(0, 800, 300, 90);
        Snap t = StructureGizmoMath.snapMove(turned, SPEC, List.of(new RefLine(0, 400, 1, 0)), 150);
        assertTrue(t.snapped());
        double[] c = StructureGizmoMath.centerWorld(t.placement(), SPEC);
        assertTrue(Math.abs(c[1] - 400) < 1e-6 || Math.abs(Math.abs(c[1] - 400) - 525.5) < 1e-6
                || Math.abs(Math.abs(c[1] - 400) - 500) < 1e-6, "центр или кромка на линии: " + c[1]);
    }

    @Test
    void angleSnapsToTheDirectionOfAReferenceLineOrToTheStep() {
        RefLine horizontal = new RefLine(0, 0, 1, 0);
        Placement cand = new Placement(0, 100, 200, 4);
        double[] centre = StructureGizmoMath.centerLocal(SPEC);
        double[] before = cand.toWorld(centre[0], centre[1]);
        Snap s = StructureGizmoMath.snapAngle(cand, centre[0], centre[1], List.of(horizontal), 6, 15);
        assertEquals(0, s.placement().yawDeg(), 1e-9, "ось стала параллельной опорной линии");
        assertTrue(s.snapped());
        double[] after = s.placement().toWorld(centre[0], centre[1]);
        assertEquals(before[0], after[0], 1e-6);
        assertEquals(before[1], after[1], 1e-6);

        // 4° от 0 → 0; 80° → перпендикуляр (90°)
        assertEquals(90, StructureGizmoMath.snapAngle(new Placement(0, 0, 0, 84), centre[0], centre[1],
                List.of(horizontal), 6, 15).placement().yawDeg(), 1e-9);
        // линия под 30°: башня на −27° поворачивается к −30° (ось X параллельна линии)
        RefLine diagonal = new RefLine(0, 0, Math.cos(Math.toRadians(30)), Math.sin(Math.toRadians(30)));
        assertEquals(-30, StructureGizmoMath.snapAngle(new Placement(0, 0, 0, -27), centre[0], centre[1],
                List.of(diagonal), 6, 15).placement().yawDeg(), 1e-9);
        // далеко от всех линий — шаг 15°
        Snap step = StructureGizmoMath.snapAngle(new Placement(0, 0, 0, 38), centre[0], centre[1],
                List.of(horizontal), 6, 15);
        assertEquals(45, step.placement().yawDeg(), 1e-9);
        assertFalse(step.snapped());
    }
}
