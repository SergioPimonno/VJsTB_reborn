package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureGizmoMath;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Режим «Раскладка» 3D-редактора конструктива (запрос пользователя 2026-10-22, трек B): поза
 * экрана в группе сдвигает и поворачивает его конструктив (кандидаты и picking), клик выбирает
 * башню или экран группы под лучом, перетаскивание двигает башню/экран по земле, Shift вращает
 * вокруг центра с шагом 15°, весь жест — одна запись отмены. Панель строится без GL-канваса — тест
 * идёт и в headless-среде; вместо пикселей — готовые лучи (как в {@code Structure3DPanelPickingTest}).
 */
class Structure3DPanelLayoutTest {

    private AppModel model;
    private CabinetType type;

    private Screen addScreen(Path dir, String name, int cols) {
        if (model == null) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
            CabinetType ct = new CabinetType();
            ct.setName("T");
            ct.setWidthMm(500);
            ct.setHeightMm(500);
            ct.setDepthMm(65.0);
            ct.setResolutionWidth(128);
            ct.setResolutionHeight(128);
            type = model.addCabinetType(ct);
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
        }
        Screen s = model.addScreen(name, type.getId(), 3, cols, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 2, 1, 1, 500, 0.6,
                null, null, null, 0, null, ScreenCurveType.FLAT, 10_000, false, 600, 2);
        return s;
    }

    private static Ray up(double[] c) {
        return new Ray(new Vec3(c[0], -5000, c[2]), new Vec3(0, 1, 0));
    }

    private static Ray down(double x, double z) {
        return new Ray(new Vec3(x, 20_000, z), new Vec3(0, -1, 0));
    }

    private Screen groupOfTwo(Path dir) {
        Screen a = addScreen(dir, "L1", 12);
        Screen b = addScreen(dir, "L2", 8);
        Scene scene = model.getCurrentScene();
        ScreenGroup g = new ScreenGroup("Left");
        scene.getScreenGroups().add(g);
        a.setGroupId(g.getId());
        b.setGroupId(g.getId());
        model.selectScreen(a);
        model.attachStructureScreen(b, a, true, 90, true);
        return a;
    }

    @Test
    void screenPoseMovesAndTurnsTheStructureCandidatesAndPicking(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        double[] before = panel.pickBoxWorldCenter("frame:1:0:0");
        model.setStructureScreenPose(s, 2000, 3000, 90, true, true);
        double[] after = panel.pickBoxWorldCenter("frame:1:0:0");
        double[] expected = new Placement(0, 2000, 3000, 90).toWorld(before[0], before[2]);
        assertEquals(expected[0], after[0], 1e-6);
        assertEquals(expected[1], after[2], 1e-6);
        assertEquals(before[1], after[1], 1e-6, "поза не меняет высоту");
        assertEquals("frame:1:0:0", panel.pickKeyAlongRay(up(after), true), "picking идёт по сдвинутой раме");
    }

    @Test
    void groupShowsAllItsScreensOnlyWhileEligible(@TempDir Path dir) {
        Screen a = groupOfTwo(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        assertEquals(2, panel.viewScreens().size());
        assertSame(a, panel.viewScreens().get(0));
        model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1)
                .setMountType(ScreenMountType.RIGGED);
        assertEquals(1, panel.viewScreens().size(), "в группе есть экран не на конструктиве");
    }

    @Test
    void layoutPressSelectsATowerAndDragMovesItAsOneUndoStep(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        StructureTowerPlacement start = s.towerPlacementOf(0).copy();
        double[] c = panel.pickBoxWorldCenter("frame:0:0:0");
        int depth = model.undoDepth();

        assertTrue(panel.layoutPress(up(c)));
        assertEquals(0, panel.selectedTower());
        assertTrue(panel.layoutDragging());
        panel.layoutDrag(up(new double[]{c[0] + 400, c[1], c[2]}), false);
        panel.layoutDrag(up(new double[]{c[0] + 1000, c[1], c[2] - 300}), false);
        panel.layoutRelease();

        assertFalse(panel.layoutDragging());
        assertEquals(depth + 1, model.undoDepth(), "жест — одна запись отмены");
        StructureTowerPlacement moved = s.towerPlacementOf(0);
        assertEquals(start.getAnchorXMm() + 1000, moved.getAnchorXMm(), 1e-6);
        assertEquals(start.getAnchorZMm() - 300, moved.getAnchorZMm(), 1e-6);
        assertEquals(start.getYawDeg(), moved.getYawDeg(), 1e-9);
        model.undo();
        assertEquals(start.getAnchorXMm(), s.towerPlacementOf(0).getAnchorXMm(), 1e-6);
    }

    /** Ручка кольца гизмо выбранной башни: мировая точка на кольце под 45° между осями башни. */
    private double[] ringPoint(Placement p, StructureCurveMath.TowerSpec spec) {
        double[] c = StructureGizmoMath.centerWorld(p, spec);
        double[] ux = StructureGizmoMath.axisDir(p, true);
        double[] uz = StructureGizmoMath.axisDir(p, false);
        double r = StructureGizmoMath.ringRadius(spec);
        double a = Math.PI / 4;
        return new double[]{c[0] + (ux[0] * Math.cos(a) + uz[0] * Math.sin(a)) * r,
                c[1] + (ux[1] * Math.cos(a) + uz[1] * Math.sin(a)) * r};
    }

    @Test
    void ringHandleRotatesTheTowerAboutItsCentreFreelyAndShiftSnapsTheAngle(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        panel.selectTower(0);
        StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0);
        StructureTowerPlacement p0 = s.towerPlacementOf(0).copy();
        Placement before = new Placement(0, p0.getAnchorXMm(), p0.getAnchorZMm(), p0.getYawDeg());
        double[] centre = StructureGizmoMath.centerWorld(before, spec);
        double[] rp = ringPoint(before, spec);
        assertEquals(StructureGizmoMath.Handle.RING, panel.layoutHover(down(rp[0], rp[1])));

        assertTrue(panel.layoutPress(down(rp[0], rp[1])));
        assertEquals(StructureGizmoMath.Handle.RING, panel.dragHandle());
        double v0x = rp[0] - centre[0];
        double v0z = rp[1] - centre[1];
        double a = Math.toRadians(37); // курсор повернули на 37° (в смысле atan2) -> башня на -37°
        double v1x = v0x * Math.cos(a) - v0z * Math.sin(a);
        double v1z = v0x * Math.sin(a) + v0z * Math.cos(a);
        panel.layoutDrag(down(centre[0] + v1x, centre[1] + v1z), false);
        assertEquals(StructureLayoutMath.normalizeDeg(p0.getYawDeg() - 37), s.towerPlacementOf(0).getYawDeg(), 1e-6,
                "без Shift угол свободный");
        panel.layoutDrag(down(centre[0] + v1x, centre[1] + v1z), true);
        assertEquals(-30, s.towerPlacementOf(0).getYawDeg(), 1e-6, "с Shift — шаг 15°");
        panel.layoutRelease();

        StructureTowerPlacement p1 = s.towerPlacementOf(0);
        double[] centreAfter = StructureGizmoMath.centerWorld(
                new Placement(0, p1.getAnchorXMm(), p1.getAnchorZMm(), p1.getYawDeg()), spec);
        assertEquals(centre[0], centreAfter[0], 1e-6, "центр контура остаётся на месте");
        assertEquals(centre[1], centreAfter[1], 1e-6);
        assertEquals(StructureGizmoMath.Handle.NONE, panel.dragHandle());
        assertTrue(panel.activeGuides().isEmpty(), "после жеста линий привязки нет");
    }

    @Test
    void axisHandleMovesTheTowerStrictlyAlongItsAxis(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        panel.selectTower(0);
        StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0);
        StructureTowerPlacement p0 = s.towerPlacementOf(0).copy();
        Placement w = new Placement(0, p0.getAnchorXMm(), p0.getAnchorZMm(), p0.getYawDeg());
        double[] c = StructureGizmoMath.centerWorld(w, spec);

        assertTrue(panel.layoutPress(down(c[0] + 800, c[1])));
        assertEquals(StructureGizmoMath.Handle.AXIS_X, panel.dragHandle());
        panel.layoutDrag(down(c[0] + 800 + 600, c[1] + 500), false); // тянут по диагонали, ось X — вдоль экрана
        panel.layoutRelease();
        StructureTowerPlacement p1 = s.towerPlacementOf(0);
        assertEquals(p0.getAnchorXMm() + 600, p1.getAnchorXMm(), 1e-6);
        assertEquals(p0.getAnchorZMm(), p1.getAnchorZMm(), 1e-6, "поперёк оси башня не сдвинулась");
        assertEquals(p0.getYawDeg(), p1.getYawDeg(), 1e-9);
    }

    @Test
    void shiftEnablesGuideLinesAndSnapsTheTowerToTheScreenPlane(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        panel.selectTower(0);
        StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0);
        StructureTowerPlacement p0 = s.towerPlacementOf(0).copy();
        Placement w = new Placement(0, p0.getAnchorXMm(), p0.getAnchorZMm(), p0.getYawDeg());
        double[] c = StructureGizmoMath.centerWorld(w, spec);
        double depthOfCabinet = 65; // из addScreen: ct.setDepthMm(65)
        double forward = p0.getAnchorZMm() + 300; // передняя кромка башни окажется на 300 мм ближе к экрану

        panel.layoutPress(down(c[0], c[1] + 800)); // ось Z (вперёд-назад)
        assertEquals(StructureGizmoMath.Handle.AXIS_Z, panel.dragHandle());
        panel.layoutDrag(down(c[0], c[1] + 800 + 300), false);
        assertEquals(forward, s.towerPlacementOf(0).getAnchorZMm(), 1e-6, "без Shift — ровно куда довели");
        assertTrue(panel.activeGuides().isEmpty());

        panel.layoutDrag(down(c[0], c[1] + 800 + 300), true);
        assertEquals(-depthOfCabinet, s.towerPlacementOf(0).getAnchorZMm(), 1e-6,
                "с Shift передняя кромка прилипла к тыльной плоскости экрана");
        assertFalse(panel.activeGuides().isEmpty(), "сработавшая линия привязки видна");
        panel.layoutRelease();
    }

    @Test
    void panStaysOnTheFloorInTheTopViewAndMovesHeightInTheFrontView(@TempDir Path dir) {
        addScreen(dir, "E", 12);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setViewAngle(0, 89);
        panel.panBy(0, 100);
        double[] top = panel.panOffset();
        assertEquals(0, top[1], 1e-9, "вид сверху: перетаскивание вверх-вниз высоту не меняет");
        assertTrue(Math.abs(top[2]) > 1, "а двигает вдоль пола (Z плана)");
        assertEquals(0, top[0], 1e-6);
        panel.panBy(100, 0);
        assertTrue(Math.abs(panel.panOffset()[0]) > 1, "влево-вправо — вдоль X");
        assertEquals(0, panel.panOffset()[1], 1e-9);

        Structure3DPanel front = new Structure3DPanel(model, false);
        front.setViewAngle(0, 0);
        front.panBy(0, 100);
        double[] f = front.panOffset();
        assertTrue(Math.abs(f[1]) > 1, "вид спереди: вертикаль двигает по высоте");
        assertEquals(0, f[2], 1e-6);
    }

    @Test
    void draggedTowerWithShiftSnapsFlushToANeighbourAndWithoutItStaysFree(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        StructureTowerPlacement second = s.towerPlacementOf(1).copy();
        double[] c = panel.pickBoxWorldCenter("frame:0:0:0");
        double firstX = s.towerPlacementOf(0).getAnchorXMm();
        // сдвигаем башню 0 вправо так, чтобы её правая грань оказалась в 40 мм от левой грани башни 1
        double gapNow = second.getAnchorXMm() - firstX - 1051;
        double shift = gapNow - 40;

        panel.layoutPress(up(c));
        panel.layoutDrag(up(new double[]{c[0] + shift, c[1], c[2]}), true);
        panel.layoutRelease();
        assertEquals(second.getAnchorXMm() - 1051, s.towerPlacementOf(0).getAnchorXMm(), 1e-6,
                "прилипла вплотную к соседней башне");

        model.undo();
        c = panel.pickBoxWorldCenter("frame:0:0:0");
        panel.layoutPress(up(c));
        panel.layoutDrag(up(new double[]{c[0] + shift, c[1], c[2]}), false);
        panel.layoutRelease();
        assertEquals(firstX + shift, s.towerPlacementOf(0).getAnchorXMm(), 1e-6,
                "без Shift — ровно там, куда довели курсор");
    }

    @Test
    void clickingAnotherGroupScreenSelectsItAndDragMovesItsPose(@TempDir Path dir) {
        Screen a = groupOfTwo(dir);
        Screen b = model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        assertSame(a, model.getCurrentScreen());
        // L2 стоит под -90° от торца L1 (6000, 0), её лицевая сторона смотрит вдоль -x: точка на её
        // верхней грани в её системе (1000, -30) -> мировая
        double[] w = new Placement(0, b.getStructurePoseXMm(), b.getStructurePoseZMm(), b.getStructurePoseYawDeg())
                .toWorld(1000, -30);

        assertSame(b, panel.layoutHitScreen(down(w[0], w[1])));
        assertTrue(panel.layoutPress(down(w[0], w[1])));
        assertSame(b, model.getCurrentScreen(), "клик в 3D выбирает экран и в модели");
        assertEquals(-1, panel.selectedTower());

        double poseXBefore = b.getStructurePoseXMm();
        // луч сверху вниз: плоскость жеста — верхняя грань экрана, сдвиг ложится на землю как есть
        panel.layoutDrag(down(w[0] + 500, w[1]), false);
        panel.layoutRelease();
        assertEquals(poseXBefore + 500, b.getStructurePoseXMm(), 1e-6);
        assertEquals(-90, b.getStructurePoseYawDeg(), 1e-9, "перенос угол не меняет");
        model.undo();
        assertEquals(poseXBefore, b.getStructurePoseXMm(), 1e-6);
    }

    @Test
    void missingEverythingClearsTheTowerSelectionAndStartsNoDrag(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        panel.selectTower(1);
        assertEquals(1, panel.selectedTower());
        assertFalse(panel.layoutPress(down(90_000, 90_000)));
        assertEquals(-1, panel.selectedTower());
        assertFalse(panel.layoutDragging());

        Structure3DPanel frames = new Structure3DPanel(model, false);
        assertFalse(frames.layoutPress(down(0, 0)), "вне режима «Раскладка» нажатие ничего не делает");
        assertNotNull(frames);
    }

    @Test
    void towersAreNotSelectableWithoutManualLayout(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        double[] c = panel.pickBoxWorldCenter("frame:0:0:0");
        panel.layoutPress(up(c));
        assertEquals(-1, panel.selectedTower(), "в авто-режиме башни не выбираются");
        assertEquals(-1, panel.layoutHitTower(up(c)));
        assertFalse(s.isStructureManualLayout());
    }
}
