package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты {@link NovaLctCombineHelper} — {@code validateSlots()}/{@code combine()} на
 *  {@link NovaLctCombineHelper.ScreenSlot} (эфемерная сеточная раскладка из
 *  {@code LctPresetMasterDialog}, заменившая привязку к {@code ContentCanvas} — тот
 *  оказался не тем инструментом для этой задачи, см. class-javadoc
 *  {@link NovaLctCombineHelper}), и вырожденный случай (контроллер трогает ровно
 *  ОДИН экран), доказывающий, что контроллер-центричный путь
 *  resolver→combine→writeStandardCombined — надмножество уже подтверждённого
 *  одноэкранного {@link NovaLctScrWriter#write}, а не отдельная, независимо не
 *  проверенная логика. */
class NovaLctCombineHelperTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128(String name) {
        CabinetType ct = new CabinetType();
        ct.setName(name);
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    private ControllerType singleCardController() {
        ControllerType ct = new ControllerType();
        ct.setName("Test controller");
        ct.setPortCount(20);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20))));
        return ct;
    }

    private ControllerType twoCardController() {
        ControllerType ct = new ControllerType();
        ct.setName("Test 2-card controller");
        ct.setPortCount(8);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        ct.getCards().add(new SchemaCard("Карта 2", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        return ct;
    }

    @Test
    void degenerateSingleScreenMatchesDirectWrite(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 2, 0, 0); // 1 row, 2 cols
        model.selectScreen(screen);

        ControllerType ct = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(screen, ct.getId());

        List<String> ids = List.of(screen.cabinetAt(0, 0).getId(), screen.cabinetAt(0, 1).getId());
        model.addSignalChain(1, false, ids);

        // Прямой путь -- уже подтверждён golden-тестом NovaLctScrWriterTest.
        byte[] direct = NovaLctScrWriter.write(screen, scene, model.getWorkspace());

        // Контроллер-центричный путь -- должен дать РОВНО то же самое для экрана,
        // размещённого ровно в (0,0) объединённой сетки того же размера.
        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(screen, 0, 0));

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        assertNull(NovaLctCombineHelper.validateSlots(slots, model));
        NovaLctCombineHelper.CombineResult combined =
                NovaLctCombineHelper.combine(slots, screen.getCols(), screen.getRows(), recs, model);
        byte[] viaCombine = NovaLctScrWriter.writeStandardCombined(combined);

        assertArrayEquals(direct, viaCombine,
                "Контроллер-центричный путь для ОДНОГО экрана обязан совпадать с прямым write(...)");
    }

    @Test
    void validateSlotsRejectsMismatchedCabinetSize(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType typeA = model.addCabinetType(type128("A")); // 128x128
        CabinetType typeB = new CabinetType();
        typeB.setName("B");
        typeB.setWidthMm(250);
        typeB.setHeightMm(250);
        typeB.setResolutionWidth(64);
        typeB.setResolutionHeight(64);
        model.addCabinetType(typeB);

        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screenA = model.addScreen("A", typeA.getId(), 1, 1, 0, 0);
        Screen screenB = model.addScreen("B", typeB.getId(), 1, 1, 500, 0);

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(screenA, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(screenB, 1, 0));

        String reason = NovaLctCombineHelper.validateSlots(slots, model);
        assertNotNull(reason, "Разный физический размер кабинета обязан блокировать объединение");
    }

    @Test
    void validateSlotsAcceptsScreensOfDifferentHeightSideBySide(@TempDir Path dir) {
        // Баг-репорт: Left/Right 4x8, Center 10x6 -- поставленные рядом, Center короче
        // соседей. РАНЬШЕ (неверно) считалось, что такая "дыра" должна блокироваться --
        // пользователь показал реальный файл NovaLCT (111.scr) именно с такой
        // конфигурацией: дыра -- штатный случай, помечается blank (card=0xFF) в combine(),
        // а не запрещается на этапе валидации.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen left = model.addScreen("Left", type.getId(), 8, 4, 0, 0);
        Screen center = model.addScreen("Center", type.getId(), 6, 10, 0, 0);
        Screen right = model.addScreen("Right", type.getId(), 8, 4, 0, 0);

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(left, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(center, 4, 0),
                new NovaLctCombineHelper.ScreenSlot(right, 14, 0));

        assertNull(NovaLctCombineHelper.validateSlots(slots, model),
                "Экраны разной высоты рядом -- штатный случай (дыра заполняется blank), не ошибка валидации");
    }

    @Test
    void combineFillsMismatchedHeightGapWithBlankSentinelAtExactCoordinates(@TempDir Path dir) {
        // Та же сцена -- проверяем, что combine() кладёт card=0xFF ТОЧНО в те же 20
        // координат (col 4..13, row 6..7, 0-based), что подтверждены побайтовым разбором
        // реального 111.scr, присланного пользователем.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen left = model.addScreen("Left", type.getId(), 8, 4, 0, 0);
        Screen center = model.addScreen("Center", type.getId(), 6, 10, 0, 0);
        Screen right = model.addScreen("Right", type.getId(), 8, 4, 0, 0);

        ControllerType ct = model.addControllerType(singleCardController());
        model.selectScreen(left);
        ControllerInstance controller = model.addControllerToScreen(left, ct.getId());

        // Расключаем ВСЕ кабинеты всех 3 экранов одной цепочкой каждый (не важно для
        // этого теста -- нас интересует только blank-заполнение дыры).
        int port = 1;
        for (Screen s : List.of(left, center, right)) {
            List<String> ids = new java.util.ArrayList<>();
            for (int c = 0; c < s.getCols(); c++) {
                for (int r = 0; r < s.getRows(); r++) {
                    ids.add(s.cabinetAt(r, c).getId());
                }
            }
            model.addSignalChain(port++, false, ids);
        }

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(left, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(center, 4, 0),
                new NovaLctCombineHelper.ScreenSlot(right, 14, 0));

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        NovaLctCombineHelper.CombineResult combined =
                NovaLctCombineHelper.combine(slots, 18, 8, recs, model);

        List<NovaLctScrWriter.CellKey> blanks = new java.util.ArrayList<>();
        for (var e : combined.cells().entrySet()) {
            if (e.getValue().card() == 255) {
                blanks.add(e.getKey());
            }
        }
        assertEquals(20, blanks.size(), "Ожидается ровно 20 blank-ячеек (10 столбцов x 2 строки), как в 111.scr");

        Set<Integer> cols = new TreeSet<>();
        Set<Integer> rows = new TreeSet<>();
        for (NovaLctScrWriter.CellKey k : blanks) {
            cols.add(k.col());
            rows.add(k.row());
        }
        assertEquals(Set.of(4, 5, 6, 7, 8, 9, 10, 11, 12, 13), cols, "Blank-столбцы должны точно совпасть с 111.scr");
        assertEquals(Set.of(6, 7), rows, "Blank-строки должны точно совпасть с 111.scr");
    }

    @Test
    void validateSlotsRejectsOverlappingScreens(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 2, 0, 0);

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(a, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(b, 1, 0)); // перекрывает A на 1 столбец

        String reason = NovaLctCombineHelper.validateSlots(slots, model);
        assertNotNull(reason, "Перекрывающиеся экраны обязаны блокировать объединение");
    }

    @Test
    void combinesTwoScreensSideBySide(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T")); // 128x128
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screenA = model.addScreen("A", type.getId(), 1, 1, 0, 0);
        Screen screenB = model.addScreen("B", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screenA);

        ControllerType ct = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(screenA, ct.getId());

        CabinetInstance cabA = screenA.cabinetAt(0, 0);
        CabinetInstance cabB = screenB.cabinetAt(0, 0);
        model.addSignalChain(1, false, List.of(cabA.getId(), cabB.getId()));

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(screenA, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(screenB, 1, 0)); // сразу справа от A

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        assertTrue(NovaLctCombineHelper.validateSlots(slots, model) == null);

        NovaLctCombineHelper.CombineResult combined = NovaLctCombineHelper.combine(slots, 2, 1, recs, model);
        assertTrue(combined.cols() == 2 && combined.rows() == 1,
                "Два экрана 1x1 бок о бок должны дать объединённую сетку 2x1, получено "
                        + combined.cols() + "x" + combined.rows());
    }

    @Test
    void splitSeparateGrouped_withoutGroups_matchesPlainSplitSeparate(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 2, 0, 0);
        model.selectScreen(a);

        ControllerType ct = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(a, ct.getId());

        for (Screen s : List.of(a, b)) {
            List<String> ids = new java.util.ArrayList<>();
            for (int c = 0; c < s.getCols(); c++) {
                for (int r = 0; r < s.getRows(); r++) {
                    ids.add(s.cabinetAt(r, c).getId());
                }
            }
            model.addSignalChain(s == a ? 1 : 2, false, ids);
        }

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(a, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(b, 4, 0));
        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);

        List<NovaLctScrWriter.ScreenBlock> plain = NovaLctCombineHelper.splitSeparate(slots, recs, model);
        List<NovaLctScrWriter.ScreenBlock> grouped =
                NovaLctCombineHelper.splitSeparateGrouped(slots, Map.of(), recs, model);
        List<NovaLctScrWriter.ScreenBlock> groupedNullMap =
                NovaLctCombineHelper.splitSeparateGrouped(slots, null, recs, model);

        assertEquals(plain, grouped, "Без группировки splitSeparateGrouped обязан совпадать с splitSeparate");
        assertEquals(plain, groupedNullMap, "null-карта групп -- то же самое, что пустая");
    }

    @Test
    void splitSeparateGrouped_mergesGroupedScreensIntoOneBlockWithBlankHoles(@TempDir Path dir) {
        // A1 (2x2) + A2 (2x2, короче на 1 ряд по факту той же высоты тут не проверяем --
        // просто разные по форме) сгруппированы -> ОДИН NovaLCT-экран; B (3x2) отдельно.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a1 = model.addScreen("A1", type.getId(), 2, 2, 0, 0); // 2 rows, 2 cols
        Screen a2 = model.addScreen("A2", type.getId(), 1, 2, 0, 0); // 1 row, 2 cols -- короче A1
        Screen b = model.addScreen("B", type.getId(), 2, 3, 0, 0);
        model.selectScreen(a1);

        ControllerType ct = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(a1, ct.getId());

        int port = 1;
        for (Screen s : List.of(a1, a2, b)) {
            List<String> ids = new java.util.ArrayList<>();
            for (int c = 0; c < s.getCols(); c++) {
                for (int r = 0; r < s.getRows(); r++) {
                    ids.add(s.cabinetAt(r, c).getId());
                }
            }
            model.addSignalChain(port++, false, ids);
        }

        // A1 в col 0-1, A2 сразу справа в col 2-3 (короче -- оставит дыру снизу),
        // B отдельно, с зазором, в col 6-8.
        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(a1, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(a2, 2, 0),
                new NovaLctCombineHelper.ScreenSlot(b, 6, 0));
        Map<String, Integer> groups = Map.of(a1.getId(), 1, a2.getId(), 1);

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        List<NovaLctScrWriter.ScreenBlock> blocks =
                NovaLctCombineHelper.splitSeparateGrouped(slots, groups, recs, model);

        assertEquals(2, blocks.size(), "2 группы (A1+A2 слиты, B отдельно) -> 2 NovaLCT-экрана");
        NovaLctScrWriter.ScreenBlock group = blocks.get(0);
        NovaLctScrWriter.ScreenBlock separate = blocks.get(1);
        assertEquals(0, group.screenXPx(), "Группа начинается с самого левого своего слота (A1, col 0)");
        assertEquals(4, group.cols(), "Bounding box группы: A1(2 col)+A2(2 col) = 4");
        assertEquals(2, group.rows(), "Bounding box группы по высоте -- максимум из A1(2)/A2(1)");
        assertEquals(768, separate.screenXPx(), "B начинается с col 6 * cabW(128) = 768px");
        assertEquals(3, separate.cols());
        assertEquals(2, separate.rows());

        long blankCount = group.cells().values().stream().filter(r -> r.card() == 255).count();
        assertEquals(2, blankCount, "A2 короче A1 на 1 ряд из 2 столбцов -- ровно 2 blank-ячейки внутри группы");
        for (var e : group.cells().entrySet()) {
            if (e.getValue().card() == 255) {
                assertTrue(e.getKey().col() >= 2 && e.getKey().row() == 1,
                        "Blank-ячейки обязаны быть под A2 (col 2-3, row 1), получено " + e.getKey());
            }
        }
        // separate (B) -- полностью расключён, дыр нет.
        assertTrue(separate.cells().values().stream().noneMatch(r -> r.card() == 255));
    }

    @Test
    void combineAssignsDifferentSendingCardsToDifferentCellsOfOneGrid(@TempDir Path dir) {
        // Пробел из NOVALCT_EXPORT.md секция 10: комбинирование нескольких экранов уже
        // покрыто, но нигде не проверено, что РАЗНЫЕ ячейки одной объединённой сетки
        // корректно несут РАЗНЫЕ физические Sending Card (не просто разные порты одной
        // карты, как в остальных тестах этого файла) -- контроллер с 2 картами, экран A
        // расключён через карту 1, экран B -- через карту 2, оба в одной объединённой сетке.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128("T"));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a = model.addScreen("A", type.getId(), 1, 2, 0, 0); // 1 row, 2 cols
        Screen b = model.addScreen("B", type.getId(), 1, 2, 0, 0); // 1 row, 2 cols
        model.selectScreen(a);

        ControllerType ct = model.addControllerType(twoCardController());
        ControllerInstance controller = model.addControllerToScreen(a, ct.getId());

        // Сквозной номер порта 1 -> карта 0/порт 0 (первые 4 порта -- карта 1);
        // номер 5 -> карта 1/порт 0 (первый порт следующей карты).
        model.addSignalChain(1, false, List.of(a.cabinetAt(0, 0).getId(), a.cabinetAt(0, 1).getId()));
        model.addSignalChain(5, false, List.of(b.cabinetAt(0, 0).getId(), b.cabinetAt(0, 1).getId()));

        List<NovaLctCombineHelper.ScreenSlot> slots = List.of(
                new NovaLctCombineHelper.ScreenSlot(a, 0, 0),
                new NovaLctCombineHelper.ScreenSlot(b, 2, 0)); // B сразу справа от A

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        assertNull(NovaLctCombineHelper.validateSlots(slots, model));
        NovaLctCombineHelper.CombineResult combined =
                NovaLctCombineHelper.combine(slots, 4, 1, recs, model);

        assertEquals(4, combined.cols());
        assertEquals(1, combined.rows());
        for (int col = 0; col <= 1; col++) {
            NovaLctScrWriter.Rec rec = combined.cells().get(new NovaLctScrWriter.CellKey(col, 0));
            assertNotNull(rec, "col=" + col + " принадлежит экрану A -- обязана быть запись");
            assertEquals(0, rec.card(), "экран A расключён через карту 0 (col=" + col + ")");
            assertEquals(0, rec.port(), "экран A -- порт 0 своей карты (col=" + col + ")");
        }
        for (int col = 2; col <= 3; col++) {
            NovaLctScrWriter.Rec rec = combined.cells().get(new NovaLctScrWriter.CellKey(col, 0));
            assertNotNull(rec, "col=" + col + " принадлежит экрану B -- обязана быть запись");
            assertEquals(1, rec.card(), "экран B расключён через карту 1, ДРУГУЮ, чем A (col=" + col + ")");
            assertEquals(0, rec.port(), "экран B -- порт 0 своей карты (col=" + col + ")");
        }

        // Итоговый .scr обязан быть валиден структурно: писатель -> наш собственный
        // парсер (round-trip, см. NovaLctScrParserTest) -- 2 разные цепочки по
        // (card,port), обе длиной 2, с восстановленной (0,0) в цепочке карты 0.
        byte[] bytes = NovaLctScrWriter.writeStandardCombined(combined);
        NovaLctScrParser.ImportResult parsed = NovaLctScrParser.parse(bytes);
        assertEquals(1, parsed.screens().size());
        NovaLctScrParser.ImportedScreen screen = parsed.screens().get(0);
        assertEquals(4, screen.width);
        assertEquals(1, screen.height);
        Map<String, List<NovaLctScrParser.CabinetEntry>> chains = screen.chainsByCardPort();
        assertEquals(2, chains.size(), "2 разные (карта,порт) -- по одной на каждый исходный экран");
        assertEquals(2, chains.get("0/0").size());
        assertEquals(2, chains.get("1/0").size());
    }
}
