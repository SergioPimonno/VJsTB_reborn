package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NovaLCT-экспорт МОДУЛЬНОГО экрана (docs/receiver-cards/PLAN.md, раздел 7, трек E): приёмная карта — «кабинет» NovaLCT
 * размером с прямоугольник своих модулей в px, порядок на порту — порядок звеньев цепочки. Тесты структурные (как
 * {@code NovaLctMultiScreenWriterTest}): реальных образцов NovaLCT с картами нет, поэтому байты читает собственный
 * разбор записей здесь, а не golden-файл. Немодульные экраны охраняют старые golden-тесты — этот файл их не касается.
 */
class NovaLctModularScrWriterTest {

    private static final int HEADER_LEN = 0x154;
    private static final int COMPLEX_HEADER_LEN = 0x13f;

    private AppModel model;
    private Scene scene;
    private Screen screen;
    private ControllerInstance controller;
    private CabinetType module;
    private ReceiverCardType cardType;

    /** Экран {@code rows × cols} из модулей 250 мм, 64×64 px; контроллер с одной картой на 20 портов. */
    private void setUp(Path dir, int rows, int cols) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setReceiverCardSeparate(true);
        model.addCabinetType(module);
        cardType = new ReceiverCardType();
        cardType.setName("К");
        cardType.setModuleOutputs(16);
        model.addReceiverCardType(cardType);
        model.selectProject(model.addProject("P"));
        scene = model.addScene("S");
        model.selectScene(scene);
        screen = model.addScreen("A", module.getId(), rows, cols, 0, 0);
        model.selectScreen(screen);
        EquipmentPreset preset = model.addControllerPreset("Контроллер", "", 20, 1000, 0, false,
                List.of(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20)))));
        controller = model.addControllerToScreen(screen, preset.getId(), null);
    }

    private String card(int r0, int c0, int r1, int c1) {
        model.assignReceiverCardsManual(new GridRect(r0, c0, r1, c1), cardType.getId());
        return ChainLinks.unitContaining(screen, screen.cabinetAt(r0, c0).getId(), ChainUnits.Mode.SIGNAL);
    }

    private byte[] export(Boolean forceComplex) {
        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        return NovaLctScrWriter.writeForResolvedScreen(screen, recs, model.getWorkspace(), forceComplex);
    }

    // ------------------------------------------------------------------ разбор байтов

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private record StdRec(int w, int h, int card, int port, int seq, int x, int y, int col, int row) {
    }

    private record Std(int cols, int rows, int originSeq, List<StdRec> recs, int[] trailingAnchor) {
    }

    private static Std parseStandard(byte[] b) {
        int cols = u16(b, 0x145);
        int rows = u16(b, 0x147);
        int n = (u16(b, 0x0a) + 108 - HEADER_LEN) / 17;
        List<StdRec> recs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int o = HEADER_LEN + 17 * i;
            recs.add(new StdRec(u16(b, o + 1), u16(b, o + 3), b[o + 6] & 0xff, b[o + 7] & 0xff, u16(b, o + 8),
                    u16(b, o + 10), u16(b, o + 12), b[o + 14] & 0xff, b[o + 16] & 0xff));
        }
        int t = HEADER_LEN + 17 * n;
        return new Std(cols, rows, u16(b, 0x14b), recs, new int[]{u16(b, t + 1), u16(b, t + 3)});
    }

    private record CxRec(int card, int port, int seq, int x, int y, int w, int h) {
    }

    private static List<CxRec> parseComplex(byte[] b) {
        assertEquals(2, b[COMPLEX_HEADER_LEN], "Type=2 — Complex");
        int n = u16(b, COMPLEX_HEADER_LEN + 2);
        List<CxRec> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int o = COMPLEX_HEADER_LEN + 6 + 16 * i;
            out.add(new CxRec(b[o] & 0xff, b[o + 1] & 0xff, u16(b, o + 2), u16(b, o + 4), u16(b, o + 6),
                    u16(b, o + 12), u16(b, o + 14)));
        }
        return out;
    }

    private static StdRec at(Std s, int col, int row) {
        return s.recs().stream().filter(r -> r.col() == col && r.row() == row).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ Standard: сетка карт

    @Test
    void equalCardsAreWrittenAsStandardGridOfCards(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        String c = card(2, 0, 3, 1);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b, d, c));

        assertEquals(List.of(), NovaLctScrWriter.complexReasons(screen,
                NovaLctControllerResolver.resolve(scene, controller, model), model.getWorkspace()));
        Std s = parseStandard(export(null));

        assertEquals(2, s.cols(), "сетка КАРТ 2×2, а не модулей 4×4");
        assertEquals(2, s.rows());
        assertEquals(0, s.originSeq(), "карта A — первая в цепочке; она в ячейке (0,0)");
        assertEquals(3, s.recs().size(), "ячейка (0,0) отдельной записью не пишется");
    }

    @Test
    void cardOrderOnPortIsChainOrderAndCellSizeIsCardRectangle(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        String c = card(2, 0, 3, 1);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b, d, c));

        Std s = parseStandard(export(null));

        // Колонковый обход: (0,0)=A не пишется; (0,1)=C; (1,0)=B; (1,1)=D.
        assertNull(at(s, 0, 0));
        assertEquals(3, at(s, 0, 1).seq(), "C — четвёртая в цепочке");
        assertEquals(1, at(s, 1, 0).seq(), "B — вторая");
        assertEquals(2, at(s, 1, 1).seq(), "D — третья");
        assertEquals(128, s.trailingAnchor()[0], "размер «кабинета» NovaLCT — 2×2 модуля 64 px = 128 px");
        assertEquals(128, s.trailingAnchor()[1]);
        assertEquals(128, at(s, 1, 0).x(), "позиция B — сумма ширин левее");
        assertEquals(128, at(s, 0, 1).y(), "позиция C — сумма высот выше");
        for (StdRec r : s.recs()) {
            assertEquals(0, r.card());
            assertEquals(0, r.port(), "порт 1 → Ethernet-порт 0 карты 0");
        }
    }

    @Test
    void cardsOfDifferentSizesOnDifferentPortsStayStandardWithPerCellSizes(@TempDir Path dir) {
        setUp(dir, 4, 4);
        // Верхний ряд карт — 2×1 модуля (128×64), нижний — 2×3 модуля (128×192).
        String a = card(0, 0, 0, 1);
        String b = card(0, 2, 0, 3);
        String c = card(1, 0, 3, 1);
        String d = card(1, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b));
        model.addSignalChain(2, false, List.of(c, d));

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        assertEquals(List.of(), NovaLctScrWriter.complexReasons(screen, recs, model.getWorkspace()),
                "на каждом порту размер один — Standard допустим (как в образце 128×128 + 128×64)");
        Std s = parseStandard(export(null));

        assertEquals(2, s.cols());
        assertEquals(2, s.rows());
        // Записи в колоночном порядке: C(0,1), B(1,0), D(1,1); перед записью — размер предыдущей ячейки.
        assertEquals(64, at(s, 0, 1).y(), "C стоит под A: сумма высот выше = 64");
        assertEquals(128, at(s, 0, 1).w(), "якорь перед C — размер A: ширина 128");
        assertEquals(64, at(s, 0, 1).h(), "якорь перед C — размер A (128×64)");
        assertEquals(192, at(s, 1, 0).h(), "якорь перед B — размер C (128×192)");
        assertEquals(64, at(s, 1, 1).h(), "якорь перед D — размер B (128×64)");
        assertEquals(192, s.trailingAnchor()[1], "завершающий якорь — размер последней записи D");
        assertEquals(1, at(s, 0, 1).port(), "карты нижнего ряда — порт 2");
        assertEquals(0, at(s, 1, 0).port(), "B — порт 1");
    }

    @Test
    void mixedSizesOnOnePortForceComplexWithReason(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 0, 1);
        String c = card(1, 0, 3, 1);
        model.addSignalChain(1, false, List.of(a, c));

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        List<String> reasons = NovaLctScrWriter.complexReasons(screen, recs, model.getWorkspace());
        assertEquals(1, reasons.size(), reasons.toString());
        assertTrue(reasons.get(0).contains("на одном порту"), reasons.get(0));
        assertTrue(NovaLctScrWriter.isComplexExport(screen, recs, model.getWorkspace()));
        List<CxRec> cards = parseComplex(export(null));
        assertEquals(2, cards.size());
    }

    // ------------------------------------------------------------------ Complex: пиксельные прямоугольники карт

    @Test
    void unalignedCardsAreComplexWithPixelRectangles(@TempDir Path dir) {
        setUp(dir, 4, 4);
        // Верхний ряд: 3 модуля + 1 модуль; нижний: 2 + 2 — линии стыков не совпадают → не сетка.
        String a = card(0, 0, 1, 2);
        String b = card(0, 3, 1, 3);
        String c = card(2, 0, 3, 1);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b));
        model.addSignalChain(2, false, List.of(c, d));

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        List<String> reasons = NovaLctScrWriter.complexReasons(screen, recs, model.getWorkspace());
        assertFalse(reasons.isEmpty());
        assertTrue(NovaLctScrWriter.isComplexExport(screen, recs, model.getWorkspace()));

        List<CxRec> cards = parseComplex(export(null));
        assertEquals(4, cards.size());
        CxRec first = cards.get(0);
        assertEquals(new CxRec(0, 0, 0, 0, 0, 192, 128), first, "карта A: 3×2 модуля = 192×128 px в (0,0)");
        assertEquals(new CxRec(0, 0, 1, 192, 0, 64, 128), cards.get(1), "карта B справа, seq 1");
        assertEquals(new CxRec(0, 1, 0, 0, 128, 128, 128), cards.get(2), "карта C — порт 2, seq 0");
        assertEquals(new CxRec(0, 1, 1, 128, 128, 128, 128), cards.get(3));
    }

    @Test
    void forcedStandardOnUnalignedCardsStillWritesAFile(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 2);
        String b = card(0, 3, 1, 3);
        String c = card(2, 0, 3, 1);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b, c, d));

        Std s = parseStandard(export(Boolean.FALSE));
        assertEquals(3, s.cols(), "три различных X начала карт");
        assertEquals(2, s.rows());
        assertEquals(3, s.recs().stream().filter(r -> r.card() != 255).count(),
                "три записи карт B, C, D; карта A — в ячейке (0,0), её запись в заголовке");
        assertEquals(4, parseComplex(export(Boolean.TRUE)).size(), "forceComplex=true — Complex на 4 карты");
    }

    // ------------------------------------------------------------------ вырезы, незаведённые модули, смешанный экран

    @Test
    void hiddenModulesOfWholeCardBecomeBlankRecordButUnassignedModulesDoNot(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        String c = card(2, 0, 3, 1);
        // Модули нижней правой четверти скрыты (форма экрана с вырезом): там «кабинета нет» → blank-сентинел 0xFF.
        for (int r = 2; r <= 3; r++) {
            for (int col = 2; col <= 3; col++) {
                model.toggleCabinetHidden(screen.cabinetAt(r, col).getId());
            }
        }
        model.addSignalChain(1, false, List.of(a, b, c));

        Std s = parseStandard(export(null));

        assertEquals(255, at(s, 1, 1).card(), "вырез внутри прямоугольника — явный blank, иначе NovaLCT не загрузит файл");
    }

    @Test
    void modulesWithoutCardAreNotBlankButUnwired(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        String c = card(2, 0, 3, 1);
        // Нижняя правая четверть видима, но карты у неё нет — «ещё не расключено», записи нет (как у обычного кабинета).
        model.addSignalChain(1, false, List.of(a, b, c));

        Std s = parseStandard(export(null));

        assertNull(at(s, 1, 1), "нерасключённые модули в файл не попадают");
        assertEquals(2, s.recs().size());
    }

    @Test
    void ordinaryCabinetOfMixedScreenIsItsOwnNovaLctCabinet(@TempDir Path dir) {
        setUp(dir, 2, 3);
        CabinetType ordinary = new CabinetType();
        ordinary.setName("Кабинет");
        ordinary.setWidthMm(250);
        ordinary.setHeightMm(250);
        ordinary.setResolutionWidth(128);
        ordinary.setResolutionHeight(128);
        model.addCabinetType(ordinary);
        String a = card(0, 0, 1, 1);
        CabinetInstance cab = screen.cabinetAt(0, 2);
        model.setCabinetTypeOverride(cab.getId(), ordinary.getId());
        model.toggleCabinetHidden(screen.cabinetAt(1, 2).getId());
        model.addSignalChain(1, false, List.of(a, cab.getId()));

        Std s = parseStandard(export(null));

        assertEquals(2, s.cols());
        assertEquals(1, s.rows());
        StdRec o = at(s, 1, 0);
        assertNotNull(o);
        assertEquals(1, o.seq());
        assertEquals(128, s.trailingAnchor()[0], "обычный кабинет 128×128, как и карта 2×2 модуля");
    }

    // ------------------------------------------------------------------ другие входы писателя

    @Test
    void screenCentricWriteMatchesControllerCentricForFirstController(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        String c = card(2, 0, 3, 1);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b, d, c));

        assertEquals(java.util.Arrays.toString(export(null)),
                java.util.Arrays.toString(NovaLctScrWriter.write(screen, scene, model.getWorkspace())));
    }

    @Test
    void subScreenOfModularScreenIsComplexRelativeToItsOrigin(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String b = card(0, 2, 1, 3);
        String d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(b, d));

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        int[] origin = NovaLctScrWriter.modularOriginPx(screen, recs, model.getWorkspace());
        assertEquals(128, origin[0], "карты B и D начинаются с X = 128 px");
        assertEquals(0, origin[1]);

        List<CxRec> cards = parseComplex(NovaLctScrWriter.writeResolvedSubScreen(screen, recs,
                model.getWorkspace(), 0, 0));
        assertEquals(new CxRec(0, 0, 0, 0, 0, 128, 128), cards.get(0), "в режиме «отдельный screen» X/Y куска = 0");
        assertEquals(new CxRec(0, 0, 1, 0, 128, 128, 128), cards.get(1));
    }

    @Test
    void mixedMultiScreenBlocksAcceptModularScreen(@TempDir Path dir) {
        setUp(dir, 4, 4);
        String a = card(0, 0, 1, 1);
        String b = card(0, 2, 1, 3);
        model.addSignalChain(1, false, List.of(a, b));
        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);

        NovaLctScrWriter.ComplexScreenBlock cx = NovaLctScrWriter.resolvedComplexScreen(screen, recs,
                model.getWorkspace());
        assertEquals(2, cx.cards().size());
        assertEquals(128, cx.cards().get(1).x());
        NovaLctScrWriter.ScreenBlock std = NovaLctScrWriter.resolvedStandardScreen(screen, recs,
                model.getWorkspace());
        assertEquals(2, std.cols());
        assertEquals(1, std.rows());
        assertNotNull(NovaLctScrWriter.writeMixedMultiScreen(List.of(
                NovaLctScrWriter.MixedScreen.of(std), NovaLctScrWriter.MixedScreen.of(cx))));
    }

    @Test
    void nonRectangularCardIsReportedInReasons(@TempDir Path dir) {
        setUp(dir, 2, 2);
        // Г-образная карта: три модуля из четырёх, собранная прямо в данных.
        ReceiverCardInstance l = new ReceiverCardInstance(cardType.getId(), List.of(
                screen.cabinetAt(0, 0).getId(), screen.cabinetAt(0, 1).getId(), screen.cabinetAt(1, 0).getId()));
        screen.getReceiverCards().add(l);
        model.addSignalChain(1, false, List.of(l.getId()));

        List<String> reasons = NovaLctScrWriter.complexReasons(screen,
                NovaLctControllerResolver.resolve(scene, controller, model), model.getWorkspace());
        assertTrue(reasons.stream().anyMatch(r -> r.contains("не прямоугольная")), reasons.toString());
    }

    /**
     * Генератор образцов для ручной проверки загрузкой в NovaLCT ({@code docs/receiver-cards/samples/}). Не тест:
     * по умолчанию пропускается, запускается {@code mvn test -Dtest=NovaLctModularScrWriterTest
     * -Dscr.samples.dir=<папка>}.
     */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "scr.samples.dir", matches = ".+")
    void generateSamplesForManualNovaLctCheck(@TempDir Path dir) throws Exception {
        Path out = Path.of(System.getProperty("scr.samples.dir"));
        java.nio.file.Files.createDirectories(out);

        // 1) Standard: сетка карт 2×2, верхний ряд — 128×64 (порт 1), нижний — 128×192 (порт 2).
        java.nio.file.Files.createDirectories(dir.resolve("std"));
        setUp(dir.resolve("std"), 4, 4);
        String a = card(0, 0, 0, 1);
        String b = card(0, 2, 0, 3);
        String c = card(1, 0, 3, 1);
        String d = card(1, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b));
        model.addSignalChain(2, false, List.of(d, c));
        java.nio.file.Files.write(out.resolve("modular-standard-2ports.scr"), export(null));

        // 2) Complex: карты не образуют сетку (3+1 модуля сверху, 2+2 снизу), 2 порта.
        java.nio.file.Files.createDirectories(dir.resolve("cx"));
        setUp(dir.resolve("cx"), 4, 4);
        a = card(0, 0, 1, 2);
        b = card(0, 3, 1, 3);
        c = card(2, 0, 3, 1);
        d = card(2, 2, 3, 3);
        model.addSignalChain(1, false, List.of(a, b));
        model.addSignalChain(2, false, List.of(c, d));
        java.nio.file.Files.write(out.resolve("modular-complex-2ports.scr"), export(null));
    }

    @Test
    void ordinaryScreenIsNotModularAndKeepsOldReasons(@TempDir Path dir) {
        setUp(dir, 2, 2);
        module.setReceiverCardSeparate(false);
        assertFalse(NovaLctScrWriter.isModularScreen(screen, model.getWorkspace()));
        assertNull(NovaLctScrWriter.modularOriginPx(screen, List.of(), model.getWorkspace()));
    }
}
