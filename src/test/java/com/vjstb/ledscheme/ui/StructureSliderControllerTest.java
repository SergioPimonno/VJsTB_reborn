package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Пересчёт конструктива ползунками 3D-редактора (запрос пользователя 2026-10-01) — {@link
 * StructureSliderController}, без Swing/GL: тот же результат, что у «Предварительного расчёта»
 * (остальные параметры — из текущего экрана), ОДНА запись отмены на жест перетаскивания
 * (промежуточные шаги не копят записи и не пишут файл рабочей области), зажим зазора, смена
 * экрана посреди жеста, неприменимость к экрану не на наземном конструктиве.
 */
class StructureSliderControllerTest {

    private AppModel model;
    private CabinetType type;
    private String frameId;
    private File file;

    private Screen screen(Path dir, String name) {
        if (model == null) {
            file = new File(dir.toFile(), "workspace.json");
            model = new AppModel(new WorkspaceStore(file));
            CabinetType ct = new CabinetType();
            ct.setName("Dicolor");
            ct.setWidthMm(500);
            ct.setHeightMm(500);
            ct.setDepthMm(65.0);
            ct.setResolutionWidth(192);
            ct.setResolutionHeight(192);
            type = model.addCabinetType(ct);
            StructureFrameType ft = new StructureFrameType();
            ft.setName("Рама стандартная");
            ft.setKind(StructureFrameType.Kind.FRAME);
            ft.setHeightMm(950.0);
            ft.setWidthMm(500.0);
            ft.setDepthMm(51.0);
            frameId = model.addStructureFrameType(ft).getId();
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
        }
        Screen s = model.addScreen(name, type.getId(), 4, 28, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        // как после «Предварительного расчёта» пользователя: вогнутый R = 10 м, зазор 500, авто
        model.updateScreenStructure(s, 2000, StructureCalc.suggestTowerCount(s, type), 2, 2, 1, 1500, 0.7,
                frameId, null, null, 0, "заметка", ScreenCurveType.CONCAVE, 10_000, false, 500, 0);
        return s;
    }

    private static String cells(Screen s) {
        return s.getStructureFrameCells().stream().map(c -> c.getTowerIndex() + "/" + c.getRow() + "/"
                        + c.getSegmentIndex() + (c.isHidden() ? "h" : "")).sorted().collect(Collectors.joining(","))
                + "|" + s.getStructurePeremychkaCells().stream().map(c -> c.getTowerIndex() + "/" + c.getRow() + "/"
                        + c.getLevelIndex() + (c.isHidden() ? "h" : "")).sorted().collect(Collectors.joining(","))
                + "|" + s.getStructureBaseFrameCells().stream().map(c -> c.getTowerIndex() + "/" + c.getSectionIndex()
                        + (c.isHidden() ? "h" : "")).sorted().collect(Collectors.joining(","));
    }

    @Test
    void dragGestureMakesExactlyOneUndoEntryAndWritesWorkspaceOnlyOnRelease(@TempDir Path dir) throws Exception {
        Screen s = screen(dir, "Стена");
        String before = cells(s);
        int depth = model.undoDepth();
        String savedBefore = Files.readString(file.toPath());
        StructureSliderController c = new StructureSliderController(model);

        for (int tick = 15; tick >= 5; tick--) { // тянем радиус от 10 м к 5 м
            assertTrue(c.apply(ScreenCurveType.CONCAVE, StructureSliderMath.tickToRadiusMm(tick), 500, 0, true));
            assertTrue(c.inGesture());
        }
        assertEquals(depth + 1, model.undoDepth(), "одна запись на весь жест, а не на каждый шаг");
        assertEquals(5_000, s.getStructureCurveRadiusMm(), "модель пересчитана живьём");
        assertEquals(savedBefore, Files.readString(file.toPath()), "во время перетаскивания файл не пишется");

        c.apply(ScreenCurveType.CONCAVE, 5_000, 500, 0, false); // отпустили
        assertFalse(c.inGesture());
        assertEquals(depth + 1, model.undoDepth());
        assertTrue(Files.readString(file.toPath()).contains("5000"), "по отпусканию — сохранено");

        model.undo();
        assertEquals(10_000, s.getStructureCurveRadiusMm(), "Ctrl+Z — состояние до начала жеста");
        assertEquals(before, cells(s));
    }

    @Test
    void singleClickIsItsOwnGestureAndOtherParametersComeFromTheScreen(@TempDir Path dir) {
        Screen s = screen(dir, "Стена");
        int depth = model.undoDepth();
        StructureSliderController c = new StructureSliderController(model);
        c.apply(ScreenCurveType.CONCAVE, 12_000, 1000, 0, false);
        c.apply(ScreenCurveType.CONCAVE, 12_000, 1000, 7, false);
        assertEquals(depth + 2, model.undoDepth(), "щелчок по шкале — отдельная запись");
        assertEquals(7, s.getStructureSeparateTowerCount());
        assertEquals(14, s.getStructureTowerCount(), "7 башен = 14 столбов");
        assertEquals(1500, s.getStructureBaseExtensionMm(), "вынос — с экрана");
        assertEquals(0.7, s.getStructureBallastRatio(), 1e-9);
        assertEquals(frameId, s.getStructureFrameTypeId());
        assertEquals("заметка", s.getStructureNotes());
        assertEquals(2000, s.getStructureTowerHeightMm());
    }

    /** Тот же путь, что кнопка: ячейки после ползунка совпадают с прямым вызовом
     *  updateScreenStructure с теми же числами, что считает «Предварительный расчёт». */
    @Test
    void sliderResultEqualsPreliminaryCalculationWithTheSameValues(@TempDir Path dir) {
        Screen a = screen(dir, "A");
        new StructureSliderController(model).apply(ScreenCurveType.CONVEX, 15_000, 1500, 0, false);
        Screen b = screen(dir, "B");
        model.updateScreenStructure(b, 2000, StructureCalc.suggestTowerCount(b, type), 2, 2, 1, 1500, 0.7,
                frameId, null, null, 0, "заметка", ScreenCurveType.CONVEX, 15_000, false, 1500, 0);
        assertEquals(cells(b), cells(a));
        assertEquals(b.getStructureTowerCount(), a.getStructureTowerCount());
    }

    @Test
    void gapIsClampedAndFlatZeroGapReturnsToWall(@TempDir Path dir) {
        Screen s = screen(dir, "Стена");
        StructureSliderController c = new StructureSliderController(model);
        c.apply(ScreenCurveType.CONCAVE, 10_000, 0, 0, false);
        assertEquals(500, s.getStructureTowerGapMm(), "изогнутый — не меньше 500 мм");
        c.apply(ScreenCurveType.FLAT, 10_000, 0, 0, false);
        assertEquals(0, s.getStructureTowerGapMm());
        assertEquals(15, s.getStructureTowerCount(), "прямой без зазора — стена 15 столбов");
        assertTrue(c.warnings().isEmpty(), "у стены предупреждений изгиба нет");
    }

    @Test
    void switchingScreenMidGestureStartsANewUndoEntry(@TempDir Path dir) {
        Screen first = screen(dir, "A");
        Screen second = screen(dir, "B");
        model.selectScreen(first);
        StructureSliderController c = new StructureSliderController(model);
        c.apply(ScreenCurveType.CONCAVE, 8_000, 500, 0, true);
        assertEquals(8_000, first.getStructureCurveRadiusMm());
        model.selectScreen(second); // смена экрана сама чистит экранные записи отмены
        int depth = model.undoDepth();
        c.apply(ScreenCurveType.CONCAVE, 7_000, 500, 0, true);
        assertEquals(depth + 1, model.undoDepth(), "другой экран — новый жест, своя запись");
        c.apply(ScreenCurveType.CONCAVE, 7_000, 500, 0, false);
        assertEquals(depth + 1, model.undoDepth());
        assertEquals(7_000, second.getStructureCurveRadiusMm());
        assertEquals(8_000, first.getStructureCurveRadiusMm(), "первый экран не задет");
        model.undo();
        assertEquals(10_000, second.getStructureCurveRadiusMm(), "Ctrl+Z откатывает именно второй экран");
    }

    @Test
    void screenNotOnGroundStructureIsLeftUntouched(@TempDir Path dir) {
        screen(dir, "A");
        Screen rigged = model.addScreen("Подвес", type.getId(), 2, 4, 0, 0, ScreenMountType.RIGGED);
        model.selectScreen(rigged);
        StructureSliderController c = new StructureSliderController(model);
        int depth = model.undoDepth();
        assertFalse(c.applicable());
        assertFalse(c.apply(ScreenCurveType.CONCAVE, 5_000, 500, 0, false));
        assertEquals(depth, model.undoDepth());
        assertEquals(ScreenCurveType.FLAT, rigged.getStructureCurveType());
    }

    @Test
    void convexSmallRadiusReportsCollisionWarningForTheStatusLine(@TempDir Path dir) {
        screen(dir, "Стена");
        StructureSliderController c = new StructureSliderController(model);
        c.apply(ScreenCurveType.CONVEX, 3_000, 500, 0, false);
        assertTrue(c.warnings().stream().anyMatch(w -> w.contains("пересекаются")), c.warnings().toString());
        assertTrue(c.autoTowerCount() >= 1);
    }
}
