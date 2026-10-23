package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Перемычки на ЛЮБОЙ перекладине вертикальной рамы (запрос пользователя 2026-10-22: «сейчас я могу ставить
 * перемычки строго на 1500, что некорректно»). Положение перемычки — номер перекладины (рама·3 + 0 низ / 1
 * середина / 2 верх), а не «уровень через 1,5 рамы»; «Предварительный расчёт» ставит их по умолчанию через
 * интервал из формы (1500) на ближайшую перекладину, руками — куда угодно (две соседние перемычки, как на
 * фото реальной башни); старые проекты (уровень) переводятся при загрузке.
 */
class StructurePeremychkaRungsTest {

    private static final double CROSS = StructureCupMath.crossbarThicknessMm(500);

    private AppModel model;
    private CabinetType type;
    private StructureFrameType frame;

    private Screen screen(Path dir, int rows) {
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
        frame = model.addStructureFrameType(f);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", type.getId(), rows, 6, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    private void calc(Screen s, double height, int back, double interval) {
        s.setStructureTowerHeightMm(height);
        int vertical = StructureCalc.suggestVerticalFramesPerTower(s, frame);
        int levels = StructureCalc.suggestPeremychkaLevelsByInterval(height, interval);
        model.updateScreenStructure(s, height, 4, vertical, back, levels, 500, 0.6, frame.getId(), null, null, 0, null,
                ScreenCurveType.FLAT, 10_000, false, 600, 2, interval);
    }

    private static Set<Integer> rungs(Screen s, int gap, int row) {
        return s.getStructurePeremychkaCells().stream().filter(c -> c.matches(gap, row, c.getLevelIndex()))
                .filter(c -> c.getTowerIndex() == gap && c.getRow() == row && !c.isHidden())
                .map(StructurePeremychkaCell::getLevelIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void autoRungsSnapToTheNearestCrossbarAtEveryInterval() {
        // 3 рамы по 950 со стаканами 50: перекладины рамы k — низ k·1000+21, середина k·1000+475, верх k·1000+929
        assertEquals(List.of(4, 8), StructureCupMath.autoRungs(950, 50, CROSS, 3, 1500, 5),
                "1500 → середина второй рамы (1475), 3000 → верх третьей (2929); 4500 выше башни");
        assertEquals(List.of(3, 6), StructureCupMath.autoRungs(950, 50, CROSS, 3, 1000, 2),
                "интервал 1000: ближе всего низ второй рамы (1021), а не верх первой (929); 2000 → низ третьей");
        assertTrue(StructureCupMath.autoRungs(950, 50, CROSS, 1, 1500, 3).isEmpty(),
                "стопка из одной рамы ниже 1500 — перемычке не к чему крепиться");
        assertTrue(StructureCupMath.autoRungs(950, 50, CROSS, 3, 1500, 0).isEmpty());
        assertEquals(List.of(4, 8), StructureCupMath.autoRungs(950, 0, CROSS, 3, 1500, 2).subList(0, 2),
                "без стаканов: 1425 (середина второй) и 2829 (верх третьей)");
    }

    @Test
    void defaultCalculationPutsPeremychkiEveryFifteenHundredOnTheNearestCrossbar(@TempDir Path dir) {
        Screen s = screen(dir, 8);
        calc(s, 3000, 2, 1500);
        assertEquals(1500, s.getStructurePeremychkaIntervalMm(), 1e-9);
        assertEquals(Set.of(4, 8), rungs(s, 0, 0), "передний ряд из 3 рам: 1475 и верх");
        // задний ряд — 2 рамы (1950): 1500 → та же перекладина; 3000 выше стопки заднего ряда
        assertEquals(Set.of(4), rungs(s, 0, 1), "задний ряд короче — перемычки только в пределах его стопки");
    }

    @Test
    void anyCrossbarAcceptsAPeremychkaAndTwoNeighbouringRungsCanBothCarryOne(@TempDir Path dir) {
        Screen s = screen(dir, 8);
        calc(s, 3000, 2, 1500);
        int before = StructureCalc.compute(s, type, model.getWorkspace()).peremychkaCount();
        // верх второй рамы (рельс 8 не занят? 8 — верх третьей) и низ третьей: соседние перекладины через стакан
        model.toggleStructurePeremychkaCell(s, 0, 0, 5);
        model.toggleStructurePeremychkaCell(s, 0, 0, 6);
        assertEquals(Set.of(4, 5, 6, 8), rungs(s, 0, 0), "перемычки на соседних перекладинах (верх 2-й, низ 3-й)");
        assertEquals(before + 2, StructureCalc.compute(s, type, model.getWorkspace()).peremychkaCount());

        model.toggleStructurePeremychkaCell(s, 0, 0, 4);
        assertFalse(rungs(s, 0, 0).contains(4), "автоматически поставленную можно убрать");
        // пересчёт с тем же интервалом ручную расстановку не трогает
        calc(s, 3000, 2, 1500);
        assertEquals(Set.of(5, 6, 8), rungs(s, 0, 0));
    }

    @Test
    void changingTheIntervalRebuildsThePeremychkiAndUndoBringsTheOldOnesBack(@TempDir Path dir) {
        Screen s = screen(dir, 8);
        calc(s, 3000, 2, 1500);
        model.toggleStructurePeremychkaCell(s, 0, 0, 0); // ручная перемычка на нижней перекладине
        assertTrue(rungs(s, 0, 0).contains(0));

        calc(s, 3000, 2, 1000);
        assertEquals(1000, s.getStructurePeremychkaIntervalMm(), 1e-9);
        assertEquals(Set.of(3, 6, 8), rungs(s, 0, 0), "по новому интервалу (1000, 2000, 3000), ручная сброшена");

        model.undo();
        assertEquals(1500, s.getStructurePeremychkaIntervalMm(), 1e-9);
        assertTrue(rungs(s, 0, 0).contains(0) && rungs(s, 0, 0).contains(4));
    }

    @Test
    void oldProjectsWithLevelIndexesAreConvertedToCrossbarNumbersOnce() {
        Screen s = new Screen();
        for (int level = 0; level < 3; level++) {
            s.getStructurePeremychkaCells().add(new StructurePeremychkaCell(0, 0, level));
        }
        assertFalse(s.isStructurePeremychkaByRung());
        ScreenLogic.migratePeremychkaToRungs(s);
        assertTrue(s.isStructurePeremychkaByRung());
        // уровень 0 = 1,5 рамы → середина второй рамы (4); 1 = 3 рамы → низ четвёртой (9); 2 = 4,5 → середина пятой (13)
        assertEquals(List.of(4, 9, 13), s.getStructurePeremychkaCells().stream()
                .map(StructurePeremychkaCell::getLevelIndex).toList());
        ScreenLogic.migratePeremychkaToRungs(s);
        assertEquals(List.of(4, 9, 13), s.getStructurePeremychkaCells().stream()
                .map(StructurePeremychkaCell::getLevelIndex).toList(), "повторно — не трогает");
    }

    @Test
    void storedProjectWithoutTheFlagIsMigratedOnLoadAndNewScreensStartOnCrossbars() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen old = mapper.readValue("{\"id\":\"s1\",\"mountType\":\"STRUCTURE\",\"structurePeremychkaCells\":["
                + "{\"towerIndex\":0,\"row\":0,\"levelIndex\":0,\"hidden\":false}]}", Screen.class);
        assertEquals(1500, old.getStructurePeremychkaIntervalMm(), 1e-9, "старый проект — интервал по умолчанию");
        ScreenLogic.dedupeStructureCells(old); // вызывается при загрузке проекта
        assertEquals(4, old.getStructurePeremychkaCells().get(0).getLevelIndex());

        Screen back = mapper.readValue(mapper.writeValueAsString(old), Screen.class);
        assertTrue(back.isStructurePeremychkaByRung());
        ScreenLogic.dedupeStructureCells(back);
        assertEquals(4, back.getStructurePeremychkaCells().get(0).getLevelIndex(), "сохранённый новый проект не переводится");
    }

    @Test
    void rungCentresFollowTheRealStackWithCupsAndShortFrames(@TempDir Path dir) {
        Screen s = screen(dir, 8);
        calc(s, 3000, 2, 1500);
        assertEquals(1000 + 475, StructureCupMath.rungCenterY(s, model.getWorkspace(), 0, 0, 4, 950, CROSS), 1e-9);
        assertEquals(CROSS / 2.0, StructureCupMath.rungCenterY(s, model.getWorkspace(), 0, 0, 0, 950, CROSS), 1e-9);
        assertEquals(1000 + 950 - CROSS / 2.0,
                StructureCupMath.rungCenterY(s, model.getWorkspace(), 0, 0, 5, 950, CROSS), 1e-9);
        model.toggleStructureCup(s, 0, 0, 1);
        assertEquals(950 + 475, StructureCupMath.rungCenterY(s, model.getWorkspace(), 0, 0, 4, 950, CROSS), 1e-9,
                "убрали стакан — перекладина опустилась на его высоту");
    }
}
