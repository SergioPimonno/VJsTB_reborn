package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureGizmoMath;
import com.vjstb.ledscheme.service.StructureOrient.TowerPose;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Отзыв 2026-10-24 («не работают хоткеи — выделение нескольких через Shift, удаление через Delete, снятие
 * выделения через Esc»): в редакторе конструктива Shift/Ctrl+клик добавляет башню к выбору, Ctrl+клик по выбранной
 * её снимает, перенос тела одной из выбранных двигает все, Delete удаляет все выбранные одной записью отмены, Esc
 * снимает выбор; список башен тоже выбирает несколько.
 */
class StructureMultiSelectionKeysTest {

    private AppModel model;
    private Screen screen;
    private Structure3DPanel view;
    private StructureLayoutPanel panel;

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
        screen = model.addScreen("E", type.getId(), 3, 36, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(screen);
        screen.setStructureTowerHeightMm(3000);
        model.updateScreenStructure(screen, 3000, 4, StructureCalc.suggestVerticalFramesPerTower(screen, null) + 3,
                2, 1, 500, 0.6, null, null, null, 0, null, ScreenCurveType.FLAT, 10_000, false, 600, 2);
        model.setStructureManualLayout(screen, true);
        // башни разнесены по X (с запасом, чтобы не пересекались); ровно 5 штук, как в окне пользователя
        while (screen.getStructureTowerPlacements().size() < 5) {
            model.addStructureTower(screen, 1500.0 * (screen.getStructureTowerPlacements().size() + 1), -400, 0);
        }
        view = new Structure3DPanel(model, false);
        view.setLayoutMode(true);
        panel = new StructureLayoutPanel(model, view);
        model.addListener(panel::syncFromModel);
        panel.syncFromModel();
    }

    /** Луч строго сверху в центр башни {@code k}. */
    private Ray above(int k) {
        var p = screen.towerPlacementOf(k);
        TowerPose pose = new TowerPose(p.getAnchorXMm(), p.getElevationMm(), p.getAnchorZMm(), p.getYawDeg(),
                p.getPitchDeg(), p.getRollDeg());
        double[] c = pose.toWorld(0, 1500, -StructureCurveMath.towerSpecOf(screen, model.getWorkspace(), k)
                .depthMm() / 2.0);
        return new Ray(new Vec3(c[0], 9000, c[2]), new Vec3(0, -1, 0));
    }

    private double x(int k) {
        return screen.towerPlacementOf(k).getAnchorXMm();
    }

    @Test
    void shiftClickAddsAndCtrlClickRemovesTowersFromTheSelection(@TempDir Path dir) {
        setup(dir);
        assertTrue(screen.getStructureTowerPlacements().size() >= 3);
        view.layoutPress(above(0));
        view.layoutRelease();
        assertEquals(List.of(0), view.selectedTowers());

        assertFalse(view.layoutPress(above(2), true, false), "Shift+клик расширяет выбор, переноса не начинает");
        assertEquals(List.of(0, 2), view.selectedTowers());
        assertEquals(2, view.selectedTower(), "главная — последняя выбранная");

        view.layoutPress(above(1), false, true);
        assertEquals(List.of(0, 1, 2), view.selectedTowers());
        view.layoutPress(above(1), false, true);
        assertEquals(List.of(0, 2), view.selectedTowers(), "Ctrl+клик по выбранной снимает её");

        view.layoutPress(above(1));
        view.layoutRelease();
        assertEquals(List.of(1), view.selectedTowers(), "обычный клик заменяет выбор");
    }

    @Test
    void dragMovesEverySelectedTowerTogetherInOneUndoStep(@TempDir Path dir) {
        setup(dir);
        double x0 = x(0);
        double x1 = x(1);
        double x2 = x(2);
        view.layoutPress(above(0));
        view.layoutRelease();
        view.layoutPress(above(1), true, false);
        assertEquals(List.of(0, 1), view.selectedTowers());

        Ray from = above(1);
        assertTrue(view.layoutPress(from));
        Ray to = new Ray(new Vec3(from.origin().x() + 400, from.origin().y(), from.origin().z()), from.direction());
        view.layoutDrag(to, false);
        view.layoutRelease();

        assertEquals(x0 + 400, x(0), 1e-6);
        assertEquals(x1 + 400, x(1), 1e-6);
        assertEquals(x2, x(2), 1e-6, "невыбранная башня на месте");
        assertEquals(List.of(0, 1), view.selectedTowers(), "выбор сохраняется");

        model.undo();
        assertEquals(x0, x(0), 1e-6);
        assertEquals(x1, x(1), 1e-6, "весь перенос откатывается одним шагом");
    }

    @Test
    void deleteKeyRemovesAllSelectedTowersAndUndoBringsThemBack(@TempDir Path dir) {
        setup(dir);
        int before = screen.getStructureTowerPlacements().size();
        view.layoutPress(above(0));
        view.layoutRelease();
        view.layoutPress(above(2), true, false);

        panel.pressKeyForTest(KeyEvent.VK_DELETE);

        assertEquals(before - 2, screen.getStructureTowerPlacements().size());
        assertTrue(view.selectedTowers().isEmpty());
        model.undo();
        assertEquals(before, screen.getStructureTowerPlacements().size(), "одна запись отмены на оба удаления");
    }

    @Test
    void deleteWithoutSelectionDoesNothingAndEscClearsTheSelection(@TempDir Path dir) {
        setup(dir);
        int before = screen.getStructureTowerPlacements().size();
        panel.pressKeyForTest(KeyEvent.VK_DELETE);
        assertEquals(before, screen.getStructureTowerPlacements().size());

        view.layoutPress(above(0));
        view.layoutRelease();
        view.layoutPress(above(1), true, false);
        assertEquals(2, view.selectedTowers().size());
        panel.pressKeyForTest(KeyEvent.VK_ESCAPE);
        assertTrue(view.selectedTowers().isEmpty());
        assertEquals(-1, view.selectedTower());
        assertEquals(before, screen.getStructureTowerPlacements().size());
    }

    @Test
    void towerListSelectsSeveralAndStaysInSyncWithTheView(@TempDir Path dir) {
        setup(dir);
        panel.towerList().setSelectedIndices(new int[]{0, 2});
        assertEquals(List.of(0, 2), view.selectedTowers());
        assertFalse(panel.towerYawSpinner().isEnabled(), "при нескольких выбранных поля одной башни недоступны");

        view.layoutPress(above(1));
        view.layoutRelease();
        assertEquals(List.of(1), view.selectedTowers());
        assertEquals(1, panel.towerList().getSelectedIndices().length);
        assertTrue(panel.towerYawSpinner().isEnabled());
        assertTrue(StructureGizmoMath.pivotLocal(StructureCurveMath.towerSpecOf(screen, model.getWorkspace(), 1)).length == 3);
    }

    @Test
    void toggleModeSwitchesBetweenFramesAndLayoutAndKeepsTheButtonsInSync(@TempDir Path dir) {
        setup(dir);
        view.setLayoutMode(false);
        panel.framesModeButton().setSelected(true);

        panel.toggleMode();
        assertTrue(view.isLayoutMode());
        assertTrue(panel.layoutModeButton().isSelected());
        assertFalse(panel.framesModeButton().isSelected());

        panel.toggleMode();
        assertFalse(view.isLayoutMode());
        assertTrue(panel.framesModeButton().isSelected());
    }

    @Test
    void spaceIsTheDefaultBindingForTheModeToggle() {
        var combo = com.vjstb.ledscheme.settings.HotkeyAction.TOGGLE_STRUCTURE_MODE.getDefaultCombo();
        assertTrue(combo.matchesKey(new KeyEvent(new javax.swing.JPanel(), KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_SPACE, ' ')));
    }
}
