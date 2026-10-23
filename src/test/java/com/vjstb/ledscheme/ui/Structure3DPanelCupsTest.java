package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 3D конструктива: стаканы 50 мм между рамами и узкие башни (запрос пользователя 2026-10-22). Рамы
 * стоят по реальной стопке (рама 950 + стакан 50 — метр на модуль), стакан — блок в зазоре, ПКМ по
 * нему убирает его (рамы выше опускаются), убранный стык виден призраком для Ctrl+клика; у башни с
 * короткими горизонтальными рамами (850) столбы стоят на 900 мм вместо 1000. Панель без GL.
 */
class Structure3DPanelCupsTest {

    private AppModel model;
    private CabinetType type;
    private StructureFrameType shortFrame;
    private Screen screen;

    private Screen setup(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        type = model.addCabinetType(ct);
        StructureFrameType f = new StructureFrameType();
        f.setName("Рама 950");
        f.setKind(StructureFrameType.Kind.FRAME);
        f.setHeightMm(950.0);
        f.setWidthMm(500.0);
        f.setDepthMm(51.0);
        StructureFrameType frame = model.addStructureFrameType(f);
        StructureFrameType sf = new StructureFrameType();
        sf.setName("Короткая 850");
        sf.setKind(StructureFrameType.Kind.SHORT_FRAME);
        sf.setHeightMm(850.0);
        sf.setWidthMm(500.0);
        sf.setDepthMm(51.0);
        shortFrame = model.addStructureFrameType(sf);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        screen = model.addScreen("E", type.getId(), 6, 8, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(screen);
        screen.setStructureTowerHeightMm(3000);
        model.updateScreenStructure(screen, 3000, 4, StructureCalc.suggestVerticalFramesPerTower(screen, frame),
                1, 1, 500, 0.6, frame.getId(), null, null, 0, null, ScreenCurveType.FLAT, 10_000, false, 600, 2);
        return screen;
    }

    private static Ray up(double x, double z) {
        return new Ray(new Vec3(x, -5000, z), new Vec3(0, 1, 0));
    }

    @Test
    void framesStandOnTheRealStackAndFollowTheCups(@TempDir Path dir) {
        Screen s = setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        assertEquals(1000 + 475, panel.pickBoxWorldCenter("frame:0:0:1")[1], 1e-6, "950 + стакан 50, середина рамы");
        assertEquals(2 * 1000 + 475, panel.pickBoxWorldCenter("frame:0:0:2")[1], 1e-6);

        model.setStructureCupsEnabled(s, false);
        assertEquals(950 + 475, panel.pickBoxWorldCenter("frame:0:0:1")[1], 1e-6, "без стаканов — рамы вплотную");
        assertTrue(panel.cupKeys(true).isEmpty());
        assertTrue(panel.cupKeys(false).isEmpty(), "и призраков стаканов при сборке без них нет");
    }

    @Test
    void peremychkaSitsOnTheRungOfTheRealStack(@TempDir Path dir) {
        setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        // уровень 0 — середина второй рамы: со стаканом 1000 + 475; перемычка толщиной в рельс, берём центр
        double y = panel.pickBoxWorldCenter("peremychka:0:0:4")[1];
        assertEquals(1475, y, 1.0);
    }

    @Test
    void removedCupBecomesAGhostThatCtrlClickBringsBack(@TempDir Path dir) {
        Screen s = setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        assertTrue(panel.cupKeys(true).contains("cup:0:0:1"));
        assertFalse(panel.cupKeys(false).contains("cup:0:0:1"));

        model.toggleStructureCup(s, 0, 0, 1);
        assertTrue(panel.cupKeys(false).contains("cup:0:0:1"), "убранный стык — призрак");
        assertTrue(panel.candidateKeys(false).contains("cup:0:0:1"), "призрак доступен Ctrl+клику");
        assertFalse(panel.candidateKeys(true).stream().anyMatch(k -> k.startsWith("cup:")),
                "существующие стаканы — не цель обычного клика (только ПКМ)");
        assertEquals(950 + 475, panel.pickBoxWorldCenter("frame:0:0:1")[1], 1e-6, "рама села на нижнюю");

        model.toggleStructureCup(s, 0, 0, 1);
        assertTrue(panel.cupKeys(true).contains("cup:0:0:1"));
    }

    @Test
    void rightClickPickTargetsTheCupBlockBetweenTheFramesAndMissesTheFrames(@TempDir Path dir) {
        Screen s = setup(dir);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        model.toggleStructureCup(s, 0, 0, 1);
        double[] ghost = panel.pickBoxWorldCenter("cup:0:0:1"); // центр призрака: x стойки, y = стык, z между рельсами
        model.toggleStructureCup(s, 0, 0, 1);
        double nearZ = ghost[2] - 220; // ближний рельс: блок 60 мм у края ряда (см. cupBoxes)
        // луч снизу вверх в сторону от стойки по x не попадает; через ближний блок — попадает
        assertEquals("cup:0:0:1", panel.pickCupAlongRay(up(ghost[0], nearZ)));
        assertNull(panel.pickCupAlongRay(up(ghost[0] + 400, nearZ)), "мимо стакана");
        assertNull(panel.pickCupAlongRay(up(ghost[0], ghost[2])), "между рельсами стакана нет");
    }

    @Test
    void narrowTowerHasPostsNineHundredApartAndItsFootprintShrinks(@TempDir Path dir) {
        Screen s = setup(dir);
        model.setStructureManualLayout(s, true);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        double standard = panel.pickBoxWorldCenter("frame:1:0:0")[0] - panel.pickBoxWorldCenter("frame:0:0:0")[0];
        assertEquals(1000, standard, 1e-6);

        model.setStructureTowerWidth(s, 0, shortFrame.getId());
        double narrow = panel.pickBoxWorldCenter("frame:1:0:0")[0] - panel.pickBoxWorldCenter("frame:0:0:0")[0];
        assertEquals(900, narrow, 1e-6, "короткая рама 850 + стакан 50");
        double other = panel.pickBoxWorldCenter("frame:3:0:0")[0] - panel.pickBoxWorldCenter("frame:2:0:0")[0];
        assertEquals(1000, other, 1e-6, "соседняя башня стандартной ширины");
        assertEquals(900, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).postSpacingMm(), 1e-9);
        assertEquals(951, StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).outerWidthMm(), 1e-9);
        assertEquals(1000, StructureLayoutMath.towerPostSpacingMm(s, model.getWorkspace(), 1), 1e-9);

        // центр башни не сдвинулся: оба столба симметрично
        double c0 = panel.pickBoxWorldCenter("frame:0:0:0")[0];
        double c1 = panel.pickBoxWorldCenter("frame:1:0:0")[0];
        model.setStructureTowerWidth(s, 0, null);
        double s0 = panel.pickBoxWorldCenter("frame:0:0:0")[0];
        double s1 = panel.pickBoxWorldCenter("frame:1:0:0")[0];
        assertEquals((s0 + s1) / 2.0, (c0 + c1) / 2.0, 1e-6);
    }

    @Test
    void towerWidthIsUndoableShiftsOnRemovalAndSurvivesJson(@TempDir Path dir) throws Exception {
        Screen s = setup(dir);
        model.setStructureManualLayout(s, true);
        model.setStructureTowerWidth(s, 1, shortFrame.getId());
        int depth = model.undoDepth();
        model.setStructureTowerWidth(s, 1, shortFrame.getId());
        assertEquals(depth, model.undoDepth(), "то же значение — не правка");

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen back = mapper.readValue(mapper.writeValueAsString(s), Screen.class);
        assertEquals(shortFrame.getId(), back.towerWidthFrameTypeOf(1));
        assertNull(back.towerWidthFrameTypeOf(0));

        assertTrue(model.removeStructureTower(s, 0));
        assertEquals(shortFrame.getId(), s.towerWidthFrameTypeOf(0), "ширина башни переехала на её новый номер");
        assertNull(s.towerWidthFrameTypeOf(1));
        model.undo();
        assertEquals(shortFrame.getId(), s.towerWidthFrameTypeOf(1));
        model.undo();
        assertNull(s.towerWidthFrameTypeOf(1));
    }

    @Test
    void layoutPanelTogglesCupsAndSetsTheWidthOfTheSelectedTower(@TempDir Path dir) {
        Screen s = setup(dir);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = new StructureLayoutPanel(model, view);
        model.addListener(p::syncFromModel);
        assertTrue(p.cupsCheck().isSelected());
        assertFalse(p.widthComboBox().isEnabled(), "башня не выбрана");

        view.selectTower(0);
        assertTrue(p.widthComboBox().isEnabled());
        int shortIndex = -1;
        for (int i = 0; i < p.widthComboBox().getItemCount(); i++) {
            if (String.valueOf(p.widthComboBox().getItemAt(i)).contains("Короткая 850")) {
                shortIndex = i;
            }
        }
        assertTrue(shortIndex > 0, "короткая рама из библиотеки есть в списке ширин");
        assertTrue(String.valueOf(p.widthComboBox().getItemAt(shortIndex)).contains("900"), "850 + стакан 50");
        p.widthComboBox().setSelectedIndex(shortIndex);
        assertEquals(shortFrame.getId(), s.towerWidthFrameTypeOf(0));
        p.widthComboBox().setSelectedIndex(0);
        assertNull(s.towerWidthFrameTypeOf(0));

        p.cupsCheck().doClick();
        assertTrue(s.isStructureWithoutCups());
        p.cupsCheck().doClick();
        assertFalse(s.isStructureWithoutCups());
    }
}
