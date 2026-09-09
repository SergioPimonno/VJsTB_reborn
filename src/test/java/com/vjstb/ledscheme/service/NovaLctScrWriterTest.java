package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-байтовый регрессионный тест на {@link NovaLctScrWriter} — фиксирует РЕАЛЬНЫЙ вывод NovaLCT (не наш
 * собственный, а байты, сохранённые самой NovaLCT и подтверждённые загрузкой обратно в неё
 * пользователем), чтобы рефакторинг {@code writeStandard}/{@code writeStandardCore} (выделение под
 * контроллер-центричный экспорт, см. {@link NovaLctControllerResolver}/{@link NovaLctCombineHelper}) не мог незаметно
 * сломать уже подтверждённый одноэкранный путь. Байты образцов — Base64 двух реальных файлов
 * NovaLCT: {@code t1.scr} (тривиальный 1×1 экран, 0 записей) и {@code scr1.scr} (сложный 4×3 экран,
 * 11 записей, НЕ построчная цепочка — column-major обход и происхождение поля 0x14b были
 * расшифрованы именно на этом образце).
 */
class NovaLctScrWriterTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128() {
        CabinetType ct = new CabinetType();
        ct.setName("Test 128x128");
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

    @Test
    void matchesRealSample_1x1Trivial(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(screen, controllerType.getId());

        CabinetInstance origin = screen.cabinetAt(0, 0);
        model.addSignalChain(1, false, List.of(origin.getId()));

        byte[] actual = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        byte[] expected = decode(T1_SCR_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void matchesRealSample_4x3ComplexChain(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 3, 4, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());

        int[][] seqOrderColRow = {
                {0, 2}, {0, 1}, {0, 0}, {1, 0}, {1, 1}, {1, 2},
                {2, 2}, {2, 1}, {2, 0}, {3, 0}, {3, 1}, {3, 2},
        };
        List<String> ids = new ArrayList<>();
        for (int[] colRow : seqOrderColRow) {
            CabinetInstance cab = screen.cabinetAt(colRow[1], colRow[0]);
            ids.add(cab.getId());
        }
        model.addSignalChain(1, false, ids);

        byte[] actual = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        byte[] expected = decode(SCR1_SCR_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void firstCardAndPortSkipBlankSentinelWhenPickingHeaderDefault() {
        // Столбец 0 -- целиком blank (card=0xFF), реальные записи только в столбце 1.
        // Column-major обход посещает столбец 0 первым -- заголовочные card/port
        // (смещения 0x149/0x14a) обязаны взять их из первой РЕАЛЬНОЙ записи (card=3),
        // а не ошибочно унаследовать 0xFF от blank-ячейки.
        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
        cells.put(new NovaLctScrWriter.CellKey(0, 1), new NovaLctScrWriter.Rec(1, 0, 255, 0, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 0), new NovaLctScrWriter.Rec(0, 1, 3, 7, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 1), new NovaLctScrWriter.Rec(1, 1, 3, 7, 1));

        byte[] data = NovaLctScrWriter.writeStandardCore(2, 2, 128, 128, 0, cells);
        assertEquals(3, data[0x149] & 0xff, "Заголовочный Sending Card не должен взять blank-сентинел 0xFF");
        assertEquals(7, data[0x14a] & 0xff, "Заголовочный Ethernet Port не должен взять blank-сентинел");
    }

    @Test
    void hiddenCabinetWritesExplicitBlankRecord_notOmittedEntirely(@TempDir Path dir) {
        // Прямой тест на баг-репорт ДКФ (уличная сцена с арками): скрытая ("вырезанная")
        // ячейка обязана попасть в файл ЯВНОЙ blank-записью (card=255), а не быть просто
        // не написанной вовсе -- см. javadoc NovaLctScrWriter.writeStandard и
        // ScreenLogic.isUniformRectangularGrid за полной историей (первая попытка чинить
        // это ("просто снять гейт Complex") сломала реальную загрузку в NovaLCT именно
        // потому, что не писала blank-запись, эта запись здесь и проверяется). Круговой
        // тест через NovaLctScrParser -- надёжнее, чем разбор смещений вручную: если
        // блока-записи нет вовсе, парсер либо рассинхронизируется на границе следующей
        // записи, либо не наберёт полный набор ячеек, и тест это ловит через
        // fillMissingOrigin() (та восстанавливает РОВНО ОДНУ недостающую ячейку -- origin
        // -- и провалилась бы, если бы недостающих ячеек было ДВЕ, как до этого фикса).
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());

        CabinetInstance origin = screen.cabinetAt(0, 0);
        CabinetInstance c01 = screen.cabinetAt(0, 1);
        CabinetInstance c11 = screen.cabinetAt(1, 1);
        CabinetInstance hidden = screen.cabinetAt(1, 0);
        model.addSignalChain(1, false, List.of(origin.getId(), c01.getId(), c11.getId()));
        model.toggleCabinetHidden(hidden.getId());

        assertFalse(NovaLctScrWriter.isComplexExport(screen, model.getWorkspace()),
                "скрытая ячейка сама по себе не форсирует Complex -- пишем Standard с blank-ом");

        byte[] bytes = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        NovaLctScrParser.ImportResult parsed = NovaLctScrParser.parse(bytes);
        assertEquals(1, parsed.screens().size());
        NovaLctScrParser.ImportedScreen imported = parsed.screens().get(0);
        assertEquals(2, imported.width);
        assertEquals(2, imported.height);
        assertEquals(4, imported.cabinets.size(),
                "все 4 ячейки сетки обязаны присутствовать в файле -- скрытая как явный "
                        + "blank (card=255), origin (0,0) восстанавливается парсером из заголовка");

        NovaLctScrParser.CabinetEntry hiddenEntry = imported.cabinets.stream()
                .filter(e -> e.row() == 1 && e.col() == 0)
                .findFirst().orElseThrow();
        assertEquals(255, hiddenEntry.card(), "скрытая ячейка обязана попасть в файл как явный blank-сентинел");
    }

    @Test
    void controllerNotFirstInScene_stillExportsCabinetRecords(@TempDir Path dir) {
        // Баг-репорт: проект с несколькими одноэкранными контроллерами MCTRL4k
        // (по одному на экран) — в NovaLCT грузился только экран ПЕРВОГО
        // контроллера, у остальных импорт падал. Причина: одноэкранная ветка
        // экспорта резолвила порты через ScreenLogic.cardAndLocalPort, который
        // отсчитывает локальный порт с нуля по контроллерам самого экрана — для
        // не-первого контроллера scene-wide номер порта выходит за его ёмкость,
        // cardAndLocalPort возвращает null, и файл уходил без единой кабинетной
        // записи. writeForResolvedScreen берёт записи резолвера, где смещение
        // порта посчитано правильно (AppModel.portOffsetOf).
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen1 = model.addScreen("E1", type.getId(), 1, 2, 0, 0);
        Screen screen2 = model.addScreen("E2", type.getId(), 1, 3, 2000, 0);

        ControllerType controllerType = model.addControllerType(singleCardController()); // 20 портов
        model.addControllerToScreen(screen1, controllerType.getId());                    // порты сцены 1..20
        ControllerInstance controller2 =
                model.addControllerToScreen(screen2, controllerType.getId());            // порты сцены 21..40

        // Цепочка второго экрана несёт scene-wide номер порта 21 — первый порт
        // второго контроллера. (addSignalChain молча выходит без выбранного экрана.)
        model.selectScreen(screen2);
        List<String> ids = screen2.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(21, false, ids);

        // Механизм бага: одноэкранный резолв не видит порт не-первого контроллера.
        assertNull(ScreenLogic.cardAndLocalPort(screen2, model.getWorkspace(), 21),
                "предпосылка бага: cardAndLocalPort роняет порт не-первого контроллера сцены");

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller2, model);
        assertEquals(3, recs.size());

        byte[] fixed = NovaLctScrWriter.writeForResolvedScreen(screen2, recs, model.getWorkspace());
        NovaLctScrParser.ImportResult parsed = NovaLctScrParser.parse(fixed);
        assertEquals(1, parsed.screens().size());
        NovaLctScrParser.ImportedScreen imported = parsed.screens().get(0);
        assertEquals(3, imported.width);
        assertEquals(1, imported.height);
        assertEquals(3, imported.cabinets.size(), "все 3 кабинета экрана обязаны попасть в файл");
        for (NovaLctScrParser.CabinetEntry e : imported.cabinets) {
            assertEquals(0, e.card(), "кабинет несёт реальную карту, а не отброшен");
            assertEquals(0, e.port());
        }
    }

    @Test
    void writeForResolvedScreen_firstControllerMatchesLegacyWrite(@TempDir Path dir) {
        // Для контроллера, ПЕРВОГО в сцене (единственный уже подтверждённый путь),
        // writeForResolvedScreen обязан давать байт-в-байт то же, что старый
        // write(screen, scene, ws) — сценарий тот же, что в golden-тесте 4×3.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 3, 4, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(screen, controllerType.getId());

        int[][] seqOrderColRow = {
                {0, 2}, {0, 1}, {0, 0}, {1, 0}, {1, 1}, {1, 2},
                {2, 2}, {2, 1}, {2, 0}, {3, 0}, {3, 1}, {3, 2},
        };
        List<String> ids = new ArrayList<>();
        for (int[] colRow : seqOrderColRow) {
            ids.add(screen.cabinetAt(colRow[1], colRow[0]).getId());
        }
        model.addSignalChain(1, false, ids);

        byte[] legacy = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        byte[] viaRecs = NovaLctScrWriter.writeForResolvedScreen(screen, recs, model.getWorkspace());
        assertArrayEquals(legacy, viaRecs);
    }

    /** Байт по смещению 0x13f: у Standard это константа заголовка 0x01, у Complex —
     *  поле {@code Type=2} сразу за COMPLEX_HEADER_LEN. Дискриминатор формата без
     *  доступа к приватным константам писателя. */
    private static boolean isComplexScr(byte[] d) {
        return (d[0x13f] & 0xff) == 2;
    }

    @Test
    void mixedMultiScreen_standardPlusComplex_matchesRealNovaLctSample() {
        // Golden-байт: реальный standart+complex.scr, сохранённый самой NovaLCT
        // (Screen Connection → Quantity of Screens = 2: Screen1 = Standard 2×2,
        // Screen2 = Complex, 4 карты, у части ненулевые StartX/StartY).
        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
        cells.put(new NovaLctScrWriter.CellKey(0, 0), new NovaLctScrWriter.Rec(0, 0, 0, 0, 1)); // origin, seq 1
        cells.put(new NovaLctScrWriter.CellKey(0, 1), new NovaLctScrWriter.Rec(1, 0, 0, 0, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 0), new NovaLctScrWriter.Rec(0, 1, 0, 0, 2));
        cells.put(new NovaLctScrWriter.CellKey(1, 1), new NovaLctScrWriter.Rec(1, 1, 0, 0, 3));
        NovaLctScrWriter.ScreenBlock std = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 0, cells);

        NovaLctScrWriter.ComplexScreenBlock cplx = new NovaLctScrWriter.ComplexScreenBlock(128, 128, List.of(
                new NovaLctScrWriter.ComplexRec(0, 1, 0, 0, 176, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 1, 0, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 2, 188, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 3, 212, 128, 128, 128)));

        byte[] actual = NovaLctScrWriter.writeMixedMultiScreen(List.of(
                NovaLctScrWriter.MixedScreen.of(cplx), NovaLctScrWriter.MixedScreen.of(std))); // порядок вход. не важен
        assertArrayEquals(decode(STANDART_COMPLEX_SCR_BASE64), actual);
    }

    @Test
    void mixedMultiScreen_complexPlusComplex_matchesRealNovaLctSample() {
        // Golden-байт: реальный complex+complex.scr (Quantity = 2, оба экрана Complex).
        NovaLctScrWriter.ComplexScreenBlock c0 = new NovaLctScrWriter.ComplexScreenBlock(128, 128, List.of(
                new NovaLctScrWriter.ComplexRec(0, 0, 0, 0, 128, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 0, 1, 0, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 0, 2, 128, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 0, 3, 128, 128, 128, 128)));
        NovaLctScrWriter.ComplexScreenBlock c1 = new NovaLctScrWriter.ComplexScreenBlock(128, 128, List.of(
                new NovaLctScrWriter.ComplexRec(0, 1, 0, 0, 176, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 1, 0, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 2, 188, 0, 128, 128),
                new NovaLctScrWriter.ComplexRec(0, 1, 3, 212, 128, 128, 128)));

        byte[] actual = NovaLctScrWriter.writeMixedMultiScreen(List.of(
                NovaLctScrWriter.MixedScreen.of(c0), NovaLctScrWriter.MixedScreen.of(c1)));
        assertArrayEquals(decode(COMPLEX_COMPLEX_SCR_BASE64), actual);
    }

    @Test
    void sharedScreen_exportsReindexedSubScreen_notFullGridWithHoles(@TempDir Path dir) {
        // Баг-репорт: экран расключён с ДВУХ контроллеров (левая/правая половина).
        // Экспорт каждого раньше писал ПОЛНУЮ сетку экрана с записями только для
        // своих колонок — половина ячеек без записи → NovaLCT отклоняет импорт.
        // Теперь пишется пере-индексированный кусок этого контроллера.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 4, 0, 0); // 2 ряда × 4 столбца
        model.selectScreen(screen);

        ControllerType ctype = model.addControllerType(singleCardController()); // 20 портов
        ControllerInstance left = model.addControllerToScreen(screen, ctype.getId());   // порты 1..20
        ControllerInstance right = model.addControllerToScreen(screen, ctype.getId());  // порты 21..40

        // Левая половина (столбцы 0..1) — на контроллере left, порт 1.
        model.addSignalChain(1, false, List.of(
                screen.cabinetAt(0, 0).getId(), screen.cabinetAt(1, 0).getId(),
                screen.cabinetAt(1, 1).getId(), screen.cabinetAt(0, 1).getId()));
        // Правая половина (столбцы 2..3) — на контроллере right, порт 21.
        model.addSignalChain(21, false, List.of(
                screen.cabinetAt(0, 2).getId(), screen.cabinetAt(1, 2).getId(),
                screen.cabinetAt(1, 3).getId(), screen.cabinetAt(0, 3).getId()));

        assertEquals(2, NovaLctControllerResolver.controllersWiringScreen(scene, screen, model).size(),
                "экран должен опознаваться как расключённый двумя контроллерами");

        List<NovaLctControllerResolver.CabinetRec> recsRight =
                NovaLctControllerResolver.resolve(scene, right, model);
        assertEquals(4, recsRight.size());

        // Режим «отдельный screen» (оффсет 0): столбцы 2..3 → локальная сетка 2×2 в (0,0).
        byte[] atOrigin = NovaLctScrWriter.writeResolvedSubScreen(screen, recsRight, model.getWorkspace(), 0, 0);
        assertFalse(isComplexScr(atOrigin), "ровный кусок без сдвига по Y → Standard");
        NovaLctScrParser.ImportedScreen s = NovaLctScrParser.parse(atOrigin).screens().get(0);
        assertEquals(2, s.width);
        assertEquals(2, s.height);
        assertEquals(4, s.cabinets.size(), "все 4 кабинета куска в файле (3 записи + восстановленный origin)");
        for (NovaLctScrParser.CabinetEntry e : s.cabinets) {
            assertEquals(0, e.card());
            assertEquals(0, e.port());
            assertTrue(e.row() >= 0 && e.row() <= 1 && e.col() >= 0 && e.col() <= 1,
                    "ряды/столбцы пере-индексированы к локальным 0..1 (было ряды 0..1, столбцы 2..3)");
        }
        int maxCol = s.cabinets.stream().mapToInt(NovaLctScrParser.CabinetEntry::col).max().orElse(-1);
        assertEquals(1, maxCol, "столбцы пере-индексированы к локальным 0..1 (было 2..3)");

        // Режим «сохранить положение»: Coordinate X = 2×128 = 256, всё ещё Standard.
        byte[] keepPos = NovaLctScrWriter.writeResolvedSubScreen(screen, recsRight, model.getWorkspace(), 256, 0);
        assertFalse(isComplexScr(keepPos));
        assertFalse(java.util.Arrays.equals(atOrigin, keepPos), "Coordinate X попадает в файл");
        assertEquals(256, (keepPos[0x141] & 0xff) | ((keepPos[0x142] & 0xff) << 8),
                "Coordinate X пишется ПОЛНЫМ LE16 (256 > 255)");
        assertEquals(256, (keepPos[0x14d] & 0xff) | ((keepPos[0x14e] & 0xff) << 8),
                "дубль Coordinate X — тоже LE16");
        NovaLctScrParser.ImportedScreen sk = NovaLctScrParser.parse(keepPos).screens().get(0);
        assertEquals(2, sk.width);
        assertEquals(2, sk.height);
        assertEquals(4, sk.cabinets.size());

        // Сдвиг по Y не выражается в Standard .scr → кусок уходит в Complex.
        byte[] withY = NovaLctScrWriter.writeResolvedSubScreen(screen, recsRight, model.getWorkspace(), 256, 128);
        assertTrue(isComplexScr(withY), "ненулевой сдвиг по Y → Complex (там есть пиксельные X/Y на карту)");
    }

    @Test
    void mixedMultiScreen_fromResolvedScene_isWellFormedOneFile(@TempDir Path dir) {
        // Контроллер обслуживает Complex-экран (сдвинутый кабинет) И обычный.
        // Экспорт собирает ОДИН .scr: Standard-блок обычного экрана + Complex-блок
        // неровного (writeMixedMultiScreen). Проверяем, что файл цельный и один.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen complexScreen = model.addScreen("CX", type.getId(), 1, 3, 0, 0);
        Screen plainScreen = model.addScreen("STD", type.getId(), 1, 3, 4000, 0);

        ControllerType ctype = model.addControllerType(singleCardController());
        ControllerInstance ctrl = model.addControllerToScreen(complexScreen, ctype.getId());

        model.updateCabinetOffset(complexScreen.cabinetAt(0, 2), 10.0, 0.0); // → Complex
        model.selectScreen(complexScreen);
        model.addSignalChain(1, false, complexScreen.getCabinets().stream()
                .map(CabinetInstance::getId).toList());
        model.selectScreen(plainScreen);
        model.addSignalChain(2, false, plainScreen.getCabinets().stream()
                .map(CabinetInstance::getId).toList());

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, ctrl, model);

        List<NovaLctScrWriter.MixedScreen> mixed = List.of(
                NovaLctScrWriter.MixedScreen.of(
                        NovaLctScrWriter.resolvedStandardScreen(plainScreen, recs, model.getWorkspace())),
                NovaLctScrWriter.MixedScreen.of(
                        NovaLctScrWriter.resolvedComplexScreen(complexScreen, recs, model.getWorkspace())));
        byte[] data = NovaLctScrWriter.writeMixedMultiScreen(mixed);

        assertEquals('D', data[0]);
        assertEquals('I', data[3]);
        assertEquals(2, data[0x13a] & 0xff, "SCREENCOUNT = 2");
        // хвостовой блок коррекции: ea 03, длина 133+40*N
        int tf = data.length - (133 + 40 * 2);
        assertEquals(0xea, data[tf] & 0xff);
        assertEquals(0x03, data[tf + 1] & 0xff);
        assertEquals(2, data[tf + 132] & 0xff);
        // Блок экрана 0 начинается с descBase = 0x13f+4*(N-1) = 0x143; порядок
        // экранов — по возрастанию (Sending Card, Port), поэтому первым может быть
        // как Standard (0x01), так и Complex (Type=2).
        assertTrue((data[0x143] & 0xff) == 0x01 || (data[0x143] & 0xff) == 0x02);
        // Complex-блок присутствует: пиксельная запись с w=h=128 у неровного экрана
        // даёт байты 80 00 80 00 (w,h LE16) — и есть 6-байтный якорь Standard-экрана.
        boolean hasAnchor = false;
        for (int i = 0x143; i < tf - 6; i++) {
            if ((data[i] & 0xff) == 0x00 && (data[i + 1] & 0xff) == 0x80 && (data[i + 2] & 0xff) == 0x00
                    && (data[i + 3] & 0xff) == 0x80 && (data[i + 4] & 0xff) == 0x00
                    && (data[i + 5] & 0xff) == 0x01) {
                hasAnchor = true;
                break;
            }
        }
        assertTrue(hasAnchor, "Standard-экран в файле → есть 6-байтный якорь 00 80 00 80 00 01");
    }

    @Test
    void writeForResolvedScreen_picksFormatPerScreen_withRecsSpanningTwoScreens(@TempDir Path dir) {
        // Один контроллер обслуживает и Complex-экран (сдвинутый кабинет), и
        // обычный прямоугольный. `writeForResolvedScreen` для каждого экрана должен
        // выбрать верный формат (Complex vs Standard) и учесть только записи ЭТОГО
        // экрана, хотя в `recs` кабинеты обоих. (Сам мультиэкранный .scr со
        // смешанными блоками пока не поддержан — экспорт таких контроллеров
        // блокируется, см. NovaLctControllerExportDialog / NOVALCT_EXPORT.md §5.)
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen complexScreen = model.addScreen("CX", type.getId(), 1, 3, 0, 0);
        Screen plainScreen = model.addScreen("STD", type.getId(), 1, 3, 4000, 0);

        ControllerType ctype = model.addControllerType(singleCardController()); // 20 портов
        ControllerInstance ctrl = model.addControllerToScreen(complexScreen, ctype.getId()); // порты 1..20

        // Крайний правый кабинет Complex-экрана сдвинут наружу → грид становится
        // неровным (isUniformRectangularGrid=false), но соседей он не перекрывает,
        // так что ни один кабинет не деактивируется автоматически.
        model.updateCabinetOffset(complexScreen.cabinetAt(0, 2), 10.0, 0.0);

        model.selectScreen(complexScreen);
        model.addSignalChain(1, false, complexScreen.getCabinets().stream()
                .map(CabinetInstance::getId).toList());
        model.selectScreen(plainScreen);
        model.addSignalChain(2, false, plainScreen.getCabinets().stream()
                .map(CabinetInstance::getId).toList());

        assertTrue(NovaLctScrWriter.isComplexExport(complexScreen, model.getWorkspace()));
        assertFalse(NovaLctScrWriter.isComplexExport(plainScreen, model.getWorkspace()));

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, ctrl, model);
        assertEquals(6, recs.size(), "6 кабинетов обоих экранов на одном контроллере");

        byte[] cx = NovaLctScrWriter.writeForResolvedScreen(complexScreen, recs, model.getWorkspace());
        byte[] std = NovaLctScrWriter.writeForResolvedScreen(plainScreen, recs, model.getWorkspace());
        assertTrue(isComplexScr(cx), "Complex-экран → Complex .scr");
        assertFalse(isComplexScr(std), "обычный экран → Standard .scr");

        NovaLctScrParser.ImportedScreen s = NovaLctScrParser.parse(std).screens().get(0);
        assertEquals(3, s.width);
        assertEquals(1, s.height);
        assertEquals(3, s.cabinets.size());
    }

    @Test
    void singleControllerScreen_notDetectedAsShared(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        ControllerType ctype = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, ctype.getId());
        model.addSignalChain(1, false,
                screen.getCabinets().stream().map(CabinetInstance::getId).toList());

        assertEquals(1, NovaLctControllerResolver.controllersWiringScreen(scene, screen, model).size(),
                "экран на одном контроллере не должен уходить на путь пере-индексации куска");
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static final String T1_SCR_BASE64 =
            "RFNDScsXgAAAAOgAAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA2gRAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAApAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAABGwAAAAEAAAAAAAEAAQAAAAAAAAAAAAAAAACAAIAAAUIAW3sic2kiOjAsIngxIjowLCJ5MSI6MCwieDIi"
            + "OjAsInkyIjowLCJ4MyI6MCwieTMiOjAsIng0IjowLCJ5NCI6MH1d6gPnAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String STANDART_COMPLEX_SCR_BASE64 =
              "RFNDSSsjgAAAAGUBAADVAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAADuAz8dAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACTgAAAEYAAAABAAAAAAAC"
            + "AAIAAAABAAAAAAAAAAAAgACAAAEAAAAAAACAAAAAAQCAAIAAAQAAAgCAAAAAAQAAAIAAgAABAAADAIAAgAABAAEAgACAAAECAAQAAAAAAQAAAA"
            + "CwAAAAAACAAIAAAAEBAAAAAAAAAAAAgACAAAABAgC8AAAAAAAAAIAAgAAAAQMA1ACAAAAAAACAAIAAQgBbeyJzaSI6MCwieDEiOjAsInkxIjow"
            + "LCJ4MiI6MCwieTIiOjAsIngzIjowLCJ5MyI6MCwieDQiOjAsInk0IjowfV3qA84BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAACAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAA=";

    private static final String COMPLEX_COMPLEX_SCR_BASE64 =
              "RFNDSdITgAAAAB0BAADVAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAADuAz4OAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACRgAAAEYAAAACAAQAAAAA"
            + "AAAAAACAAAAAAACAAIAAAAABAAAAAAAAAAAAgACAAAAAAgCAAAAAAAAAAIAAgAAAAAMAgACAAAAAAACAAIAAAgAEAAAAAAEAAAAAsAAAAAAAgA"
            + "CAAAABAQAAAAAAAAAAAIAAgAAAAQIAvAAAAAAAAACAAIAAAAEDANQAgAAAAAAAgACAAAIAW13qA84BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAHkAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String SCR1_SCR_BASE64 =
            "RFNDSXoogAAAAKMBAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA1kiAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAXwEAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAB1gAAAAEAAAAAAAQAAwAAAAIAAAAAAAAAAACAAIAAAQAAAQAAAIAAAAABAIAAgAABAAAAAAAAAAEAAAIA"
            + "gACAAAEAAAMAgAAAAAEAAACAAIAAAQAABACAAIAAAQABAIAAgAABAAAFAIAAAAEBAAIAgACAAAEAAAgAAAEAAAIAAACAAIAAAQAA"
            + "BwAAAYAAAgABAIAAgAABAAAGAAABAAECAAIAgACAAAEAAAkAgAEAAAMAAACAAIAAAQAACgCAAYAAAwABAIAAgAABAAALAIABAAED"
            + "AAIAgACAAAFCAFt7InNpIjowLCJ4MSI6MCwieTEiOjAsIngyIjowLCJ5MiI6MCwieDMiOjAsInkzIjowLCJ4NCI6MCwieTQiOjB9"
            + "XeoD5wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAQAB5AAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
}
