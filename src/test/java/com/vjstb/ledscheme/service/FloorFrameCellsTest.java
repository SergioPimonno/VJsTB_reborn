package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.FloorFrameCell;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Рамы напольного каркаса как ПЕРСИСТЕНТНЫЙ список ({@link Screen#getFloorFrameCells()}) —
 * запрос пользователя 2026-10-01: «для пола давай сделаем переключаемый режим отображения —
 * либо 2D схема как сейчас, либо 3D редактор как для конструктива». Редактор прячет раму
 * кликом и возвращает/добавляет Ctrl+кликом; после правки всё железо (стыки, стаканы, болты,
 * ножки, зубы, неопёртые кабинеты, нагрузка) должно пересчитаться — это и проверяется здесь,
 * плюс merge-not-overwrite при «Рассчитать пол» (тот же класс бага, что Phase 2 конструктива:
 * пересчёт молча возвращал убранное), совместимость старых проектов и Ctrl+Z.
 */
class FloorFrameCellsTest {

    private AppModel model;

    private void freshModel(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
    }

    private CabinetType cabinet(double weightKg) {
        CabinetType ct = new CabinetType();
        ct.setName("Пол 500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weightKg);
        return model.addCabinetType(ct);
    }

    private StructureFrameType frame(double weightKg) {
        StructureFrameType f = new StructureFrameType();
        f.setName("Рама 950");
        f.setKind(StructureFrameType.Kind.FRAME);
        f.setHeightMm(950.0);
        f.setWidthMm(500.0);
        f.setDepthMm(51.0);
        f.setWeightKg(weightKg);
        return model.addStructureFrameType(f);
    }

    private StructureFrameType frameType;

    private Screen floorScreen(CabinetType t, int rows, int cols) {
        Screen s = model.addScreen("Пол", t.getId(), rows, cols, 0, 0, ScreenMountType.FLOOR);
        model.selectScreen(s);
        if (frameType == null) {
            frameType = frame(10);
        }
        s.setStructureFrameTypeId(frameType.getId());
        return s;
    }

    private FloorCalc.Result calc(Screen s) {
        return FloorCalc.compute(s, model.typeOf(s), model.getWorkspace());
    }

    private static FloorFrameCell at(Screen s, int row, int col) {
        return s.getFloorFrameCells().stream().filter(c -> c.matches(row, col)).findFirst().orElse(null);
    }

    @Test
    void hidingFrameMakesItsCabinetsUnsupportedAndRecountsAllHardware(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(10);
        Screen s = floorScreen(t, 6, 14);
        FloorCalc.Result before = calc(s);
        assertEquals(42, before.frameCount());

        assertTrue(model.toggleFloorFrameCell(s, 0, 2), "клик по существующей раме прячет её");
        FloorFrameCell rec = at(s, 0, 2);
        assertTrue(rec != null && rec.isHidden() && rec.isManual(), "запись не удаляется, а прячется");
        assertEquals(42, s.getFloorFrameCells().size(), "список материализован целиком");

        FloorCalc.Result r = calc(s);
        assertEquals(41, r.frameCount());
        assertEquals(List.of(new FloorCalc.CabinetCell(0, 2), new FloorCalc.CabinetCell(0, 3)),
                r.unsupportedCabinets(), "кабинеты убранной рамы — без опоры (оранжевые)");
        assertEquals(34, r.shortSideJointCount(), "в ряду 0 пропали оба стыка убранной рамы");
        assertEquals(68, r.cupCount());
        assertEquals(34, r.longSideJointCount(), "пропал стык с рамой ряда 1 под ней");
        assertEquals(2 * (34 + 34), r.boltCount());
        assertEquals(164, r.legCount(), "4 ножки на раму × 41");
        assertEquals(82 * 4, r.toothCount(), "зубы только на опёртые кабинеты");
        assertEquals((84 * 10.0 + 41 * 10.0) / 21.0, r.averageLoadKgPerM2(), 1e-9, "нагрузка — с 41 рамой");

        assertTrue(model.toggleFloorFrameCell(s, 0, 2), "Ctrl+клик по призраку возвращает раму");
        FloorCalc.Result back = calc(s);
        assertEquals(42, back.frameCount());
        assertEquals(0, back.unsupportedCabinetCount());
        assertEquals(142, back.boltCount());
        assertFalse(at(s, 0, 2).isManual(), "возвращённая на место автоматики рама — снова не ручная");
    }

    @Test
    void ctrlClickAddsFrameAtShiftedPositionAndDropsOverlappedHiddenRecord(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 1, 13);
        assertEquals(List.of(new FloorCalc.CabinetCell(0, 12)), calc(s).unsupportedCabinets());

        model.toggleFloorFrameCell(s, 0, 10);
        assertTrue(model.toggleFloorFrameCell(s, 0, 11), "сдвиг рамы на кабинет вправо — допустимая позиция");

        FloorCalc.Result r = calc(s);
        assertEquals(6, r.frameCount());
        assertEquals(List.of(new FloorCalc.CabinetCell(0, 10)), r.unsupportedCabinets());
        assertEquals(4, r.shortSideJointCount(), "8→11 не встык (зазор в кабинет) — стыка нет");
        assertTrue(at(s, 0, 11).isManual(), "рама вне автоматики — ручная");
        assertEquals(null, at(s, 0, 10), "спрятанная запись, перекрытая новой рамой, убрана");
    }

    @Test
    void invalidPositionsAreRefusedWithoutUndoEntry(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 2, 7);
        s.cabinetAt(0, 2).setHidden(true);
        int depth = model.undoDepth();

        assertFalse(model.toggleFloorFrameCell(s, 0, 4), "перекрывает видимые рамы [3,5) и [5,7)");
        assertFalse(model.toggleFloorFrameCell(s, 0, 2), "через скрытый кабинет рама не кладётся");
        assertFalse(model.toggleFloorFrameCell(s, 1, 6), "рама вылезла бы за край экрана");
        assertEquals(depth, model.undoDepth(), "отказ не пишет запись отмены");
        assertTrue(s.getFloorFrameCells().isEmpty(), "и не материализует список");
    }

    @Test
    void editingIsOnlyAllowedForCurrentScreen(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen a = floorScreen(t, 1, 4);
        Screen b = floorScreen(t, 1, 4);
        assertFalse(model.toggleFloorFrameCell(a, 0, 0), "снимок отмены берётся с текущего экрана (b)");
        assertTrue(model.toggleFloorFrameCell(b, 0, 0));
    }

    @Test
    void recalculationMergesManualEditsAndRegeneratesUntouchedBands(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 2, 7);
        model.toggleFloorFrameCell(s, 0, 2); // ряд 0: убрали среднюю раму

        // Правка формы экрана в НЕтронутом ряду 1: автоматика там должна сдвинуться.
        s.cabinetAt(1, 0).setHidden(true);
        model.updateScreenFloor(s, s.getStructureFrameTypeId(), null, 4);

        assertTrue(at(s, 0, 2).isHidden(), "ручное скрытие пережило «Рассчитать пол»");
        assertEquals(null, at(s, 1, 0), "запись автоматики вне новой сетки отброшена");
        assertTrue(at(s, 1, 1) != null && !at(s, 1, 1).isHidden(), "ряд 1 перерасставлен с колонки 1");
        assertTrue(at(s, 1, 5) != null, "1,3,5");
        FloorCalc.Result r = calc(s);
        assertEquals(List.of(new FloorCalc.FramePlacement(0, 0, 1, 2), new FloorCalc.FramePlacement(0, 4, 1, 2),
                new FloorCalc.FramePlacement(1, 1, 1, 2), new FloorCalc.FramePlacement(1, 3, 1, 2),
                new FloorCalc.FramePlacement(1, 5, 1, 2)), r.frames());
    }

    @Test
    void mergeKeepsValidManualRecordsDropsOutOfGridOnesAndFillsTheRest(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 2, 6);
        FloorCalc.Layout layout = FloorCalc.layout(s, t, model.getWorkspace());

        List<FloorFrameCell> stored = new ArrayList<>();
        stored.add(new FloorFrameCell(0, 1, false, true));  // ручная, сдвинутая — остаётся
        stored.add(new FloorFrameCell(1, 2, true, true));   // спрятанная на месте автоматики — остаётся
        stored.add(new FloorFrameCell(5, 0, true, true));   // вне сетки — отбрасывается
        stored.add(new FloorFrameCell(0, 4, false, false)); // неручная — генерируется заново
        stored.add(new FloorFrameCell(0, 2, false, true));  // перекрывает (0,1) — отбрасывается

        List<FloorFrameCell> merged = FloorCalc.mergeCells(stored, layout);

        assertEquals(List.of("0,1,v,m", "0,4,v,a", "1,0,v,a", "1,2,h,m", "1,4,v,a"),
                merged.stream().map(c -> c.getRow() + "," + c.getCol() + "," + (c.isHidden() ? "h" : "v") + ","
                        + (c.isManual() ? "m" : "a")).toList(),
                "ряд 0: автопозиции (0,0) и (0,2) перекрыты ручной (0,1) — пропущены, (0,4) свободна;"
                        + " ряд 1: спрятанная (1,2) осталась спрятанной, вокруг — автоматика");
        assertEquals(merged.toString(), FloorCalc.mergeCells(merged, layout).toString(), "merge идемпотентен");
    }

    @Test
    void oldProjectWithoutCellsComputesAsBeforeAndGetsListOnFirstCalculation(@TempDir Path dir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen old = mapper.readValue("{\"id\":\"s1\",\"name\":\"A\",\"mountType\":\"FLOOR\"}", Screen.class);
        assertTrue(old.getFloorFrameCells().isEmpty(), "старый JSON без поля — пустой список, не null");

        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 6, 14);
        assertTrue(s.getFloorFrameCells().isEmpty());
        assertEquals(42, calc(s).frameCount(), "без списка — чистая автоматика, как до редактора");

        model.updateScreenFloor(s, s.getStructureFrameTypeId(), null, 4);
        assertEquals(42, s.getFloorFrameCells().size(), "«Рассчитать пол» сохраняет список");
        assertTrue(s.getFloorFrameCells().stream().noneMatch(c -> c.isHidden() || c.isManual()));

        model.toggleFloorFrameCell(s, 2, 4);
        Screen back = mapper.readValue(mapper.writeValueAsString(s), Screen.class);
        assertEquals(42, back.getFloorFrameCells().size());
        FloorFrameCell rec = back.getFloorFrameCells().stream().filter(c -> c.matches(2, 4)).findFirst().orElseThrow();
        assertTrue(rec.isHidden() && rec.isManual(), "hidden/manual переживают JSON");
        assertEquals(41, FloorCalc.compute(back, t, model.getWorkspace()).frameCount());
        assertEquals(42, s.copy().getFloorFrameCells().size(), "copy() — снимок отмены — переносит список");
    }

    @Test
    void undoRevertsFrameEditsStepByStep(@TempDir Path dir) {
        freshModel(dir);
        CabinetType t = cabinet(0);
        Screen s = floorScreen(t, 6, 14);
        model.toggleFloorFrameCell(s, 0, 0);
        model.toggleFloorFrameCell(s, 1, 0);
        assertEquals(40, calc(s).frameCount());

        model.undo();
        Screen live = model.getCurrentScreen();
        assertEquals(41, calc(live).frameCount(), "Ctrl+Z возвращает последнюю убранную раму");
        assertTrue(at(live, 0, 0).isHidden());

        model.undo();
        assertEquals(42, calc(live).frameCount());
        assertTrue(live.getFloorFrameCells().isEmpty(), "до первой правки списка не было — откат к пустому");

        // Снимок не делит записи с живым экраном: правка после отмены не портит стек.
        model.toggleFloorFrameCell(live, 0, 0);
        model.toggleFloorFrameCell(live, 0, 0);
        model.undo(2);
        assertTrue(live.getFloorFrameCells().isEmpty());
    }
}
