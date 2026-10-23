package com.vjstb.ledscheme.service;

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
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Стаканы и высоты рам (запрос пользователя 2026-10-22): нормальная рама 950 мм, стакан между рамами
 * даёт ещё 50 — ровно метр на модуль; бывает, что собирают без стаканов (ПКМ по стакану — убрать, башня
 * пересчитывается); в стойку можно поставить короткую раму (850) — башня ниже, а если короткая рама
 * есть в библиотеке и помещается в остаток высоты, расчёт добивает ею башню сверху. Раньше стакан был
 * кубиком без высоты, рамы стояли с шагом 950, а высота башни бралась из введённого числа.
 */
class StructureCupsTest {

    private AppModel model;
    private CabinetType type;
    private StructureFrameType frame;
    private StructureFrameType shortFrame;

    private Screen screen(Path dir, int rows, boolean withShortFrame) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(10);
        type = model.addCabinetType(ct);
        StructureFrameType f = new StructureFrameType();
        f.setName("Рама 950");
        f.setKind(StructureFrameType.Kind.FRAME);
        f.setHeightMm(950.0);
        f.setWidthMm(500.0);
        f.setDepthMm(51.0);
        frame = model.addStructureFrameType(f);
        if (withShortFrame) {
            StructureFrameType sf = new StructureFrameType();
            sf.setName("Короткая 850");
            sf.setKind(StructureFrameType.Kind.SHORT_FRAME);
            sf.setHeightMm(850.0);
            sf.setWidthMm(500.0);
            sf.setDepthMm(51.0);
            shortFrame = model.addStructureFrameType(sf);
        }
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", type.getId(), rows, 6, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    /** «Предварительный расчёт» так, как его делает форма: число рам — по высоте башни. */
    private void calc(Screen s, double towerHeight) {
        s.setStructureTowerHeightMm(towerHeight);
        int vertical = StructureCalc.suggestVerticalFramesPerTower(s, frame);
        model.updateScreenStructure(s, towerHeight, 4, vertical, 1, 1, 500, 0.6, frame.getId(), null, null, 0, null,
                ScreenCurveType.FLAT, 10_000, false, 600, 2);
    }

    private double bottom(Screen s, int post, int row, int seg) {
        return StructureCupMath.frameBottomY(s, model.getWorkspace(), post, row, seg, 950);
    }

    @Test
    void suggestedFrameCountAccountsForTheCupBetweenFrames() {
        Screen s = new Screen();
        s.setStructureTowerHeightMm(2850);
        StructureFrameType f = new StructureFrameType();
        f.setHeightMm(950.0);
        assertEquals(2, StructureCalc.suggestVerticalFramesPerTower(s, f), "2 рамы + стакан = 1950; 3 рамы со стаканами = 2950 > 2850");
        s.setStructureTowerHeightMm(2950);
        assertEquals(3, StructureCalc.suggestVerticalFramesPerTower(s, f), "три рамы и два стакана = ровно 2950");
        s.setStructureWithoutCups(true);
        s.setStructureTowerHeightMm(2850);
        assertEquals(3, StructureCalc.suggestVerticalFramesPerTower(s, f), "без стаканов — три рамы по 950");
    }

    @Test
    void frameBottomIsTheSumOfRealFrameHeightsPlusCupsAtTheJoints(@TempDir Path dir) {
        Screen s = screen(dir, 8, true);
        calc(s, 3000);
        assertEquals(0, bottom(s, 0, 0, 0), 1e-9);
        assertEquals(1000, bottom(s, 0, 0, 1), 1e-9, "рама 950 + стакан 50 — метр");
        assertEquals(2000, bottom(s, 0, 0, 2), 1e-9);

        // стык перед третьей рамой без стакана — она стоит прямо на второй
        model.toggleStructureCup(s, 0, 0, 2);
        assertEquals(1950, bottom(s, 0, 0, 2), 1e-9);
        assertEquals(2000, bottom(s, 0, 1, 2), 1e-9, "задний ряд и другой столб не тронуты");
        assertEquals(2000, bottom(s, 1, 0, 2), 1e-9);

        // короткая рама во второй позиции: 950 + 50 + 850 + 50
        StructureFrameCell second = s.getStructureFrameCells().stream()
                .filter(c -> c.matches(1, 0, 1)).findFirst().orElseThrow();
        second.setFrameTypeId(shortFrame.getId());
        assertEquals(1900, bottom(s, 1, 0, 2), 1e-9);
        assertEquals(850, StructureCupMath.frameHeightMm(s, model.getWorkspace(), 1, 0, 1, 950), 1e-9);
    }

    @Test
    void towerHeightInTheSpecIsTheRealAssembledHeightAndFollowsTheCups(@TempDir Path dir) {
        Screen s = screen(dir, 8, false);
        calc(s, 3000);
        assertEquals(3, s.getStructureVerticalFramesPerTower());
        StructureCalc.Result before = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(2950, before.totalTowerHeightMm(), 1e-9, "3 × 950 + 2 × 50");
        assertFalse(before.exceedsScreenHeightWarning());

        int cupsBefore = before.cupCount();
        model.toggleStructureCup(s, 0, 0, 2);
        StructureCalc.Result after = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(2950, after.totalTowerHeightMm(), 1e-9, "в ведомости — самая высокая из башен");
        assertEquals(2900, bottom(s, 0, 0, 2) + 950, 1e-9, "а у этой башни убрали стакан — она ниже на 50 мм");
        assertEquals(cupsBefore - 2, after.cupCount(), "на стыке пара стаканов (у обоих рельсов)");

        model.toggleStructureCup(s, 0, 0, 2);
        assertEquals(cupsBefore, StructureCalc.compute(s, type, model.getWorkspace()).cupCount(), "вернули");
    }

    @Test
    void shortFrameStandingInTheStackLowersTheTower(@TempDir Path dir) {
        Screen s = screen(dir, 8, true);
        calc(s, 3000);
        for (StructureFrameCell c : s.getStructureFrameCells()) {
            if (c.getRow() == 0 && c.getSegmentIndex() == 2 && c.getTowerIndex() == 0) {
                c.setFrameTypeId(shortFrame.getId());
            }
        }
        // башни 1, 2, 3 остались по 2950, а башню 0 «верх» (850 вместо 950) — 2850; максимум по экрану не меняется
        assertEquals(2850, StructureCupMath.frameBottomY(s, model.getWorkspace(), 0, 0, 2, 950)
                + StructureCupMath.frameHeightMm(s, model.getWorkspace(), 0, 0, 2, 950), 1e-9);
    }

    @Test
    void withoutCupsTheTowerIsRecomputedForTheNewPitchAndEverythingComesBackWithUndo(@TempDir Path dir) {
        Screen s = screen(dir, 6, false);
        calc(s, 2850);
        assertEquals(2, s.getStructureVerticalFramesPerTower(), "со стаканами две рамы и стакан = 1950");
        int cups = StructureCalc.compute(s, type, model.getWorkspace()).cupCount();
        assertTrue(cups > 0);

        model.setStructureCupsEnabled(s, false);
        assertTrue(s.isStructureWithoutCups());
        assertEquals(3, s.getStructureVerticalFramesPerTower(), "без стаканов 3 × 950 = 2850 — ровно высота");
        StructureCalc.Result noCups = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(0, noCups.cupCount());
        assertEquals(2850, noCups.totalTowerHeightMm(), 1e-9);
        assertEquals(950, bottom(s, 0, 0, 1), 1e-9, "рамы вплотную");

        model.undo();
        assertFalse(s.isStructureWithoutCups());
        assertEquals(2, s.getStructureVerticalFramesPerTower());
        assertEquals(cups, StructureCalc.compute(s, type, model.getWorkspace()).cupCount());

        model.toggleStructureCup(s, 0, 0, 1);
        model.setStructureCupsEnabled(s, false);
        model.setStructureCupsEnabled(s, true);
        assertTrue(s.getStructureRemovedCups().isEmpty(), "включение возвращает и убранные стыки");
        assertEquals(2, s.getStructureVerticalFramesPerTower());
    }

    @Test
    void enablingCupsOnAnUncalculatedScreenOnlyFlipsTheFlag(@TempDir Path dir) {
        Screen s = screen(dir, 6, false);
        model.setStructureCupsEnabled(s, false);
        assertTrue(s.isStructureWithoutCups());
        assertEquals(0, s.getStructureVerticalFramesPerTower());
        assertTrue(s.getStructureFrameCells().isEmpty());
    }

    @Test
    void aShortFrameFromTheLibraryTopsUpTheTowerWhenItFitsTheRemainingHeight(@TempDir Path dir) {
        Screen s = screen(dir, 8, true);
        calc(s, 3850); // 3 рамы + 2 стакана = 2950; остаток 900 ≥ стакан 50 + короткая 850
        assertEquals(4, s.getStructureVerticalFramesPerTower());
        for (StructureFrameCell c : s.getStructureFrameCells()) {
            if (c.getRow() == 0 && c.getSegmentIndex() == 3) {
                assertEquals(shortFrame.getId(), c.getFrameTypeId(), "верхняя рама переднего ряда — короткая");
            } else if (c.getRow() == 0) {
                assertNull(c.getFrameTypeId());
            }
        }
        StructureCalc.Result r = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(3850, r.totalTowerHeightMm(), 1e-9, "3 × 950 + 850 + 3 стакана — ровно");

        calc(s, 3000); // остаток после трёх рам — 50: короткая не помещается, добивать нечем
        assertEquals(3, s.getStructureVerticalFramesPerTower());
    }

    @Test
    void withoutAShortFrameInTheLibraryNothingIsAddedOnTop(@TempDir Path dir) {
        Screen s = screen(dir, 8, false);
        calc(s, 3850);
        assertEquals(3, s.getStructureVerticalFramesPerTower());
        assertTrue(s.getStructureFrameCells().stream().allMatch(c -> c.getFrameTypeId() == null));
    }

    @Test
    void removedCupsFollowTheirTowerWhenAnotherTowerIsRemovedAndSurviveUndoAndJson(@TempDir Path dir)
            throws Exception {
        Screen s = screen(dir, 8, false);
        calc(s, 3000);
        model.setStructureManualLayout(s, true);
        model.toggleStructureCup(s, 4, 0, 1); // башня 2 (столбы 4, 5)
        model.toggleStructureCup(s, 0, 1, 2); // башня 0
        assertTrue(StructureCupMath.isRemoved(s, 4, 0, 1));

        model.addStructureTower(s, 20_000, 0, 0);
        assertTrue(model.removeStructureTower(s, 0));
        assertTrue(StructureCupMath.isRemoved(s, 2, 0, 1), "стык башни 1 (была 2) переехал на её новый номер");
        assertFalse(StructureCupMath.isRemoved(s, 0, 1, 2), "убранной башни стык исчез");
        assertEquals(1, s.getStructureRemovedCups().size());

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen back = mapper.readValue(mapper.writeValueAsString(s), Screen.class);
        assertTrue(StructureCupMath.isRemoved(back, 2, 0, 1));
        assertFalse(back.isStructureWithoutCups());
        Screen old = mapper.readValue("{\"id\":\"s1\",\"mountType\":\"STRUCTURE\"}", Screen.class);
        assertTrue(old.getStructureRemovedCups().isEmpty(), "старый проект — стаканы везде");

        model.undo();
        model.undo();
        assertTrue(StructureCupMath.isRemoved(s, 4, 0, 1), "отмена вернула исходные номера");
    }

    @Test
    void rungOfThePeremychkaFollowsTheRealStack(@TempDir Path dir) {
        Screen s = screen(dir, 8, false);
        calc(s, 3000);
        // уровень 0: 1,5 высоты рамы = середина второй рамы; со стаканом вторая рама на 50 выше
        assertEquals(1425 + 50, StructureCupMath.rungY(s, model.getWorkspace(), 0, 0, 1425, 950), 1e-9);
        model.setStructureCupsEnabled(s, false);
        assertEquals(1425, StructureCupMath.rungY(s, model.getWorkspace(), 0, 0, 1425, 950), 1e-9);
    }
}
