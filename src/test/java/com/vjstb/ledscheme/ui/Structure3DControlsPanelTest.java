package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.stage.SetupStagePanel;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ползунки формы/радиуса/зазора/числа башен окна 3D-редактора конструктива ({@link
 * Structure3DControlsPanel}, запрос пользователя 2026-10-01). Панель — обычные Swing-компоненты
 * без GL, поэтому первые тесты идут и в headless-среде: заполнение из модели, активность у
 * прямого/изогнутого экрана, перетаскивание = одна запись отмены и живой пересчёт, строка
 * предупреждений. Последний тест (синхронизация с полями «Сетапа») строит {@code SetupStagePanel}
 * и, как прочие GUI-тесты проекта, в headless-среде пропускается.
 */
class Structure3DControlsPanelTest {

    private AppModel model;
    private Screen screen;

    private void setup(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("Dicolor");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(192);
        ct.setResolutionHeight(192);
        CabinetType type = model.addCabinetType(ct);
        StructureFrameType ft = new StructureFrameType();
        ft.setName("Рама стандартная");
        ft.setKind(StructureFrameType.Kind.FRAME);
        ft.setHeightMm(950.0);
        ft.setWidthMm(500.0);
        ft.setDepthMm(51.0);
        String frameId = model.addStructureFrameType(ft).getId();
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        screen = model.addScreen("Стена", type.getId(), 4, 28, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(screen);
        model.updateScreenStructure(screen, 2000, StructureCalc.suggestTowerCount(screen, type), 2, 2, 1, 500, 0.6,
                frameId, null, null, 0, "", ScreenCurveType.CONCAVE, 10_000, false, 500, 0);
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /** Жест мышью: зажали, провели по значениям, отпустили — так JSlider сообщает о нём. */
    private static void drag(JSlider slider, int... values) {
        slider.setValueIsAdjusting(true);
        for (int v : values) {
            slider.setValue(v);
        }
        slider.setValueIsAdjusting(false);
    }

    @Test
    void panelIsFilledFromTheCurrentScreen(@TempDir Path dir) throws Exception {
        setup(dir);
        onEdt(() -> {
            Structure3DControlsPanel p = new Structure3DControlsPanel(model);
            assertEquals(ScreenCurveType.CONCAVE, p.shapeCombo().getSelectedItem());
            assertEquals(15, p.radiusSlider().getValue(), "R = 10 м");
            assertEquals(1, p.gapSlider().getValue(), "500 мм");
            assertEquals(1, p.gapSlider().getMinimum(), "у изогнутого зазор не меньше 500");
            assertEquals(0, p.towersSlider().getValue(), "авто");
            assertEquals(18, p.towersSlider().getMaximum(), "вдвое больше авто (9)");
            assertTrue(p.radiusSlider().isEnabled() && p.towersSlider().isEnabled());
            assertTrue(p.radiusText().contains("между кабинетами") && p.radiusText().contains("дуга"), p.radiusText());
            assertTrue(p.towersText().contains("авто (9)"), p.towersText());
        });
    }

    @Test
    void draggingRadiusRecalculatesLiveWithOneUndoEntry(@TempDir Path dir) throws Exception {
        setup(dir);
        int depth = model.undoDepth();
        onEdt(() -> {
            Structure3DControlsPanel p = new Structure3DControlsPanel(model);
            model.addListener(p::syncFromModel); // как окно 3D-редактора
            drag(p.radiusSlider(), 14, 12, 10, 9);
            assertEquals(StructureSliderMath.tickToRadiusMm(9), screen.getStructureCurveRadiusMm());
            assertEquals(depth + 1, model.undoDepth(), "одна запись на жест");
            assertEquals(9, p.radiusSlider().getValue());
            model.undo();
            assertEquals(10_000, screen.getStructureCurveRadiusMm());
            assertEquals(15, p.radiusSlider().getValue(), "после Ctrl+Z ползунок вернулся вслед за моделью");
        });
    }

    @Test
    void flatShapeDisablesRadiusAndTowerCountAndZeroGapIsAWall(@TempDir Path dir) throws Exception {
        setup(dir);
        onEdt(() -> {
            Structure3DControlsPanel p = new Structure3DControlsPanel(model);
            model.addListener(p::syncFromModel);
            p.shapeCombo().setSelectedItem(ScreenCurveType.FLAT);
            assertEquals(ScreenCurveType.FLAT, screen.getStructureCurveType());
            assertFalse(p.radiusSlider().isEnabled(), "у прямого экрана радиус неактивен");
            assertEquals(0, p.gapSlider().getMinimum(), "у прямого 0 разрешён — стена");
            drag(p.gapSlider(), 0);
            assertEquals(0, screen.getStructureTowerGapMm());
            assertFalse(p.towersSlider().isEnabled(), "стена — число башен не задаётся");
            assertTrue(p.gapText().contains("стена"), p.gapText());
            assertEquals(15, screen.getStructureTowerCount());
        });
    }

    @Test
    void towerCountAndCollisionWarningsAreShown(@TempDir Path dir) throws Exception {
        setup(dir);
        onEdt(() -> {
            Structure3DControlsPanel p = new Structure3DControlsPanel(model);
            model.addListener(p::syncFromModel);
            drag(p.towersSlider(), 3, 5, 7);
            assertEquals(7, screen.getStructureSeparateTowerCount());
            assertEquals(14, screen.getStructureTowerCount());
            assertEquals("Башен: 7", p.towersText());
            p.shapeCombo().setSelectedItem(ScreenCurveType.CONVEX);
            drag(p.radiusSlider(), 1); // 3 м, выпуклый — основания пересекаются
            assertTrue(p.statusText().contains("пересекаются"), p.statusText());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        Field f = SetupStagePanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    /** Оба места показывают одно состояние модели: ползунок → поля «Сетапа», поле «Сетапа»
     *  (через модель) → ползунок. */
    @Test
    void slidersAndSetupFieldsShowTheSameModelState(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        setup(dir);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SetupStagePanel[] setupRef = new SetupStagePanel[1];
        Structure3DControlsPanel[] ref = new Structure3DControlsPanel[1];
        onEdt(() -> {
            setupRef[0] = new SetupStagePanel(model, settings);
            ref[0] = new Structure3DControlsPanel(model);
            model.addListener(ref[0]::syncFromModel);
        });
        onEdt(() -> drag(ref[0].gapSlider(), 2, 3));
        onEdt(() -> { });
        onEdt(() -> { });
        JSpinner gap = field(setupRef[0], "pStructureTowerGap");
        assertEquals(1500.0, ((Number) gap.getValue()).doubleValue(), "поле «Сетапа» вслед за ползунком");

        onEdt(() -> drag(ref[0].towersSlider(), 6));
        onEdt(() -> { });
        JSpinner towers = field(setupRef[0], "pStructureSeparateTowers");
        assertEquals(6, ((Number) towers.getValue()).intValue());

        onEdt(() -> model.updateScreenStructure(screen, 2000, 0, 2, 2, 1, 500, 0.6,
                screen.getStructureFrameTypeId(), null, null, 0, "", ScreenCurveType.CONCAVE, 20_000, false,
                2500, 0));
        onEdt(() -> { });
        assertEquals(35, ref[0].radiusSlider().getValue(), "20 м — ползунок вслед за моделью");
        assertEquals(5, ref[0].gapSlider().getValue());
        assertEquals(0, ref[0].towersSlider().getValue());
    }
}
