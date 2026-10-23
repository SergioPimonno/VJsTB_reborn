package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructureLayoutMath.Side;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Панель «Раскладка» окна 3D-редактора конструктива (запрос 2026-10-22, трек B): список экранов
 * группы, поза и высота экрана, ручная расстановка башен (галочка, список, поля X/Z/угол, «+ Башня»,
 * «Встык», поворот, «Удалить»), режимы «Рамы»/«Раскладка», расстановка группы «в ряд» при
 * открытии. Обычные Swing-компоненты и панель без GL — идёт в headless-среде.
 */
class StructureLayoutPanelTest {

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

    private Screen group(Path dir) {
        Screen a = addScreen(dir, "L1", 12);
        Screen b = addScreen(dir, "L2", 8);
        Scene scene = model.getCurrentScene();
        ScreenGroup g = new ScreenGroup("Left");
        scene.getScreenGroups().add(g);
        a.setGroupId(g.getId());
        b.setGroupId(g.getId());
        model.selectScreen(a);
        return a;
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Как в {@code Structure3DDialog}: панель параметров синхронизируется по оповещениям модели. */
    private StructureLayoutPanel panelFor(Structure3DPanel view) {
        StructureLayoutPanel p = new StructureLayoutPanel(model, view);
        model.addListener(p::syncFromModel);
        return p;
    }

    @Test
    void groupOpenedWithAllPosesAtZeroIsLaidOutInARowAsOneUndoStep(@TempDir Path dir) throws Exception {
        Screen a = group(dir);
        Screen b = model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1);
        assertTrue(StructureLayoutMath.groupNeedsArranging(java.util.List.of(a, b)));
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        flushEdt();
        assertEquals(7000, b.getStructurePoseXMm(), 1e-6, "L1 6000 мм + зазор 1000");
        assertEquals(2, p.screenList().getModel().getSize());
        assertTrue(p.groupMode());
        model.undo();
        assertTrue(StructureLayoutMath.groupNeedsArranging(java.util.List.of(a, b)), "расстановка — одна отмена");
    }

    @Test
    void singleScreenDisablesTheGroupFieldsButKeepsElevationAndShowsDepth(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        assertFalse(p.groupMode());
        assertFalse(p.poseXSpinner().isEnabled());
        assertFalse(p.arrangeButton().isEnabled());
        assertTrue(p.elevationSpinner().isEnabled());
        assertTrue(p.screenInfoText().contains("глубина 65"), p.screenInfoText());

        p.elevationSpinner().setValue(1200.0);
        assertEquals(1200, s.getStructureScreenElevationMm(), 1e-9);
    }

    @Test
    void poseFieldsWriteTheGroupPoseOfTheCurrentScreen(@TempDir Path dir) throws Exception {
        Screen a = group(dir);
        Screen b = model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1);
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        flushEdt();
        model.selectScreen(b);
        p.syncFromModel();
        assertEquals(b.getStructurePoseXMm(), ((Number) p.poseXSpinner().getValue()).doubleValue(), 1e-6);

        p.poseXSpinner().setValue(9000.0);
        p.poseYawSpinner().setValue(-120.0);
        assertEquals(9000, b.getStructurePoseXMm(), 1e-6);
        assertEquals(-120, b.getStructurePoseYawDeg(), 1e-6);
        assertEquals(0, a.getStructurePoseXMm(), 1e-6, "поза другого экрана не тронута");
    }

    @Test
    void screenListSelectionSelectsTheScreenInTheModel(@TempDir Path dir) throws Exception {
        Screen a = group(dir);
        Screen b = model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1);
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        flushEdt();
        p.screenList().setSelectedValue(b, false);
        assertEquals(b, model.getCurrentScreen());
        p.syncFromModel();
        assertEquals(b, p.screenList().getSelectedValue());
    }

    @Test
    void manualCheckboxCopiesTheAutoLayoutAndListsTheTowers(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        assertTrue(p.manualCheck().isEnabled());
        assertFalse(p.manualCheck().isSelected());
        assertEquals(0, p.towerList().getModel().getSize());

        p.manualCheck().doClick();
        assertTrue(s.isStructureManualLayout());
        assertEquals(2, p.towerList().getModel().getSize());
        assertTrue(p.addTowerButton().isEnabled());
        assertFalse(p.removeTowerButton().isEnabled(), "башня не выбрана");

        p.manualCheck().doClick();
        assertFalse(s.isStructureManualLayout());
        assertEquals(0, p.towerList().getModel().getSize());
    }

    @Test
    void towerListSelectionFillsTheFieldsAndTheFieldsWriteBack(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);

        p.towerList().setSelectedIndex(1);
        assertEquals(1, view.selectedTower());
        StructureTowerPlacement second = s.towerPlacementOf(1);
        double zBefore = second.getAnchorZMm();
        assertEquals(second.getAnchorXMm(), ((Number) p.towerXSpinner().getValue()).doubleValue(), 1e-6);
        assertTrue(p.removeTowerButton().isEnabled());

        p.towerXSpinner().setValue(4321.0);
        p.towerYawSpinner().setValue(45.0);
        assertEquals(45, s.towerPlacementOf(1).getYawDeg(), 1e-6);
        // угол правился отдельно от X/Z — башня повернулась НА МЕСТЕ, вокруг центра контура
        double depth = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 1).depthMm();
        double[] centre = new Placement(0, 4321, zBefore, 0).toWorld(0, -depth / 2.0);
        StructureTowerPlacement turned = s.towerPlacementOf(1);
        double[] after = new Placement(0, turned.getAnchorXMm(), turned.getAnchorZMm(), turned.getYawDeg())
                .toWorld(0, -depth / 2.0);
        assertEquals(centre[0], after[0], 1e-6);
        assertEquals(centre[1], after[1], 1e-6);
        assertTrue(p.towerList().getModel().getElementAt(1).contains(String.format("%.0f", turned.getAnchorXMm())),
                "список показывает новое значение");
    }

    @Test
    void angleFieldRotatesAboutTheCentreAndAddRemoveKeepTheSelectionConsistent(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        view.selectTower(0);
        StructureTowerPlacement before = s.towerPlacementOf(0).copy();
        double depth = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0).depthMm();
        double[] centre = new Placement(0, before.getAnchorXMm(), before.getAnchorZMm(), before.getYawDeg())
                .toWorld(0, -depth / 2.0);

        p.towerYawSpinner().setValue(StructureLayoutMath.normalizeDeg(before.getYawDeg() + 90));
        StructureTowerPlacement after = s.towerPlacementOf(0);
        assertEquals(StructureLayoutMath.normalizeDeg(before.getYawDeg() + 90), after.getYawDeg(), 1e-6);
        double[] centreAfter = new Placement(0, after.getAnchorXMm(), after.getAnchorZMm(), after.getYawDeg())
                .toWorld(0, -depth / 2.0);
        assertEquals(centre[0], centreAfter[0], 1e-6);
        assertEquals(centre[1], centreAfter[1], 1e-6);

        p.addTowerButton().doClick();
        assertEquals(3, s.getStructureTowerPlacements().size());
        assertEquals(2, view.selectedTower(), "новая башня выбрана");
        p.removeTowerButton().doClick();
        assertEquals(2, s.getStructureTowerPlacements().size());
        assertEquals(-1, view.selectedTower());
    }

    @Test
    void flushAddsATowerAtNinetyDegreesAndSelectsIt(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        view.selectTower(0);
        p.addTowerFlush(Side.RIGHT, true);
        assertEquals(3, s.getStructureTowerPlacements().size());
        assertEquals(2, view.selectedTower());
        assertEquals(StructureLayoutMath.normalizeDeg(s.towerPlacementOf(0).getYawDeg() - 90),
                s.towerPlacementOf(2).getYawDeg(), 1e-6);
        view.selectTower(-1);
        p.addTowerFlush(Side.RIGHT, true);
        assertEquals(3, s.getStructureTowerPlacements().size(), "без выбранной башни «встык» ничего не добавляет");
    }

    @Test
    void modeButtonsSwitchThePanelBetweenFramesAndLayout(@TempDir Path dir) {
        addScreen(dir, "E", 12);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        assertFalse(view.isLayoutMode());
        assertTrue(p.framesModeButton().isSelected());
        p.layoutModeButton().doClick();
        assertTrue(view.isLayoutMode());
        p.framesModeButton().doClick();
        assertFalse(view.isLayoutMode());
    }

    @Test
    void overlappingTowersAreReportedInTheWarningsLine(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        assertEquals(" ", p.warningsText());
        StructureTowerPlacement first = s.towerPlacementOf(0);
        model.setStructureTowerPlacement(s, 1, first.getAnchorXMm() + 100, first.getAnchorZMm(), 0, true, true);
        p.syncFromModel();
        assertTrue(p.warningsText().contains("пересекаются"), p.warningsText());
    }

    @Test
    void attachOperationFromThePanelPlacesTheScreenAtTheChosenAngle(@TempDir Path dir) throws Exception {
        Screen a = group(dir);
        Screen b = model.getCurrentScene().screensOf(model.getCurrentScene().groupOf(a)).get(1);
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        flushEdt();
        p.applyAttach(b, a, true, 120, true);
        assertEquals(6000, b.getStructurePoseXMm(), 1e-6);
        assertEquals(-60, b.getStructurePoseYawDeg(), 1e-6, "120° между экранами = излом на 60°");
    }

    @Test
    void screenListIsShownOnlyForAScreenFromAGroup(@TempDir Path dir) throws Exception {
        Screen single = addScreen(dir, "Одиночный", 12);
        StructureLayoutPanel p = panelFor(new Structure3DPanel(model, false));
        assertFalse(p.screenListVisible(), "экран вне группы — списка нет");
        assertTrue(p.screenNameText().contains("Одиночный"));

        Screen a = group(dir);
        model.selectScreen(a);
        flushEdt(); // отложенная авто-расстановка группы выполняется на EDT — дожидаемся её, потом синхронизируем там же
        SwingUtilities.invokeAndWait(p::syncFromModel);
        assertTrue(p.screenListVisible(), "экран из группы — список экранов группы");
        assertEquals(2, p.screenList().getModel().getSize());
        model.selectScreen(single);
        flushEdt();
        SwingUtilities.invokeAndWait(p::syncFromModel);
        assertFalse(p.screenListVisible());
    }

    @Test
    void numericEditsInARowAreOneUndoStepLikeTheMouseWheelInAField(@TempDir Path dir) {
        Screen s = addScreen(dir, "E", 12);
        model.setStructureManualLayout(s, true);
        Structure3DPanel view = new Structure3DPanel(model, false);
        StructureLayoutPanel p = panelFor(view);
        view.selectTower(0);
        int depth = model.undoDepth();
        double x0 = s.towerPlacementOf(0).getAnchorXMm();
        for (int i = 0; i < 5; i++) {
            SpinnerWheel.adjust(p.towerXSpinner(), 1, 0); // пять щелчков колеса подряд
        }
        assertEquals(depth + 1, model.undoDepth(), "серия щелчков — одна запись отмены");
        model.undo();
        assertEquals(depth, model.undoDepth());
        assertEquals(x0, s.towerPlacementOf(0).getAnchorXMm(), 1e-6, "один Ctrl+Z вернул всю серию");
    }
}
