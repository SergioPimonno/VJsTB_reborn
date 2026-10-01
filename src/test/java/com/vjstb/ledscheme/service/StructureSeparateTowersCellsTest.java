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
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Баг-репорт пользователя 2026-10-01 (скриншот вогнутого экрана, проект «Волшебник», сцена
 * «Вход (9)»): после «Предварительного расчёта» раздельные башни собрались БЕЗ перемычек и
 * оснований — бежевые перемычки и основание были только у крайней башни, остальные пользователь
 * возвращал руками Ctrl+кликом.
 *
 * <p><b>Что подтвердилось при разборе</b>: регенерация с нуля перемычки строит правильно (все
 * башни, оба ряда, видимые), рендер их рисует (проверено офскрин-рендером данных пользователя).
 * Корень — переход «стена → раздельные башни» на экране, который до этого был стеной и
 * редактировался кликами: merge-not-overwrite ({@link ScreenLogic#regenerateStructureCells})
 * сохранял записи чётных промежутков стены вместе с их {@code hidden}, хотя индексы ячеек у стены
 * и у раздельных башен означают РАЗНЫЕ детали. Убранное в стене «переезжало» в башни 0…6, чистыми
 * оставались только башни на месте промежутков, которых в стене не было. Исправление — при смене
 * режима сетка строится заново ({@link ScreenLogic#clearStructureCells} из {@code
 * AppModel#updateScreenStructure}); внутри режима ручные правки сохраняются по-прежнему.
 *
 * <p>Подозрение «в списке перемычек ЗАДВОЕНЫ ключи» не подтвердилось: пары записей с одним
 * {@code towerIndex}/{@code levelIndex} в проекте пользователя отличаются РЯДОМ (передний/задний,
 * {@code row}) — это разные перемычки; {@code toggle*} дублей не создаёт. Список всё же приходит и
 * из JSON, поэтому добавлена нормализация дублей ({@link ScreenLogic#dedupeStructureCells}:
 * видимая запись побеждает скрытую) — при загрузке, перед регенерацией и перед кликом в 3D.
 */
class StructureSeparateTowersCellsTest {

    /** Минимальная фикстура: поля конструктива экрана «Стена» пользователя (4×28 кабинетов
     *  Dicolor 500×500×65, вогнутый R = 10 м, зазор 500, 9 башен = 18 столбов) — скопированы из
     *  его workspace.json как есть, ПОСЛЕ ручных правок (перемычки переднего ряда возвращены,
     *  задние скрыты — задний ряд у него из одной рамы 950 мм, перемычке на 1425 мм там не к чему
     *  крепиться; верхние рамы заднего ряда скрыты на всех столбах). */
    static final String USER_SCREEN_JSON = """
            {
              "mountType": "STRUCTURE",
              "rows": 4,
              "cols": 28,
              "structureCurveType": "CONCAVE",
              "structureCurveRadiusMm": 10000.0,
              "structureTowerGapMm": 500.0,
              "structureSeparateTowerCount": 0,
              "structureTowerCount": 18,
              "structureTowerHeightMm": 2000.0,
              "structureVerticalFramesPerTower": 2,
              "structureBackRowSegments": 2,
              "structurePeremychkaLevels": 1,
              "structureExtendedBaseSections": 1,
              "structureBaseExtensionMm": 500.0,
              "structureFrameCells": [
                {"towerIndex":0,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":0,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":0,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":0,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":1,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":1,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":1,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":1,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":2,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":2,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":2,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":2,"row":1,"segmentIndex":1,"hidden":true},
                {"towerIndex":3,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":3,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":3,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":3,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":4,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":4,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":4,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":4,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":5,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":5,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":5,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":5,"row":1,"segmentIndex":1,"hidden":true},
                {"towerIndex":6,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":6,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":6,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":6,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":7,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":7,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":7,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":7,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":8,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":8,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":8,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":8,"row":1,"segmentIndex":1,"hidden":true},
                {"towerIndex":9,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":9,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":9,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":9,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":10,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":10,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":10,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":10,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":11,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":11,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":11,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":11,"row":1,"segmentIndex":1,"hidden":true},
                {"towerIndex":12,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":12,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":12,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":12,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":13,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":13,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":13,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":13,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":14,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":14,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":14,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":14,"row":1,"segmentIndex":1,"hidden":true},
                {"towerIndex":15,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":15,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":15,"row":1,"segmentIndex":0,"hidden":false},
                {"towerIndex":15,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":16,"row":0,"segmentIndex":0,"hidden":false}, {"towerIndex":16,"row":0,"segmentIndex":1,"hidden":false},
                {"towerIndex":16,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":16,"row":1,"segmentIndex":1,"hidden":true}, {"towerIndex":17,"row":0,"segmentIndex":0,"hidden":false},
                {"towerIndex":17,"row":0,"segmentIndex":1,"hidden":false}, {"towerIndex":17,"row":1,"segmentIndex":0,"hidden":false}, {"towerIndex":17,"row":1,"segmentIndex":1,"hidden":true}
              ],
              "structurePeremychkaCells": [
                {"towerIndex":0,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":0,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":2,"row":0,"levelIndex":0,"hidden":false},
                {"towerIndex":2,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":4,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":4,"row":1,"levelIndex":0,"hidden":true},
                {"towerIndex":6,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":6,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":8,"row":0,"levelIndex":0,"hidden":false},
                {"towerIndex":8,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":10,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":10,"row":1,"levelIndex":0,"hidden":true},
                {"towerIndex":12,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":12,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":14,"row":0,"levelIndex":0,"hidden":false},
                {"towerIndex":14,"row":1,"levelIndex":0,"hidden":true}, {"towerIndex":16,"row":0,"levelIndex":0,"hidden":false}, {"towerIndex":16,"row":1,"levelIndex":0,"hidden":true}
              ],
              "structureBaseFrameCells": [
                {"towerIndex":0,"sectionIndex":0,"hidden":false}, {"towerIndex":2,"sectionIndex":0,"hidden":false}, {"towerIndex":4,"sectionIndex":0,"hidden":false},
                {"towerIndex":6,"sectionIndex":0,"hidden":false}, {"towerIndex":8,"sectionIndex":0,"hidden":false}, {"towerIndex":10,"sectionIndex":0,"hidden":false},
                {"towerIndex":12,"sectionIndex":0,"hidden":false}, {"towerIndex":12,"sectionIndex":1,"hidden":false}, {"towerIndex":10,"sectionIndex":1,"hidden":false},
                {"towerIndex":8,"sectionIndex":1,"hidden":false}, {"towerIndex":6,"sectionIndex":1,"hidden":false}, {"towerIndex":4,"sectionIndex":1,"hidden":false},
                {"towerIndex":2,"sectionIndex":1,"hidden":false}, {"towerIndex":0,"sectionIndex":1,"hidden":false}, {"towerIndex":14,"sectionIndex":0,"hidden":false},
                {"towerIndex":14,"sectionIndex":1,"hidden":false}, {"towerIndex":16,"sectionIndex":0,"hidden":false}, {"towerIndex":16,"sectionIndex":1,"hidden":false}
              ]
            }
            """;

    private AppModel model;
    private CabinetType type;
    private StructureFrameType frame;

    private Screen screen(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        type = addTypes(model);
        frame = model.getStructureFrameTypes().stream().filter(t -> "Рама стандартная".equals(t.getName()))
                .findFirst().orElseThrow();
        model.selectProject(model.addProject("Волшебник"));
        model.selectScene(model.addScene("Вход (9)"));
        Screen s = model.addScreen("Стена", type.getId(), 4, 28, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    /** Типы из библиотеки пользователя: кабинет Dicolor и «Рама стандартная» 950×500×51. */
    static CabinetType addTypes(AppModel model) {
        CabinetType ct = new CabinetType();
        ct.setName("Dicolor");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(192);
        ct.setResolutionHeight(192);
        ct.setWeightKg(7.2);
        CabinetType added = model.addCabinetType(ct);
        StructureFrameType ft = new StructureFrameType();
        ft.setName("Рама стандартная");
        ft.setKind(StructureFrameType.Kind.FRAME);
        ft.setHeightMm(950.0);
        ft.setWidthMm(500.0);
        ft.setDepthMm(51.0);
        ft.setWeightKg(4.0);
        model.addStructureFrameType(ft);
        return added;
    }

    /** Тот же порядок и те же стартовые числа, что «Предварительный расчёт» даёт для экрана
     *  пользователя (SetupStagePanel#calculateStructure): высота 2000 → 2 сегмента рамы 950,
     *  задний ряд 2, 1 уровень перемычек, вынос 500, число башен авто. */
    private void calc(Screen s, ScreenCurveType curve, double radius, double gap) {
        model.updateScreenStructure(s, 2000, StructureCalc.suggestTowerCount(s, type), 2, 2, 1, 500, 0.6,
                frame.getId(), null, null, 0, "", curve, radius, false, gap, 0);
    }

    private static Set<String> visiblePeremychki(Screen s) {
        return s.getStructurePeremychkaCells().stream().filter(c -> !c.isHidden())
                .map(c -> c.getTowerIndex() + "/" + c.getRow()).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> visibleBases(Screen s) {
        return s.getStructureBaseFrameCells().stream().filter(c -> !c.isHidden())
                .map(c -> c.getTowerIndex() + "/" + c.getSectionIndex()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Ожидаемые ключи «каждая из n башен, оба ряда / обе секции». */
    static Set<String> everyTower(int towers) {
        Set<String> keys = new TreeSet<>();
        for (int k = 0; k < towers; k++) {
            keys.add(2 * k + "/0");
            keys.add(2 * k + "/1");
        }
        return keys;
    }

    /** Правки стены, как в проекте пользователя: верхние рамы заднего ряда убраны на каждом
     *  столбе, перемычки и основание убраны кликами во всех промежутках. */
    static void hideWallEditsLikeTheUser(AppModel model, Screen s) {
        for (StructureFrameCell c : List.copyOf(s.getStructureFrameCells())) {
            if (c.getRow() == 1 && c.getSegmentIndex() == 1) {
                model.toggleStructureFrameCell(s, c.getTowerIndex(), 1, 1);
            }
        }
        for (StructurePeremychkaCell c : List.copyOf(s.getStructurePeremychkaCells())) {
            model.toggleStructurePeremychkaCell(s, c.getTowerIndex(), c.getRow(), c.getLevelIndex());
        }
        for (StructureBaseFrameCell c : List.copyOf(s.getStructureBaseFrameCells())) {
            model.toggleStructureBaseFrameSection(s, c.getTowerIndex(), c.getSectionIndex());
        }
    }

    /** РЕГРЕССИЯ бага (до правки падал: у башен 0…6 перемычки и основание оставались скрытыми
     *  «по наследству» от стены, видимы были только у башен 7 и 8 — как на скриншоте). */
    @Test
    void switchingAnEditedWallToConcaveBuildsEveryTowerWithPeremychkiAndBase(@TempDir Path dir) {
        Screen s = screen(dir);
        calc(s, ScreenCurveType.FLAT, 10_000, 0);
        assertEquals(15, s.getStructureTowerCount(), "стена 14 м — 15 столбов");
        hideWallEditsLikeTheUser(model, s);
        assertTrue(visiblePeremychki(s).isEmpty());

        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        assertEquals(18, s.getStructureTowerCount(), "9 башен × 2 столба (авто)");
        assertEquals(everyTower(9), visiblePeremychki(s), "перемычки у КАЖДОЙ башни, оба ряда");
        assertEquals(everyTower(9), visibleBases(s), "основание у КАЖДОЙ башни, обе секции");
        assertTrue(s.getStructureFrameCells().stream().noneMatch(StructureFrameCell::isHidden),
                "скрытые в стене рамы к новым башням не относятся");
        StructureCalc.Result r = StructureCalc.compute(s, type, model.getWorkspace());
        assertEquals(18, r.peremychkaCount());
        assertEquals(18, r.baseFrameCount());
    }

    @Test
    void switchingSeparateTowersBackToWallAlsoStartsFromAFullGrid(@TempDir Path dir) {
        Screen s = screen(dir);
        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        hideWallEditsLikeTheUser(model, s);
        calc(s, ScreenCurveType.FLAT, 10_000, 0);
        assertEquals(15, s.getStructureTowerCount());
        assertEquals(28, visiblePeremychki(s).size(), "14 промежутков стены × 2 ряда, все видимые");
        assertTrue(s.getStructureBaseFrameCells().stream().noneMatch(StructureBaseFrameCell::isHidden));
    }

    /** Внутри одного режима merge-not-overwrite не тронут: ручные правки раздельных башен
     *  переживают изменение радиуса и зазора (тот же принцип, что Phase 2 для стены). */
    @Test
    void manualEditsOfSeparateTowersSurviveRadiusAndGapChanges(@TempDir Path dir) {
        Screen s = screen(dir);
        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        model.toggleStructurePeremychkaCell(s, 4, 1, 0);
        model.toggleStructureFrameCell(s, 3, 1, 1);
        calc(s, ScreenCurveType.CONCAVE, 12_000, 1000);
        assertFalse(visiblePeremychki(s).contains("4/1"), "убранная перемычка не вернулась");
        assertTrue(s.getStructureFrameCells().stream().anyMatch(c -> c.matches(3, 1, 1) && c.isHidden()));
        calc(s, ScreenCurveType.FLAT, 10_000, 500); // прямой с зазором — тоже раздельные башни
        assertFalse(visiblePeremychki(s).contains("4/1"));
    }

    /** Сброс при смене режима — в той же записи отмены, что и пересчёт: Ctrl+Z возвращает стену
     *  вместе с её ручными правками. */
    @Test
    void undoAfterModeSwitchRestoresTheEditedWall(@TempDir Path dir) {
        Screen s = screen(dir);
        calc(s, ScreenCurveType.FLAT, 10_000, 0);
        model.toggleStructurePeremychkaCell(s, 3, 0, 0);
        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        model.undo();
        assertEquals(ScreenCurveType.FLAT, s.getStructureCurveType());
        assertEquals(15, s.getStructureTowerCount());
        assertTrue(s.getStructurePeremychkaCells().stream().anyMatch(c -> c.matches(3, 0, 0) && c.isHidden()),
                "правка стены вернулась вместе со стеной");
    }

    /** Гипотеза «Ctrl+клик по призраку создаёт НОВУЮ запись рядом со скрытой» проверена и не
     *  подтвердилась: переключение снимает hidden с существующей записи, список не растёт. */
    @Test
    void ctrlClickOnHiddenPeremychkaUnhidesTheSameRecordInsteadOfAddingANewOne(@TempDir Path dir) {
        Screen s = screen(dir);
        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        int before = s.getStructurePeremychkaCells().size();
        model.toggleStructurePeremychkaCell(s, 6, 0, 0);
        model.toggleStructurePeremychkaCell(s, 6, 0, 0);
        assertEquals(before, s.getStructurePeremychkaCells().size());
        assertEquals(1, s.getStructurePeremychkaCells().stream().filter(c -> c.matches(6, 0, 0)).count());
        assertTrue(visiblePeremychki(s).contains("6/0"));
    }

    /** Данные пользователя как есть: дублей ключей нет (пары записей — передний и задний ряд),
     *  перемычка переднего ряда и основание есть у всех 9 башен; повторный «Предварительный
     *  расчёт» с теми же параметрами режим не меняет и ручные правки сохраняет. */
    @Test
    void userScreenFixtureHasNoDuplicateKeysAndRecalculationKeepsManualEdits(@TempDir Path dir) throws Exception {
        Screen s = screen(dir);
        applyFixture(s, USER_SCREEN_JSON);
        assertEquals(0, ScreenLogic.dedupeStructureCells(s), "задвоенных ключей в проекте нет");
        Set<String> front = new TreeSet<>();
        for (int k = 0; k < 9; k++) {
            front.add(2 * k + "/0");
        }
        assertEquals(front, visiblePeremychki(s));
        assertEquals(everyTower(9), visibleBases(s));

        calc(s, ScreenCurveType.CONCAVE, 10_000, 500);
        assertEquals(18, s.getStructureTowerCount());
        assertEquals(front, visiblePeremychki(s), "скрытые задние перемычки не вернулись");
        assertEquals(18, s.getStructureFrameCells().stream().filter(StructureFrameCell::isHidden).count());
    }

    /** Нормализация дублей: среди записей с одним ключом остаётся ВИДИМАЯ (деталь вернули
     *  руками — рендер и спецификация и так считали её стоящей); без видимой — первая. */
    @Test
    void dedupeKeepsTheVisibleRecordOfEachDuplicatedKey(@TempDir Path dir) throws Exception {
        Screen s = screen(dir);
        applyFixture(s, USER_SCREEN_JSON);
        // дубли «как их описывал первый разбор»: скрытая + видимая запись с одним полным ключом
        StructurePeremychkaCell hiddenTwin = new StructurePeremychkaCell(2, 0, 0);
        hiddenTwin.setHidden(true);
        s.getStructurePeremychkaCells().add(0, hiddenTwin);
        s.getStructurePeremychkaCells().add(new StructurePeremychkaCell(2, 1, 0));
        StructureBaseFrameCell baseTwin = new StructureBaseFrameCell(6, 0);
        baseTwin.setHidden(true);
        s.getStructureBaseFrameCells().add(baseTwin);
        StructureFrameCell frameTwin = new StructureFrameCell(5, 1, 1);
        frameTwin.setFrameTypeId("override");
        s.getStructureFrameCells().add(frameTwin);

        assertEquals(4, ScreenLogic.dedupeStructureCells(s));
        assertEquals(18, s.getStructurePeremychkaCells().size());
        assertEquals(18, s.getStructureBaseFrameCells().size());
        assertEquals(72, s.getStructureFrameCells().size());
        assertTrue(visiblePeremychki(s).contains("2/0"), "из пары скрытая+видимая осталась видимая");
        assertTrue(visiblePeremychki(s).contains("2/1"), "видимый дубль победил скрытую запись");
        assertTrue(visibleBases(s).contains("6/0"), "видимая секция не скрыта поздним скрытым дублем");
        StructureFrameCell kept = s.getStructureFrameCells().stream().filter(c -> c.matches(5, 1, 1))
                .findFirst().orElseThrow();
        assertFalse(kept.isHidden());
        assertEquals("override", kept.getFrameTypeId(), "осталась именно видимая запись со своим типом");
        assertEquals(0, ScreenLogic.dedupeStructureCells(s), "идемпотентно");

        // клик в 3D после нормализации переключает единственную запись
        model.toggleStructurePeremychkaCell(s, 2, 1, 0);
        assertFalse(visiblePeremychki(s).contains("2/1"));
    }

    /** Дубли из сохранённого файла сводятся уже при загрузке (без пересчёта и без кликов). */
    @Test
    void duplicatedCellsInSavedWorkspaceAreNormalizedOnLoad(@TempDir Path dir) throws Exception {
        String screenJson = USER_SCREEN_JSON.replace("\"structurePeremychkaCells\": [",
                "\"structurePeremychkaCells\": [{\"towerIndex\":0,\"row\":0,\"levelIndex\":0,\"hidden\":true},")
                .replaceFirst("\\{", "{\"id\":\"e\",\"name\":\"Стена\",");
        String ws = "{\"projects\":[{\"id\":\"p\",\"name\":\"Волшебник\",\"scenes\":[{\"id\":\"sc\","
                + "\"name\":\"Вход (9)\",\"screens\":[" + screenJson + "]}]}]}";
        File file = new File(dir.toFile(), "workspace.json");
        Files.writeString(file.toPath(), ws, StandardCharsets.UTF_8);
        AppModel loaded = new AppModel(new WorkspaceStore(file));
        Screen s = loaded.getWorkspace().getProjects().get(0).getScenes().get(0).getScreens().get(0);
        assertEquals(18, s.getStructurePeremychkaCells().size());
        assertTrue(s.getStructurePeremychkaCells().stream().anyMatch(c -> c.matches(0, 0, 0) && !c.isHidden()),
                "видимая перемычка башни 0 осталась видимой");
    }

    /** Переносит поля конструктива и ячейки фикстуры на экран модели (кабинеты — из модели). */
    static void applyFixture(Screen s, String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen f = mapper.readValue(json, Screen.class);
        s.setStructureCurveType(f.getStructureCurveType());
        s.setStructureCurveRadiusMm(f.getStructureCurveRadiusMm());
        s.setStructureTowerGapMm(f.getStructureTowerGapMm());
        s.setStructureSeparateTowerCount(f.getStructureSeparateTowerCount());
        s.setStructureTowerCount(f.getStructureTowerCount());
        s.setStructureTowerHeightMm(f.getStructureTowerHeightMm());
        s.setStructureVerticalFramesPerTower(f.getStructureVerticalFramesPerTower());
        s.setStructureBackRowSegments(f.getStructureBackRowSegments());
        s.setStructurePeremychkaLevels(f.getStructurePeremychkaLevels());
        s.setStructureExtendedBaseSections(f.getStructureExtendedBaseSections());
        s.setStructureBaseExtensionMm(f.getStructureBaseExtensionMm());
        s.setStructureFrameCells(f.getStructureFrameCells());
        s.setStructurePeremychkaCells(f.getStructurePeremychkaCells());
        s.setStructureBaseFrameCells(f.getStructureBaseFrameCells());
    }
}
