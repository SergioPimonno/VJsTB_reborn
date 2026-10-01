package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Раздельные башни наземного конструктива (запрос пользователя 2026-10-01): изогнутый экран
 * (и прямой с зазором) строится НЕ стеной с общими столбами, а отдельными башнями по 2 столба
 * с перемычками/основанием только внутри башни. Здесь — расчёт ячеек ({@link
 * AppModel#updateScreenStructure} → {@link ScreenLogic#regenerateStructureCells}), ведомость
 * {@link StructureCalc#compute} для раздельных башен, РЕГРЕССИЯ прямого экрана с зазором 0
 * (числа те же, что до изгиба), предупреждения о коллизиях, совместимость JSON и отмена.
 */
class StructureCurvedTowersTest {

    private AppModel model;
    private CabinetType type;

    private Screen screen(Path dir, int rows, int cols) {
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
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen s = model.addScreen("E", type.getId(), rows, cols, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    /** Тот же набор стартовых чисел, что даёт «Предварительный расчёт» для рамы по умолчанию:
     *  3 сегмента переднего ряда, 2 заднего, 1 уровень перемычек, вынос 500 мм. */
    private void calc(Screen s, ScreenCurveType curve, double radius, double gap, int towers) {
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 3, 2, 1, 500, 0.6,
                null, null, null, 0, null, curve, radius, false, gap, towers);
    }

    private static Set<String> peremychkaGaps(Screen s) {
        return s.getStructurePeremychkaCells().stream().filter(c -> !c.isHidden())
                .map(c -> String.valueOf(c.getTowerIndex())).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<Integer> baseGaps(Screen s) {
        return s.getStructureBaseFrameCells().stream().filter(c -> !c.isHidden())
                .map(StructureBaseFrameCell::getTowerIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<Integer> posts(Screen s) {
        return s.getStructureFrameCells().stream().filter(c -> !c.isHidden())
                .map(StructureFrameCell::getTowerIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Регрессия: прямой экран с зазором 0 через НОВУЮ сигнатуру — те же ячейки и те же числа
     *  ведомости, что через прежнюю 13-аргументную; числа зафиксированы явно. */
    @Test
    void flatScreenWithZeroGapIsStillAWallWithUnchangedNumbers(@TempDir Path dir) {
        Screen s = screen(dir, 2, 6); // 3 м -> 4 столба стены
        calc(s, ScreenCurveType.FLAT, 10_000, 0, 0);
        assertEquals(4, s.getStructureTowerCount());
        assertEquals(Set.of(0, 1, 2, 3), posts(s));
        assertEquals(Set.of("0", "1", "2"), peremychkaGaps(s), "перемычки в КАЖДОМ промежутке стены");
        assertEquals(Set.of(0, 1, 2), baseGaps(s));
        StructureCalc.Result r = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(20, r.verticalFrameCount(), "4 столба × (3 + 2)");
        assertEquals(6, r.peremychkaCount(), "3 промежутка × 2 ряда × 1 уровень");
        assertEquals(6, r.baseFrameCount(), "3 промежутка × 2 секции (башня с задним рядом)");
        assertEquals(32, r.totalFrameCount());
        assertEquals(24, r.cupCount(), "(2 + 1) стыка × 4 столба × 2 стакана");
        assertEquals(48, r.boltCount());
        assertTrue(r.curveWarnings().isEmpty());

        Screen old = s.copy();
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 3, 2, 1, 500, 0.6,
                null, null, null, 0, null);
        assertEquals(cellKeys(old), cellKeys(s), "старая сигнатура даёт ровно те же ячейки");
    }

    private static String cellKeys(Screen s) {
        return s.getStructureFrameCells().stream().map(c -> c.getTowerIndex() + "/" + c.getRow() + "/"
                        + c.getSegmentIndex() + (c.isHidden() ? "h" : "")).sorted().collect(Collectors.joining(","))
                + "|" + s.getStructurePeremychkaCells().stream().map(c -> c.getTowerIndex() + "/" + c.getRow() + "/"
                        + c.getLevelIndex()).sorted().collect(Collectors.joining(","))
                + "|" + s.getStructureBaseFrameCells().stream().map(c -> c.getTowerIndex() + "/" + c.getSectionIndex())
                        .sorted().collect(Collectors.joining(","));
    }

    @Test
    void concaveScreenBuildsSeparateTowersOfTwoPostsWithNoConnectionsBetweenThem(@TempDir Path dir) {
        Screen s = screen(dir, 2, 12); // 6 м
        calc(s, ScreenCurveType.CONCAVE, 5000, 500, 0);
        // авто: (дуга по тыльной поверхности 6123 мм + 500) / (1051 + 500) -> 4 башни
        assertEquals(8, s.getStructureTowerCount(), "4 башни = 8 столбов");
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), posts(s));
        assertEquals(Set.of("0", "2", "4", "6"), peremychkaGaps(s), "перемычки ТОЛЬКО внутри башни (2k → 2k+1)");
        assertEquals(Set.of(0, 2, 4, 6), baseGaps(s), "основание тоже только внутри башни");
        StructureCalc.Result r = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(40, r.verticalFrameCount(), "8 столбов × (3 + 2)");
        assertEquals(8, r.peremychkaCount(), "4 башни × 2 ряда × 1 уровень");
        assertEquals(8, r.baseFrameCount(), "4 башни × 2 секции");
        assertEquals(56, r.totalFrameCount());
        assertEquals(48, r.cupCount(), "стаканы на вертикальных стыках — как раньше");
        assertEquals(84, r.boltCount());
        assertTrue(r.curveWarnings().stream().noneMatch(w -> w.contains("пересекаются")),
                "вогнутый: башни расходятся веером " + r.curveWarnings());
    }

    @Test
    void userTowerCountMeansPairsOfPostsAndFlatGapAlsoSeparates(@TempDir Path dir) {
        Screen s = screen(dir, 2, 12);
        calc(s, ScreenCurveType.FLAT, 10_000, 600, 2);
        assertEquals(4, s.getStructureTowerCount(), "2 башни пользователя = 4 столба");
        assertEquals(Set.of("0", "2"), peremychkaGaps(s));
        assertEquals(Set.of(0, 2), baseGaps(s));
        assertEquals(600, s.getStructureTowerGapMm());
        assertEquals(2, s.getStructureSeparateTowerCount());

        // обратно на прямой без зазора -- снова стена: общий столб и перемычки в каждом промежутке
        calc(s, ScreenCurveType.FLAT, 10_000, 0, 2);
        assertEquals(StructureCalc.suggestTowerCount(s, type), s.getStructureTowerCount());
        assertTrue(peremychkaGaps(s).contains("1"), "в стене промежуток 1 → 2 снова соединён");
    }

    @Test
    void towerWithoutBottomRowCabinetsIsNotBuilt(@TempDir Path dir) {
        Screen s = screen(dir, 2, 12);
        // левые 3 кабинета нижнего ряда скрыты -- под левой из 4 башен опоры нет
        for (int col = 0; col < 3; col++) {
            s.cabinetAt(1, col).setHidden(true);
        }
        calc(s, ScreenCurveType.FLAT, 10_000, 500, 4);
        assertFalse(posts(s).contains(0));
        assertFalse(posts(s).contains(1));
        assertTrue(posts(s).containsAll(Set.of(2, 3, 4, 5, 6, 7)));
        assertEquals(Set.of("2", "4", "6"), peremychkaGaps(s));
    }

    @Test
    void convexSmallRadiusWithDeepBaseWarnsWithMinimalGapButKeepsUserGap(@TempDir Path dir) {
        Screen s = screen(dir, 2, 12);
        model.updateScreenStructure(s, 1500, 0, 3, 2, 1, 2000, 0.6, null, null, null, 0, null,
                ScreenCurveType.CONVEX, 4000, false, 500, 3);
        StructureCalc.Result r = StructureCalc.compute(s, type, model.getWorkspace());
        assertTrue(r.curveWarnings().stream().anyMatch(w -> w.contains("пересекаются")
                && w.contains("Минимальный зазор")), r.curveWarnings().toString());
        assertEquals(500, s.getStructureTowerGapMm(), "зазор пользователя автоматически не меняется");
    }

    @Test
    void oldJsonWithoutCurveFieldsOpensFlatAndNewFieldsRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen old = mapper.readValue("{\"id\":\"s1\",\"name\":\"A\",\"mountType\":\"STRUCTURE\","
                + "\"structureTowerCount\":5}", Screen.class);
        assertEquals(ScreenCurveType.FLAT, old.getStructureCurveType());
        assertEquals(0, old.getStructureTowerGapMm());
        assertEquals(0, old.getStructureSeparateTowerCount());
        assertEquals(5, old.getStructureTowerCount(), "старое поле = число столбов, не тронуто");
        assertEquals(Screen.DEFAULT_CURVE_RADIUS_MM, old.getStructureCurveRadiusMm());
        assertFalse(StructureCurveMath.separateTowers(old), "старый проект — прежняя стена");

        old.setStructureCurveType(ScreenCurveType.CONVEX);
        old.setStructureCurveRadiusMm(4321);
        old.setStructureCurveByAngle(true);
        old.setStructureTowerGapMm(750);
        old.setStructureSeparateTowerCount(3);
        Screen back = mapper.readValue(mapper.writeValueAsString(old), Screen.class);
        assertEquals(ScreenCurveType.CONVEX, back.getStructureCurveType());
        assertEquals(4321, back.getStructureCurveRadiusMm());
        assertTrue(back.isStructureCurveByAngle());
        assertEquals(750, back.getStructureTowerGapMm());
        assertEquals(3, back.getStructureSeparateTowerCount());
        Screen copy = back.copy();
        assertEquals(ScreenCurveType.CONVEX, copy.getStructureCurveType());
        assertEquals(4321, copy.getStructureCurveRadiusMm());
        assertTrue(copy.isStructureCurveByAngle());
        assertEquals(750, copy.getStructureTowerGapMm());
        assertEquals(3, copy.getStructureSeparateTowerCount());

        Screen garbage = mapper.readValue("{\"id\":\"s2\",\"structureCurveRadiusMm\":-5,"
                + "\"structureTowerGapMm\":-100}", Screen.class);
        assertEquals(Screen.DEFAULT_CURVE_RADIUS_MM, garbage.getStructureCurveRadiusMm());
        assertEquals(0, garbage.getStructureTowerGapMm());
    }

    @Test
    void undoRestoresCurveFieldsTogetherWithTheWallCells(@TempDir Path dir) {
        Screen s = screen(dir, 2, 6);
        calc(s, ScreenCurveType.FLAT, 10_000, 0, 0);
        String wall = cellKeys(s);

        calc(s, ScreenCurveType.CONCAVE, 6000, 800, 2);
        assertEquals(ScreenCurveType.CONCAVE, s.getStructureCurveType());
        assertEquals(4, s.getStructureTowerCount());

        model.undo();
        assertEquals(ScreenCurveType.FLAT, s.getStructureCurveType());
        assertEquals(0, s.getStructureTowerGapMm());
        assertEquals(0, s.getStructureSeparateTowerCount());
        assertEquals(4, s.getStructureTowerCount());
        assertEquals(wall, cellKeys(s), "вместе с формой экрана откатываются и ячейки стены");
        assertFalse(StructureCurveMath.separateTowers(s));
    }

    @Test
    void togglingAPeremychkaCellIsUndoable(@TempDir Path dir) {
        Screen s = screen(dir, 2, 6);
        calc(s, ScreenCurveType.CONVEX, 8000, 500, 0);
        StructurePeremychkaCell first = s.getStructurePeremychkaCells().get(0);
        model.toggleStructurePeremychkaCell(s, first.getTowerIndex(), first.getRow(), first.getLevelIndex());
        assertTrue(s.getStructurePeremychkaCells().stream()
                .anyMatch(c -> c.matches(first.getTowerIndex(), first.getRow(), first.getLevelIndex()) && c.isHidden()));
        model.undo();
        assertTrue(s.getStructurePeremychkaCells().stream()
                .anyMatch(c -> c.matches(first.getTowerIndex(), first.getRow(), first.getLevelIndex()) && !c.isHidden()));
    }
}
