package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureGizmoMath;
import com.vjstb.ledscheme.service.StructureGizmoMath.Handle;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructureOrient.TowerPose;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Башня по всем осям (запрос 2026-10-22: «вращать башню по всем осям, чтобы собрать башню, лежащую на боку, — рамы
 * в ряд на высоте»): поля «Наклон/Крен/Высота», кольца наклона и крена и ось высоты гизмо, центр основания
 * неподвижен при вращении, коллизии учитывают высоту, JSON старых проектов без новых полей.
 */
class StructureTowerOrientationTest {

    private AppModel model;
    private Screen screen;
    private TowerSpec spec;

    private void setup(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        CabinetType type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        screen = model.addScreen("E", type.getId(), 3, 12, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(screen);
        screen.setStructureTowerHeightMm(3000);
        model.updateScreenStructure(screen, 3000, 4, StructureCalc.suggestVerticalFramesPerTower(screen, null) + 3,
                2, 1, 500, 0.6, null, null, null, 0, null, ScreenCurveType.FLAT, 10_000, false, 600, 2);
        model.setStructureManualLayout(screen, true);
        spec = StructureCurveMath.towerSpecOf(screen, model.getWorkspace(), 0);
    }

    private TowerPose pose(int k) {
        StructureTowerPlacement p = screen.towerPlacementOf(k);
        return new TowerPose(p.getAnchorXMm(), p.getElevationMm(), p.getAnchorZMm(), p.getYawDeg(), p.getPitchDeg(),
                p.getRollDeg());
    }

    private double[] pivot(int k) {
        return StructureGizmoMath.pivotWorld(pose(k), StructureCurveMath.towerSpecOf(screen, model.getWorkspace(), k));
    }

    @Test
    void rollFieldLaysTheTowerOnItsSideWhileTheFootStaysPut(@TempDir Path dir) {
        setup(dir);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = new StructureLayoutPanel(model, view);
        model.addListener(p::syncFromModel);
        view.selectTower(0);
        double[] before = pivot(0);

        p.towerRollSpinner().setValue(90.0);

        StructureTowerPlacement t = screen.towerPlacementOf(0);
        assertEquals(90, t.getRollDeg(), 1e-9);
        assertEquals(0, t.getPitchDeg(), 1e-9);
        double[] after = pivot(0);
        assertEquals(before[0], after[0], 1e-6, "центр основания не сдвинулся");
        assertEquals(before[1], after[1], 1e-6);
        assertEquals(before[2], after[2], 1e-6);
        assertEquals(90.0, ((Number) p.towerRollSpinner().getValue()).doubleValue(), 1e-9);
        // верх башни теперь в стороне на высоте основания: башня лежит
        TowerPose lying = pose(0);
        double[] top = lying.toWorld(0, 3000, -spec.depthMm() / 2.0);
        assertEquals(before[1], top[1], 1e-6);
        assertEquals(3000, Math.abs(top[0] - before[0]), 1e-6);

        p.towerPitchSpinner().setValue(30.0);
        assertEquals(30, screen.towerPlacementOf(0).getPitchDeg(), 1e-9);
        assertEquals(90, screen.towerPlacementOf(0).getRollDeg(), 1e-6, "крен не пострадал");
        p.towerElevSpinner().setValue(3200.0);
        assertEquals(3200, screen.towerPlacementOf(0).getElevationMm(), 1e-9);
    }

    @Test
    void pitchAndRollRingsRotateAboutTheirOwnAxesAndAxisYLiftsTheTower(@TempDir Path dir) {
        setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        panel.selectTower(0);
        double[] p = pivot(0);
        double r = StructureGizmoMath.ringRadius(spec);
        double c45 = Math.cos(Math.PI / 4);

        // кольцо наклона (плоскость ⟂ оси X): точка кольца впереди и вверху, луч вдоль X
        double[] pitchPoint = {p[0], p[1] + r * c45, p[2] + r * c45};
        assertTrue(panel.layoutPress(new Ray(new Vec3(pitchPoint[0] + 6000, pitchPoint[1], pitchPoint[2]),
                new Vec3(-1, 0, 0))));
        assertEquals(Handle.RING_PITCH, panel.dragHandle());
        double ang = Math.toRadians(45 + 30);
        panel.layoutDrag(new Ray(new Vec3(p[0] + 6000, p[1] + r * Math.sin(ang), p[2] + r * Math.cos(ang)),
                new Vec3(-1, 0, 0)), false);
        panel.layoutRelease();
        StructureTowerPlacement t = screen.towerPlacementOf(0);
        assertEquals(30, Math.abs(t.getPitchDeg()), 1e-6, "кольцо наклона меняет только наклон");
        assertEquals(0, t.getRollDeg(), 1e-6);
        assertEquals(0, t.getYawDeg(), 1e-6);
        double[] after = pivot(0);
        assertEquals(p[0], after[0], 1e-6, "центр основания на месте");
        assertEquals(p[1], after[1], 1e-6);
        assertEquals(p[2], after[2], 1e-6);

        // вернуть в ноль и покрутить кольцо крена (плоскость ⟂ оси Z)
        model.setStructureTowerPose(screen, 0, t.getAnchorXMm(), t.getAnchorZMm(), 0, 0, 0, 0, true, true);
        p = pivot(0);
        double[] rollPoint = {p[0] + r * c45, p[1] + r * c45, p[2]};
        assertTrue(panel.layoutPress(new Ray(new Vec3(rollPoint[0], rollPoint[1], rollPoint[2] + 6000),
                new Vec3(0, 0, -1))));
        assertEquals(Handle.RING_ROLL, panel.dragHandle());
        panel.layoutDrag(new Ray(new Vec3(p[0] + r * Math.cos(Math.toRadians(75)), p[1] + r * Math.sin(Math.toRadians(75)),
                p[2] + 6000), new Vec3(0, 0, -1)), false);
        panel.layoutRelease();
        StructureTowerPlacement t2 = screen.towerPlacementOf(0);
        assertEquals(30, Math.abs(t2.getRollDeg()), 1e-6);
        assertEquals(0, t2.getPitchDeg(), 1e-6);

        // ось Y: тянем вверх на 1 м (Shift — шаг 50 мм)
        model.setStructureTowerPose(screen, 0, t2.getAnchorXMm(), t2.getAnchorZMm(), 0, 0, 0, 0, true, true);
        p = pivot(0);
        assertTrue(panel.layoutPress(new Ray(new Vec3(p[0] + 6000, p[1] + 1500, p[2]), new Vec3(-1, 0, 0))));
        assertEquals(Handle.AXIS_Y, panel.dragHandle());
        panel.layoutDrag(new Ray(new Vec3(p[0] + 6000, p[1] + 2520, p[2]), new Vec3(-1, 0, 0)), true);
        panel.layoutRelease();
        assertEquals(1000, screen.towerPlacementOf(0).getElevationMm(), 1e-6, "+1020 мм, с Shift — кратно 50 → 1000");
        assertEquals(0, screen.towerPlacementOf(0).getPitchDeg(), 1e-9, "высота углы не трогает");
    }

    @Test
    void yawRingOfATiltedTowerStillTurnsOnlyTheYawAboutTheVertical(@TempDir Path dir) {
        setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        StructureTowerPlacement t = screen.towerPlacementOf(0);
        model.setStructureTowerPose(screen, 0, t.getAnchorXMm(), t.getAnchorZMm(), 500, 0, 0, 90, true, true);
        panel.selectTower(0);
        double[] p = pivot(0);
        double r = StructureGizmoMath.ringRadius(spec);
        double d = r / Math.sqrt(2);
        // 45° между горизонтальными осями — на кольце рыскания (луч сверху)
        assertTrue(panel.layoutPress(new Ray(new Vec3(p[0] + d, p[1] + 9000, p[2] + d), new Vec3(0, -1, 0))));
        assertEquals(Handle.RING, panel.dragHandle());
        panel.layoutDrag(new Ray(new Vec3(p[0] + d + 0, p[1] + 9000, p[2] - d), new Vec3(0, -1, 0)), false);
        panel.layoutRelease();
        StructureTowerPlacement t2 = screen.towerPlacementOf(0);
        assertEquals(90, Math.abs(t2.getYawDeg()), 1e-6, "четверть оборота кольцом рыскания");
        assertEquals(90, t2.getRollDeg(), 1e-6, "крен остался");
        assertEquals(0, t2.getPitchDeg(), 1e-6);
        assertEquals(500, t2.getElevationMm(), 1e-6);
        double[] after = pivot(0);
        assertEquals(p[0], after[0], 1e-6);
        assertEquals(p[2], after[2], 1e-6);
    }

    @Test
    void collisionsRespectHeightSoATowerLiftedAboveTheOthersDoesNotCollideWithThem(@TempDir Path dir) {
        setup(dir);
        StructureTowerPlacement a = screen.towerPlacementOf(0);
        // вторую башню — прямо над первой в плане
        model.setStructureTowerPose(screen, 1, a.getAnchorXMm(), a.getAnchorZMm(), 0, 0, 0, 0, true, true);
        assertTrue(StructureLayoutMath.analyzeManual(screen, model.typeOf(screen), model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("пересекаются")), "на одном месте — пересечение");
        model.setStructureTowerPose(screen, 1, a.getAnchorXMm(), a.getAnchorZMm(), 6000, 0, 0, 0, true, true);
        assertFalse(StructureLayoutMath.analyzeManual(screen, model.typeOf(screen), model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("пересекаются")), "поднята выше верха первой — не мешает");
        // лежащая на боку на высоте крыши первой башни — тоже нет, опустить ниже — да
        double top = com.vjstb.ledscheme.service.StructureCupMath.towerHeightMm(screen, model.getWorkspace(), 0);
        model.setStructureTowerPose(screen, 1, a.getAnchorXMm(), a.getAnchorZMm(), top + 530, 0, 0, 90, true, true); // ширина 1051 лежит по вертикали: центр основания на полширины выше крыши
        assertFalse(StructureLayoutMath.analyzeManual(screen, model.typeOf(screen), model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("пересекаются")));
        model.setStructureTowerPose(screen, 1, a.getAnchorXMm(), a.getAnchorZMm(), top - 500, 0, 0, 90, true, true);
        assertTrue(StructureLayoutMath.analyzeManual(screen, model.typeOf(screen), model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("пересекаются")));
    }

    @Test
    void poseSurvivesUndoAndJsonAndOldProjectsStandUpright(@TempDir Path dir) throws Exception {
        setup(dir);
        StructureTowerPlacement t = screen.towerPlacementOf(0);
        model.setStructureTowerPose(screen, 0, t.getAnchorXMm(), t.getAnchorZMm(), 1800, 10, 20, 90, true, true);
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen back = mapper.readValue(mapper.writeValueAsString(screen), Screen.class);
        StructureTowerPlacement bt = back.towerPlacementOf(0);
        assertEquals(1800, bt.getElevationMm(), 1e-9);
        assertEquals(20, bt.getPitchDeg(), 1e-9);
        assertEquals(90, bt.getRollDeg(), 1e-9);

        Screen old = mapper.readValue("{\"id\":\"s\",\"structureManualLayout\":true,\"structureTowerPlacements\":["
                + "{\"towerIndex\":0,\"anchorXMm\":100.0,\"anchorZMm\":-400.0,\"yawDeg\":15.0}]}", Screen.class);
        StructureTowerPlacement ot = old.towerPlacementOf(0);
        assertEquals(0, ot.getElevationMm(), 1e-9, "старый проект — на земле");
        assertEquals(0, ot.getPitchDeg(), 1e-9);
        assertEquals(0, ot.getRollDeg(), 1e-9);

        model.undo();
        assertEquals(0, screen.towerPlacementOf(0).getElevationMm(), 1e-9);
        assertEquals(0, screen.towerPlacementOf(0).getRollDeg(), 1e-9);
    }
}
