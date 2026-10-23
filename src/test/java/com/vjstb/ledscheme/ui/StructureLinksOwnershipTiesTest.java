package com.vjstb.ledscheme.ui;

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
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureGroupCalc;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructureLayoutMath.Side;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Хвост запроса 2026-10-22: вращение ЭКРАНА группы кольцом в 3D, смена владельца башни между экранами, связь
 * «экран примыкает к другому под углом» (едет за опорой, ручная правка позы её разрывает, откат возвращает), пометка
 * «считать стяжки между башнями» (по умолчанию нет).
 */
class StructureLinksOwnershipTiesTest {

    private AppModel model;
    private CabinetType type;

    private Screen add(Path dir, String name, int cols) {
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
        s.setStructureTowerHeightMm(3000);
        model.updateScreenStructure(s, 3000, 4, StructureCalc.suggestVerticalFramesPerTower(s, null) + 3, 2, 1, 500,
                0.6, null, null, null, 0, null, ScreenCurveType.FLAT, 10_000, false, 600, 2);
        return s;
    }

    private Screen[] group(Path dir) {
        Screen a = add(dir, "L1", 12);
        Screen b = add(dir, "L2", 8);
        Scene scene = model.getCurrentScene();
        ScreenGroup g = new ScreenGroup("Left");
        scene.getScreenGroups().add(g);
        a.setGroupId(g.getId());
        b.setGroupId(g.getId());
        model.selectScreen(a);
        return new Screen[]{a, b};
    }

    // ---- связь «примыкает под углом» ----

    @Test
    void attachedScreenFollowsTheReferenceAndAHandEditBreaksTheLink(@TempDir Path dir) {
        Screen[] g = group(dir);
        Screen a = g[0];
        Screen b = g[1];
        model.attachStructureScreen(b, a, true, 90, true);
        assertEquals(a.getId(), b.getStructureAttachRefId());
        assertEquals(-90, b.getStructurePoseYawDeg(), 1e-6);

        model.setStructureScreenPose(a, 1000, 500, 30, true, true);
        Placement refEdge = StructureLayoutMath.compose(StructureLayoutMath.screenPose(a),
                StructureLayoutMath.screenEdgePose(a, type, true));
        Placement expected = StructureLayoutMath.attachScreenByEdges(refEdge, true,
                StructureLayoutMath.screenEdgePose(b, type, false), 90, true);
        assertEquals(expected.anchorX(), b.getStructurePoseXMm(), 1e-6, "примкнутый экран поехал за опорой");
        assertEquals(expected.anchorZ(), b.getStructurePoseZMm(), 1e-6);
        assertEquals(expected.yawDeg(), b.getStructurePoseYawDeg(), 1e-6);
        assertNull(a.getStructureAttachRefId(), "у опоры связи нет");

        model.setStructureScreenPose(b, 9000, 0, 0, true, true);
        assertNull(b.getStructureAttachRefId(), "ручная правка позы разрывает связь");
        model.setStructureScreenPose(a, 0, 0, 0, true, true);
        assertEquals(9000, b.getStructurePoseXMm(), 1e-6, "больше не следует");
    }

    @Test
    void chainsFollowCyclesAreRejectedAndUndoBringsBackPosesAndLinks(@TempDir Path dir) {
        Screen[] g = group(dir);
        Screen a = g[0];
        Screen b = g[1];
        Screen c = add(dir, "L3", 6);
        c.setGroupId(a.getGroupId());
        model.selectScreen(a);
        model.attachStructureScreen(b, a, true, 90, true);
        model.attachStructureScreen(c, b, true, 120, true);
        double cBefore = c.getStructurePoseXMm();
        model.setStructureScreenPose(a, 2000, 0, 0, true, true);
        assertTrue(Math.abs(c.getStructurePoseXMm() - cBefore) > 1, "цепочка A→B→C едет целиком");

        model.attachStructureScreen(a, c, true, 90, true); // замкнуло бы цикл
        assertNull(a.getStructureAttachRefId(), "цикл отвергнут");

        model.undo();
        assertEquals(cBefore, c.getStructurePoseXMm(), 1e-6, "откат вернул позы всех экранов");
        assertEquals(b.getId(), c.getStructureAttachRefId(), "и связи");
        assertEquals(a.getId(), b.getStructureAttachRefId());
    }

    @Test
    void detachKeepsThePoseAndIsUndoable(@TempDir Path dir) {
        Screen[] g = group(dir);
        model.attachStructureScreen(g[1], g[0], true, 90, true);
        double x = g[1].getStructurePoseXMm();
        model.detachStructureScreen(g[1]);
        assertNull(g[1].getStructureAttachRefId());
        assertEquals(x, g[1].getStructurePoseXMm(), 1e-9);
        model.undo();
        assertEquals(g[0].getId(), g[1].getStructureAttachRefId());
    }

    // ---- стяжки ----

    @Test
    void tiesAreCountedOnlyWhenMarkedAndOnlyForTowersStandingFlush(@TempDir Path dir) {
        Screen s = add(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        model.addStructureTowerFlush(s, 0, Side.RIGHT, true);
        assertEquals(0, StructureLayoutMath.tieContacts(List.of(s), model::typeOf, model.getWorkspace()),
                "пометка выключена — стяжек нет");
        model.setStructureCountTies(s, true);
        int contacts = StructureLayoutMath.tieContacts(List.of(s), model::typeOf, model.getWorkspace());
        assertTrue(contacts >= 1, "башня 3 стоит вплотную к башне 1");
        // башню 3 увели далеко — стыков с ней нет
        StructureTowerPlacement far = s.towerPlacementOf(2);
        model.setStructureTowerPlacement(s, 2, far.getAnchorXMm() + 8000, far.getAnchorZMm(), far.getYawDeg(), true, true);
        assertTrue(StructureLayoutMath.tieContacts(List.of(s), model::typeOf, model.getWorkspace()) < contacts);

        model.undo();
        StructureGroupCalc.Result r = StructureGroupCalc.compute(List.of(s), model::typeOf, model.getWorkspace());
        assertTrue(r.tieContacts() >= 1);
        String text = StructureGroupCalc.specText("G", List.of(s), r);
        assertTrue(text.contains("Стяжек между встык стоящими башнями: " + r.tieContacts() * 2), text);
        // стяжек на стык — редактируемый параметр
        model.setStructureTiesPerContact(s, 5, true);
        assertEquals(5 * contacts, StructureLayoutMath.tieCount(List.of(s), model::typeOf, model.getWorkspace()));
        assertTrue(StructureGroupCalc.specText("G", List.of(s),
                StructureGroupCalc.compute(List.of(s), model::typeOf, model.getWorkspace()))
                .contains("Стяжек между встык стоящими башнями: " + 5 * contacts));
        model.setStructureTiesPerContact(s, 99, true);
        assertEquals(Screen.MAX_TIES_PER_CONTACT, s.getStructureTiesPerContact(), "верхняя граница");
        model.undo();
        assertEquals(5, s.getStructureTiesPerContact());
        model.setStructureCountTies(s, false);
        assertTrue(StructureGroupCalc.specText("G", List.of(s),
                StructureGroupCalc.compute(List.of(s), model::typeOf, model.getWorkspace()))
                .contains("не считается"));
    }

    // ---- владелец башни ----

    @Test
    void towerMovesToAnotherScreenKeepingItsPlaceItsFramesAndOneUndoStep(@TempDir Path dir) {
        Screen[] g = group(dir);
        Screen a = g[0];
        Screen b = g[1];
        model.attachStructureScreen(b, a, true, 90, true);
        model.setStructureManualLayout(a, true);
        model.setStructureManualLayout(b, true);
        int aTowers = a.getStructureTowerPlacements().size();
        int bTowers = b.getStructureTowerPlacements().size();
        long framesA = a.getStructureFrameCells().size();
        long framesB = b.getStructureFrameCells().size();
        StructureTowerPlacement p0 = a.towerPlacementOf(1).copy();
        Placement worldBefore = StructureLayoutMath.compose(StructureLayoutMath.screenPose(a),
                new Placement(0, p0.getAnchorXMm(), p0.getAnchorZMm(), p0.getYawDeg()));
        model.toggleStructureCup(a, 2, 0, 1); // у башни 1 экрана A убран стакан — он поедет вместе с ней
        int depth = model.undoDepth();

        int moved = model.moveStructureTowerToScreen(a, 1, b);

        assertEquals(bTowers, moved);
        assertEquals(depth + 1, model.undoDepth(), "одна запись отмены на оба экрана");
        assertEquals(aTowers - 1, a.getStructureTowerPlacements().size());
        assertEquals(bTowers + 1, b.getStructureTowerPlacements().size());
        assertEquals(framesA - framesA / aTowers, a.getStructureFrameCells().size(), "рамы башни ушли из A");
        assertEquals(framesB + framesA / aTowers, b.getStructureFrameCells().size(), "и пришли в B");
        StructureTowerPlacement p1 = b.towerPlacementOf(moved);
        Placement worldAfter = StructureLayoutMath.compose(StructureLayoutMath.screenPose(b),
                new Placement(0, p1.getAnchorXMm(), p1.getAnchorZMm(), p1.getYawDeg()));
        assertEquals(worldBefore.anchorX(), worldAfter.anchorX(), 1e-6, "место в плане не изменилось");
        assertEquals(worldBefore.anchorZ(), worldAfter.anchorZ(), 1e-6);
        assertEquals(worldBefore.yawDeg(), worldAfter.yawDeg(), 1e-6);
        assertTrue(com.vjstb.ledscheme.service.StructureCupMath.isRemoved(b, 2 * moved, 0, 1), "убранный стакан поехал");
        assertFalse(com.vjstb.ledscheme.service.StructureCupMath.isRemoved(a, 2, 0, 1));

        model.undo();
        assertEquals(aTowers, a.getStructureTowerPlacements().size(), "откат вернул башню в A");
        assertEquals(bTowers, b.getStructureTowerPlacements().size(), "и убрал из B");
        assertEquals(framesA, a.getStructureFrameCells().size());
        assertEquals(framesB, b.getStructureFrameCells().size());
    }

    @Test
    void moveIsRefusedForAutoScreensTheSameScreenOrAMissingTower(@TempDir Path dir) {
        Screen[] g = group(dir);
        model.setStructureManualLayout(g[0], true);
        assertEquals(-1, model.moveStructureTowerToScreen(g[0], 0, g[1]), "у B расстановка авто");
        model.setStructureManualLayout(g[1], true);
        assertEquals(-1, model.moveStructureTowerToScreen(g[0], 0, g[0]));
        assertEquals(-1, model.moveStructureTowerToScreen(g[0], 9, g[1]));
    }

    // ---- панель и кольцо ----

    @Test
    void panelOwnerComboLinkInfoDetachAndTiesCheckboxWork(@TempDir Path dir) throws Exception {
        Screen[] g = group(dir);
        Screen a = g[0];
        Screen b = g[1];
        model.attachStructureScreen(b, a, true, 90, true);
        model.setStructureManualLayout(a, true);
        model.setStructureManualLayout(b, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = new StructureLayoutPanel(model, view);
        model.addListener(p::syncFromModel);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        model.selectScreen(b);
        p.syncFromModel();
        assertTrue(p.detachButton().isVisible());
        assertTrue(p.screenInfoText().contains("Примыкает к «L1» справа, угол 90°"), p.screenInfoText());
        p.detachButton().doClick();
        assertNull(b.getStructureAttachRefId());
        assertFalse(p.detachButton().isVisible());

        model.selectScreen(a);
        view.selectTower(0);
        assertTrue(p.ownerComboBox().isEnabled());
        assertEquals(2, p.ownerComboBox().getItemCount());
        int aBefore = a.getStructureTowerPlacements().size();
        p.ownerComboBox().setSelectedItem(b);
        assertEquals(b, model.getCurrentScreen(), "выбор перешёл на новый экран башни");
        assertEquals(aBefore - 1, a.getStructureTowerPlacements().size());
        assertTrue(view.selectedTower() >= 0, "перенесённая башня выбрана");

        p.tiesCheck().doClick();
        assertTrue(b.isStructureCountTies());
        assertTrue(p.tiesPerContactSpinner().isEnabled(), "параметр доступен при включённой пометке");
        p.tiesPerContactSpinner().setValue(3);
        assertEquals(3, b.getStructureTiesPerContact());
    }

    @Test
    void screenRingRotatesTheScreenAboutItsCentreAndLinkedScreensFollow(@TempDir Path dir) {
        Screen[] g = group(dir);
        Screen a = g[0];
        Screen b = g[1];
        model.attachStructureScreen(b, a, true, 90, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        panel.setLayoutMode(true);
        double[] c = panel.screenRingCentre();
        assertNotNull(c, "экран группы без выбранной башни — кольцо есть");
        double r = panel.screenRingRadiusForTest();
        com.vjstb.ledscheme.service.StructurePickMath.Ray press = down(c[0] + r, c[1]);

        assertTrue(panel.layoutPress(press));
        assertEquals(com.vjstb.ledscheme.service.StructureGizmoMath.Handle.RING, panel.dragHandle());
        double yaw0 = a.getStructurePoseYawDeg();
        double bX0 = b.getStructurePoseXMm();
        // курсор уходит по кольцу на +40° (в смысле atan2) -> экран на -40°
        double ang = Math.toRadians(40);
        panel.layoutDrag(down(c[0] + r * Math.cos(ang), c[1] + r * Math.sin(ang)), false);
        panel.layoutRelease();

        assertEquals(StructureLayoutMath.normalizeDeg(yaw0 - 40), a.getStructurePoseYawDeg(), 1e-6);
        double[] cAfter = StructureLayoutMath.screenPose(a).toWorld(
                a.getCols() * 500 / 2.0, -65 / 2.0);
        assertEquals(c[0], cAfter[0], 1e-6, "центр экрана остался на месте");
        assertEquals(c[1], cAfter[1], 1e-6);
        assertTrue(Math.abs(b.getStructurePoseXMm() - bX0) > 1, "примкнутый экран поехал за повёрнутым");
        assertNull(a.getStructureAttachRefId());
        assertEquals(a.getId(), b.getStructureAttachRefId(), "связь B с A сохранилась");

        // отмена жеста возвращает обоих
        model.undo();
        assertEquals(yaw0, a.getStructurePoseYawDeg(), 1e-6);
        assertEquals(bX0, b.getStructurePoseXMm(), 1e-6);
    }

    @Test
    void noScreenRingForASingleScreenOrWhileATowerIsSelected(@TempDir Path dir) {
        Screen s = add(dir, "E", 12);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        assertNull(panel.screenRingCentre(), "одиночный экран — кольца нет");
        Screen[] g = group(dir);
        model.setStructureManualLayout(g[0], true);
        Structure3DPanel panel2 = new Structure3DPanel(model, false);
        assertNotNull(panel2.screenRingCentre());
        panel2.selectTower(0);
        assertNull(panel2.screenRingCentre(), "выбрана башня — кольцо экрана скрыто (работает гизмо башни)");
        assertNotNull(s);
    }

    private static com.vjstb.ledscheme.service.StructurePickMath.Ray down(double x, double z) {
        return new com.vjstb.ledscheme.service.StructurePickMath.Ray(
                new com.vjstb.ledscheme.service.StructurePickMath.Vec3(x, 20_000, z),
                new com.vjstb.ledscheme.service.StructurePickMath.Vec3(0, -1, 0));
    }
}
