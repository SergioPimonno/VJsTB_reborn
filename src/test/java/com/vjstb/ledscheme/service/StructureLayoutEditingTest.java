package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureLayoutMath.Side;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Математика и операции 3D-редактора раскладки конструктива (запрос 2026-10-22, трек B):
 * перетаскивание башни по земле (луч → плоскость), поворот вокруг центра контура, прилипание
 * «встык» при перетаскивании, стык экранов по краям (в том числе изогнутых), расстановка экранов
 * группы в ряд, башня «встык» к выбранной, высота экрана, состав экранов в окне редактора. Основы
 * ручной расстановки — {@code StructureManualLayoutTest}.
 */
class StructureLayoutEditingTest {

    private static final TowerSpec SPEC = new TowerSpec(1000, 51, 500, 1000);

    private AppModel model;
    private CabinetType type;

    private Screen screen(Path dir, String name, int rows, int cols) {
        if (model == null) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
            CabinetType ct = new CabinetType();
            ct.setName("Test");
            ct.setWidthMm(500);
            ct.setHeightMm(500);
            ct.setResolutionWidth(128);
            ct.setResolutionHeight(128);
            ct.setWeightKg(10);
            type = model.addCabinetType(ct);
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
        }
        Screen s = model.addScreen(name, type.getId(), rows, cols, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    private void calc(Screen s, ScreenCurveType curve, double gap, int towers) {
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 3, 2, 1, 500, 0.6,
                null, null, null, 0, null, curve, 10_000, false, gap, towers);
    }

    @Test
    void rayHitsTheGroundPlaneInFrontOfTheCameraOnly() {
        StructurePickMath.Ray down = new StructurePickMath.Ray(new StructurePickMath.Vec3(100, 1000, 200),
                new StructurePickMath.Vec3(1, -1, 0).normalized());
        double[] p = StructureLayoutMath.rayGroundPoint(down, 0);
        assertEquals(1100, p[0], 1e-6);
        assertEquals(200, p[1], 1e-6);
        assertNull(StructureLayoutMath.rayGroundPoint(new StructurePickMath.Ray(
                new StructurePickMath.Vec3(0, 1000, 0), new StructurePickMath.Vec3(1, 0, 0)), 0), "параллельно земле");
        assertNull(StructureLayoutMath.rayGroundPoint(new StructurePickMath.Ray(
                new StructurePickMath.Vec3(0, 1000, 0), new StructurePickMath.Vec3(0, 1, 0)), 0), "земля позади");
    }

    @Test
    void rotatingAboutTheFootprintCentreKeepsTheCentreStill() {
        Placement p = new Placement(2, 3000, -1200, 20);
        double[] before = p.toWorld(0, -500);
        Placement turned = StructureLayoutMath.rotateAbout(p, 0, -500, 110);
        double[] after = turned.toWorld(0, -500);
        assertEquals(before[0], after[0], 1e-6);
        assertEquals(before[1], after[1], 1e-6);
        assertEquals(110, turned.yawDeg(), 1e-9);
        assertEquals(2, turned.index());
        assertEquals(-165, StructureLayoutMath.snapAngle(-160, 15), 1e-9);
        assertEquals(90, StructureLayoutMath.snapAngle(94, 15), 1e-9);
        assertEquals(94, StructureLayoutMath.snapAngle(94, 0), 1e-9);
    }

    @Test
    void draggedTowerSnapsFlushWhenCloseAndAlignedButNotWhenFarOrTurnedAway() {
        Placement target = new Placement(0, 0, 0, 0);
        List<StructureLayoutMath.SnapTarget> targets = List.of(new StructureLayoutMath.SnapTarget(target, SPEC));
        Placement exact = StructureLayoutMath.attachTowerFlush(target, SPEC, Side.RIGHT, SPEC, Side.LEFT, 80, 0);
        // отодвинули на 60 мм и довернули на 4° — прилипает к точному положению
        Placement nearby = new Placement(0, exact.anchorX() + 60, exact.anchorZ(), exact.yawDeg() + 4);
        Placement snapped = StructureLayoutMath.snapFlush(nearby, SPEC, targets, 150, 7);
        assertNotNull(snapped);
        assertEquals(exact.anchorX(), snapped.anchorX(), 1e-6, "точно встык, вдоль стороны — где отпустили");
        assertEquals(exact.yawDeg(), snapped.yawDeg(), 1e-6);
        assertFalse(StructureCurveMath.anyIntersect(StructureCurveMath.worldRects(target, SPEC),
                StructureCurveMath.worldRects(snapped, SPEC)));
        assertNull(StructureLayoutMath.snapFlush(new Placement(0, exact.anchorX() + 400, exact.anchorZ(),
                exact.yawDeg()), SPEC, targets, 150, 7), "далеко — не прилипает");
        assertNull(StructureLayoutMath.snapFlush(new Placement(0, exact.anchorX(), exact.anchorZ(),
                exact.yawDeg() + 25), SPEC, targets, 150, 7), "угол далёк от стыковочного — не прилипает");
        // под 90° тоже прилипает
        Placement quarter = StructureLayoutMath.attachTowerFlush(target, SPEC, Side.RIGHT, SPEC, Side.FRONT, 0, 0);
        Placement q = StructureLayoutMath.snapFlush(new Placement(0, quarter.anchorX() + 40, quarter.anchorZ() - 30,
                quarter.yawDeg() - 3), SPEC, targets, 150, 7);
        assertNotNull(q);
        assertEquals(-90, q.yawDeg(), 1e-6);
        assertEquals(quarter.anchorX(), q.anchorX(), 1e-6);
    }

    @Test
    void generalEdgeAttachMatchesTheFlatOneAndWorksForCurvedScreens(@TempDir Path dir) {
        Screen a = screen(dir, "A", 2, 12);
        Screen b = screen(dir, "B", 2, 8);
        Placement ref = StructureLayoutMath.screenEdgePose(a, type, true);
        assertEquals(6000, ref.anchorX(), 1e-6);
        assertEquals(0, ref.yawDeg(), 1e-9);
        Placement left = StructureLayoutMath.screenEdgePose(b, type, false);
        Placement viaEdges = StructureLayoutMath.attachScreenByEdges(ref, true, left, 120, true);
        Placement flat = StructureLayoutMath.attachScreen(new Placement(0, 0, 0, 0), 6000, true, 4000, 120, true);
        assertEquals(flat.anchorX(), viaEdges.anchorX(), 1e-6);
        assertEquals(flat.anchorZ(), viaEdges.anchorZ(), 1e-6);
        assertEquals(flat.yawDeg(), viaEdges.yawDeg(), 1e-6);
        Placement leftSide = StructureLayoutMath.attachScreenByEdges(
                StructureLayoutMath.screenEdgePose(a, type, false), false,
                StructureLayoutMath.screenEdgePose(b, type, true), 90, true);
        Placement flatLeft = StructureLayoutMath.attachScreen(new Placement(0, 0, 0, 0), 6000, false, 4000, 90, true);
        assertEquals(flatLeft.anchorX(), leftSide.anchorX(), 1e-6);
        assertEquals(flatLeft.anchorZ(), leftSide.anchorZ(), 1e-6);
        assertEquals(flatLeft.yawDeg(), leftSide.yawDeg(), 1e-6);

        // вогнутый экран: край — край крайней колонны с её поворотом, а не (W, 0)
        calc(a, ScreenCurveType.CONCAVE, 500, 0);
        Placement curvedEdge = StructureLayoutMath.screenEdgePose(a, type, true);
        assertTrue(Math.abs(curvedEdge.yawDeg()) > 1, "у изогнутого экрана край повёрнут");
        assertTrue(curvedEdge.anchorX() < 6000, "хорда короче развёрнутой ширины");
    }

    @Test
    void attachScreenOperationPlacesTheMoverAtNinetyDegreesAndIsOneUndoStep(@TempDir Path dir) {
        Screen a = screen(dir, "L1", 2, 12);
        Screen b = screen(dir, "L2", 2, 8);
        int depth = model.undoDepth();
        model.attachStructureScreen(b, a, true, 90, true);
        assertEquals(depth + 1, model.undoDepth());
        assertEquals(6000, b.getStructurePoseXMm(), 1e-6);
        assertEquals(0, b.getStructurePoseZMm(), 1e-6);
        assertEquals(-90, b.getStructurePoseYawDeg(), 1e-6);
        model.attachStructureScreen(b, b, true, 90, true); // сам к себе — игнорируется
        assertEquals(depth + 1, model.undoDepth());
        model.undo();
        assertEquals(0, b.getStructurePoseXMm(), 1e-6);
    }

    @Test
    void arrangingInLineLaysScreensEndToEndWithAGapAndGroupNeedsItOnlyWhileAllPosesAreZero(@TempDir Path dir) {
        Screen a = screen(dir, "A", 2, 12);
        Screen b = screen(dir, "B", 2, 8);
        Screen c = screen(dir, "C", 2, 6);
        List<Screen> trio = List.of(a, b, c);
        assertTrue(StructureLayoutMath.groupNeedsArranging(trio));
        assertFalse(StructureLayoutMath.groupNeedsArranging(List.of(a)));
        model.arrangeStructureScreensInLine(trio, 1000);
        assertEquals(0, a.getStructurePoseXMm(), 1e-6);
        assertEquals(7000, b.getStructurePoseXMm(), 1e-6, "6000 + зазор 1000");
        assertEquals(12000, c.getStructurePoseXMm(), 1e-6, "7000 + 4000 + 1000");
        assertEquals(0, c.getStructurePoseYawDeg(), 1e-9);
        assertFalse(StructureLayoutMath.groupNeedsArranging(trio));
        model.undo();
        assertTrue(StructureLayoutMath.groupNeedsArranging(trio));
    }

    @Test
    void towerAddedFlushStandsAtNinetyDegreesToTheTargetAndTouchesIt(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        int k = model.addStructureTowerFlush(s, 0, Side.RIGHT, true);
        assertEquals(2, k);
        StructureTowerPlacement first = s.towerPlacementOf(0);
        StructureTowerPlacement added = s.towerPlacementOf(2);
        assertEquals(StructureLayoutMath.normalizeDeg(first.getYawDeg() - 90), added.getYawDeg(), 1e-6);
        List<List<double[][]>> rects = StructureLayoutMath.towerRects(s, type, model.getWorkspace());
        assertFalse(StructureCurveMath.anyIntersect(rects.get(0), rects.get(2)), "встык — касание, не пересечение");
        // и в самом деле вплотную: сдвиг цели на 5 мм к новой даёт пересечение
        model.setStructureTowerPlacement(s, 2, added.getAnchorXMm() - 5, added.getAnchorZMm(), added.getYawDeg(),
                true, true);
        rects = StructureLayoutMath.towerRects(s, type, model.getWorkspace());
        assertTrue(StructureCurveMath.anyIntersect(rects.get(0), rects.get(2)));
        assertEquals(-1, model.addStructureTowerFlush(s, 9, Side.RIGHT, true));
        Screen auto = screen(dir, "Auto", 2, 12);
        assertEquals(-1, model.addStructureTowerFlush(auto, 0, Side.RIGHT, true));
    }

    @Test
    void elevationIsAnUndoableSingleFieldEditAndViewScreensFollowTheEligibleGroup(@TempDir Path dir) {
        Screen a = screen(dir, "A", 2, 12);
        Screen b = screen(dir, "B", 2, 8);
        model.setStructureScreenElevation(a, 1500);
        assertEquals(1500, a.getStructureScreenElevationMm(), 1e-9);
        int depth = model.undoDepth();
        model.setStructureScreenElevation(a, 1500);
        assertEquals(depth, model.undoDepth(), "то же значение — не правка");
        model.setStructureScreenElevation(a, -5);
        assertEquals(0, a.getStructureScreenElevationMm(), 1e-9, "отрицательной высоты нет");

        Scene scene = model.getCurrentScene();
        assertEquals(List.of(a), StructureGroupCalc.viewScreens(scene, a), "вне группы — один экран");
        ScreenGroup g = new ScreenGroup("G");
        scene.getScreenGroups().add(g);
        a.setGroupId(g.getId());
        assertEquals(List.of(a), StructureGroupCalc.viewScreens(scene, a), "в группе один экран — тоже один");
        b.setGroupId(g.getId());
        assertEquals(List.of(a, b), StructureGroupCalc.viewScreens(scene, b));
        b.setMountType(ScreenMountType.RIGGED);
        assertEquals(List.of(a), StructureGroupCalc.viewScreens(scene, a), "есть экран не на конструкции");
        assertTrue(StructureGroupCalc.viewScreens(scene, null).isEmpty());
    }

    @Test
    void groupSpecTextListsEveryScreenTheTotalsAndTheGroupWarnings(@TempDir Path dir) {
        Screen a = screen(dir, "L1", 2, 12);
        Screen b = screen(dir, "L2", 2, 8);
        calc(a, ScreenCurveType.FLAT, 600, 2);
        calc(b, ScreenCurveType.FLAT, 600, 1);
        model.setStructureManualLayout(a, true);
        model.attachStructureScreen(b, a, true, 90, true);
        model.setStructureTowerPlacement(a, 0, 6200, 2000, 0, true, true); // башня L1 уехала в тело L2
        List<Screen> pair = List.of(a, b);
        StructureGroupCalc.Result r = StructureGroupCalc.compute(pair, model::typeOf, model.getWorkspace());
        String text = StructureGroupCalc.specText("Left", pair, r);
        assertTrue(text.contains("группа «Left» (2 экр.)"), text);
        assertTrue(text.contains("«L1»: рам") && text.contains("«L2»: рам"), text);
        assertTrue(text.contains("Итого по группе:"), text);
        assertTrue(text.contains("Рам: " + r.total().totalFrameCount()), text);
        assertTrue(text.contains("ВНИМАНИЕ: Башня 1 экрана «L1» заходит в объём экрана «L2»"), text);
        assertTrue(text.contains("Крепление между башнями (стяжки) не считается"), text);
    }

    @Test
    void towerFootprintFollowsTheFramesThatActuallyStandNotTheNominalOverhang(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        // вынос 1500 мм: 3 секции основания + усилительная рама над 3-й секцией
        model.updateScreenStructure(s, 1500, 4, 3, 2, 1, 1500, 0.6, null, null, null, 0, null,
                ScreenCurveType.FLAT, 10_000, false, 600, 2);
        model.setStructureManualLayout(s, true);
        assertEquals(1500, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).depthMm(), 1e-9,
                "пока все рамы стоят — глубина по ним же (3 секции)");

        // убрали раму выноса: секцию основания 2 и усилительные рамы над ней
        model.toggleStructureBaseFrameSection(s, 0, 2);
        model.toggleStructureFrameCell(s, 0, 2, 2, null);
        model.toggleStructureFrameCell(s, 1, 2, 2, null);
        assertEquals(1000, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).depthMm(), 1e-9,
                "вынос убран — башня короче на секцию, а не «по-прежнему метр на метр и больше»");
        assertEquals(1500, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 1).depthMm(), 1e-9,
                "соседняя башня не тронута");

        // убрали задний ряд и вторую секцию основания — осталась одна секция под передним рядом
        model.toggleStructureBaseFrameSection(s, 0, 1);
        for (var c : List.copyOf(s.getStructureFrameCells())) {
            if (c.getRow() == 1 && c.getTowerIndex() <= 1 && !c.isHidden()) {
                model.toggleStructureFrameCell(s, c.getTowerIndex(), 1, c.getSegmentIndex(), null);
            }
        }
        assertEquals(500, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).depthMm(), 1e-9);
        // контур и пересечения считаются по ней: башни, стоявшие впритык по номинальной глубине, больше не мешают
        assertTrue(StructureLayoutMath.towerRects(s, type, model.getWorkspace()).get(0).get(0)[0] != null);
    }

    @Test
    void aTowerWithoutAnyCellsUsesTheNominalFootprint(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        model.updateScreenStructure(s, 1500, 4, 3, 2, 1, 1500, 0.6, null, null, null, 0, null,
                ScreenCurveType.FLAT, 10_000, false, 600, 2);
        assertEquals(1500, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 7).depthMm(), 1e-9,
                "башня 7 ещё не существует: номинальный вынос экрана (для «Новая встык»)");
    }
}
