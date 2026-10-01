package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Калькулятор напольного каркаса {@link FloorCalc} (запрос пользователя 2026-10-01). Числа в
 * первом тесте — пример самого пользователя (экран 14×6 кабинетов 500×500 → 7 рам в ряду × 6
 * рядов = 42 рамы); остальные проверяют правила, которые пользователь сформулировал явно, —
 * хвост цепочки без рамы (а не лишняя рама), цепочки вокруг скрытых кабинетов, стыки между
 * рядами по перекрытию, зубы только на опёртые кабинеты, средняя нагрузка кг/м² — и
 * совместимость модели (старый JSON без нового поля, отмена).
 */
class FloorCalcTest {

    private AppModel model;

    private AppModel freshModel(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        return model;
    }

    private CabinetType cabinet(double w, double h, double weightKg) {
        CabinetType ct = new CabinetType();
        ct.setName("Пол " + w + "x" + h);
        ct.setWidthMm(w);
        ct.setHeightMm(h);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weightKg);
        return model.addCabinetType(ct);
    }

    private StructureFrameType frame(double longMm, double shortMm, double weightKg) {
        StructureFrameType f = new StructureFrameType();
        f.setName("Рама " + (int) longMm);
        f.setKind(StructureFrameType.Kind.FRAME);
        f.setHeightMm(longMm);
        f.setWidthMm(shortMm);
        f.setDepthMm(51.0);
        f.setWeightKg(weightKg);
        return model.addStructureFrameType(f);
    }

    private StructureFrameType cup(Double gapMm) {
        StructureFrameType c = new StructureFrameType();
        c.setName("Стакан");
        c.setKind(StructureFrameType.Kind.CUP);
        c.setHeightMm(gapMm);
        return model.addStructureFrameType(c);
    }

    private Screen floorScreen(CabinetType t, int rows, int cols) {
        Screen s = model.addScreen("Пол", t.getId(), rows, cols, 0, 0, ScreenMountType.FLOOR);
        model.selectScreen(s);
        return s;
    }

    @Test
    void usersExample14x6Gives42FramesAndExactHardwareCounts(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 0);
        Screen s = floorScreen(t, 6, 14);
        s.setStructureFrameTypeId(frame(950, 500, 0).getId());
        s.setStructureCupTypeId(cup(50.0).getId());

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(2, r.cabinetsPerFrameX(), "шаг 950+50 = 1000 мм = 2 кабинета по 500");
        assertEquals(1, r.cabinetsPerFrameY(), "ширина рамы 500 мм = 1 кабинет");
        assertEquals(42, r.frameCount(), "7 рам в ряду × 6 рядов");
        assertEquals(36, r.shortSideJointCount(), "6 стыков в ряду × 6 рядов");
        assertEquals(72, r.cupCount(), "2 стакана на стык по короткой стороне");
        assertEquals(35, r.longSideJointCount(), "7 рам × 5 межрядных границ");
        assertEquals(142, r.boltCount(), "2 болта на каждый стык: 2×(36+35)");
        assertEquals(168, r.legCount(), "4 ножки на раму");
        assertEquals(336, r.toothCount(), "84 кабинета × 4 зуба (по умолчанию)");
        assertEquals(0, r.unsupportedCabinetCount());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());

        s.setFloorTeethPerCabinet(2);
        assertEquals(168, FloorCalc.compute(s, t, model.getWorkspace()).toothCount(), "84 кабинета × 2 зуба");
    }

    @Test
    void oddColumnCountLeavesTailUnsupportedWithoutExtraFrameAndNoTeethOnIt(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 0);
        Screen s = floorScreen(t, 6, 13);
        s.setStructureFrameTypeId(frame(950, 500, 0).getId());

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(36, r.frameCount(), "6 рам в ряду — лишняя рама на 13-ю колонку не ставится");
        assertEquals(6, r.unsupportedCabinetCount(), "по одному хвостовому кабинету в каждом ряду");
        for (FloorCalc.CabinetCell c : r.unsupportedCabinets()) {
            assertEquals(12, c.col(), "неопёрта именно последняя колонка");
        }
        assertEquals((78 - 6) * 4, r.toothCount(), "зубы только на опёртые кабинеты");
        assertEquals(30, r.shortSideJointCount());
        assertEquals(30, r.longSideJointCount());
        assertEquals(144, r.legCount());
        assertFalse(r.warnings().isEmpty(), "о неопёртых кабинетах есть предупреждение");
    }

    @Test
    void hiddenCabinetsBreakChainsAndRowsJoinByOverlap(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 0);
        Screen s = floorScreen(t, 2, 7);
        s.setStructureFrameTypeId(frame(950, 500, 0).getId());
        // Ряд 0: скрыт кабинет колонки 2 -> цепочки [0..1] и [3..6]; ряд 1 целый -> [0..6].
        s.cabinetAt(0, 2).setHidden(true);

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(List.of(
                new FloorCalc.FramePlacement(0, 0, 1, 2),
                new FloorCalc.FramePlacement(0, 3, 1, 2),
                new FloorCalc.FramePlacement(0, 5, 1, 2),
                new FloorCalc.FramePlacement(1, 0, 1, 2),
                new FloorCalc.FramePlacement(1, 2, 1, 2),
                new FloorCalc.FramePlacement(1, 4, 1, 2)), r.frames());
        // Короткая сторона: в ряду 0 стык только внутри цепочки [3..6] (через скрытый
        // кабинет стыка нет), в ряду 1 — два.
        assertEquals(3, r.shortSideJointCount());
        // Длинная сторона — по перекрытию отрезков колонок: [0,2)-[0,2), [3,5)-[2,4),
        // [3,5)-[4,6), [5,7)-[4,6) — рама может стыковаться с двумя рамами соседнего ряда.
        assertEquals(4, r.longSideJointCount());
        assertEquals(2 * (3 + 4), r.boltCount());
        assertEquals(List.of(new FloorCalc.CabinetCell(1, 6)), r.unsupportedCabinets(),
                "скрытый кабинет не считается неопёртым — его просто нет");
        assertEquals(13, r.visibleCabinetCount());
        assertEquals(12 * 4, r.toothCount());
    }

    @Test
    void averageLoadIsCabinetsPlusFramesOverScreenArea(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 10);
        Screen s = floorScreen(t, 6, 14);
        s.setStructureFrameTypeId(frame(950, 500, 12).getId());

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(840, r.cabinetWeightKg(), 1e-9, "84 кабинета × 10 кг");
        assertEquals(504, r.frameWeightKg(), 1e-9, "42 рамы × 12 кг");
        assertEquals(21, r.areaM2(), 1e-9, "7 м × 3 м");
        assertEquals((840.0 + 504.0) / 21.0, r.averageLoadKgPerM2(), 1e-9);
    }

    @Test
    void withoutLibraryFrameAndCupDefaultsTo950x500AndGap50(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 10);
        Screen s = floorScreen(t, 6, 14);

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(1000, r.framePitchXMm(), 1e-9);
        assertEquals(500, r.framePitchYMm(), 1e-9);
        assertEquals(42, r.frameCount());
        assertEquals(0, r.frameWeightKg(), 1e-9, "вес рам без записи библиотеки не известен");
        assertNull(r.frameTypeName());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Тип рамы не выбран")));
    }

    @Test
    void cupGapComesFromLibraryAndMismatchedPitchGivesWarning(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 0);
        Screen s = floorScreen(t, 2, 4);
        s.setStructureFrameTypeId(frame(950, 500, 0).getId());
        s.setStructureCupTypeId(cup(150.0).getId());

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(150, r.cupGapMm(), 1e-9, "зазор стакана — из его высоты в библиотеке");
        assertEquals(1100, r.framePitchXMm(), 1e-9);
        assertEquals(2, r.cabinetsPerFrameX(), "1100/500 = 2.2 -> 2 кабинета");
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("не кратен кабинету")), r.warnings().toString());
    }

    @Test
    void cabinetsPerFrameAreDerivedFromSizesNotHardcoded(@TempDir Path dir) {
        freshModel(dir);
        // Кабинет 500×250: рама 500 мм по глубине несёт 2 ряда. 3 ряда -> 1 полоса рам, третий
        // ряд (неполная полоса) целиком без опоры.
        CabinetType t = cabinet(500, 250, 0);
        Screen s = floorScreen(t, 3, 4);
        s.setStructureFrameTypeId(frame(950, 500, 0).getId());

        FloorCalc.Result r = FloorCalc.compute(s, t, model.getWorkspace());

        assertEquals(2, r.cabinetsPerFrameX());
        assertEquals(2, r.cabinetsPerFrameY());
        assertEquals(List.of(new FloorCalc.FramePlacement(0, 0, 2, 2), new FloorCalc.FramePlacement(0, 2, 2, 2)),
                r.frames());
        assertEquals(4, r.unsupportedCabinetCount());
        assertEquals(8 * 4, r.toothCount());
    }

    @Test
    void teethSettingIsClampedTo2Through4(@TempDir Path dir) {
        Screen s = new Screen();
        assertEquals(4, s.getFloorTeethPerCabinet(), "по умолчанию 4");
        s.setFloorTeethPerCabinet(7);
        assertEquals(4, s.getFloorTeethPerCabinet());
        s.setFloorTeethPerCabinet(0);
        assertEquals(2, s.getFloorTeethPerCabinet());
        s.setFloorTeethPerCabinet(3);
        assertEquals(3, s.copy().getFloorTeethPerCabinet(), "copy() переносит настройку (undo-снимок)");
    }

    @Test
    void oldScreenJsonWithoutFloorFieldOpensWithFourTeethAndRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen old = mapper.readValue("{\"id\":\"s1\",\"name\":\"A\",\"mountType\":\"FLOOR\"}", Screen.class);
        assertEquals(4, old.getFloorTeethPerCabinet());

        old.setFloorTeethPerCabinet(3);
        Screen back = mapper.readValue(mapper.writeValueAsString(old), Screen.class);
        assertEquals(3, back.getFloorTeethPerCabinet());

        Screen garbage = mapper.readValue("{\"id\":\"s2\",\"floorTeethPerCabinet\":9}", Screen.class);
        assertEquals(4, garbage.getFloorTeethPerCabinet(), "мусорное значение зажимается в 2..4");
    }

    @Test
    void updateScreenFloorPersistsAndUndoesInOneStep(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(500, 500, 0);
        Screen s = floorScreen(t, 2, 2);
        StructureFrameType f = frame(950, 500, 0);
        StructureFrameType c = cup(50.0);

        model.updateScreenFloor(s, f.getId(), c.getId(), 2);
        assertEquals(f.getId(), s.getStructureFrameTypeId());
        assertEquals(c.getId(), s.getStructureCupTypeId());
        assertEquals(2, s.getFloorTeethPerCabinet());

        model.undo();
        Screen after = model.getCurrentScene().getScreens().get(0);
        assertNotNull(after);
        assertEquals(4, after.getFloorTeethPerCabinet(), "отмена возвращает прежнее число зубов");
        assertNull(after.getStructureFrameTypeId());
        assertNull(after.getStructureCupTypeId());
    }
}
