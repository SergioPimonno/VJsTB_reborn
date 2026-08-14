package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link NovaLctScrParser} не имел вообще никакого тестового покрытия (см.
 * NOVALCT_EXPORT.md, секция 10) — нет ни одного реального образца .scr для >2 экранов
 * или нестандартного (не 128×128) кабинета, поэтому здесь только round-trip через
 * {@link NovaLctScrWriter} (наш собственный, подтверждённый golden-тестами писатель),
 * НЕ подмена настоящей проверке реальной загрузкой. Фикстуры скопированы из
 * {@link NovaLctScrWriterTest}, чтобы использовать те же подтверждённые 128×128-сэмплы.
 *
 * <p>Два теста ниже намеренно фиксируют ИЗВЕСТНЫЕ ограничения парсера (не баги в
 * писателе): {@link NovaLctScrParser#ANCHOR} — фиксированная константа для 128×128
 * кабинета (парсер писался ДО того, как в {@code NovaLctScrWriter.buildAnchor} было
 * подтверждено, что эти байты — реальные пиксельные ширина/высота кабинета, см.
 * NOVALCT_EXPORT.md секция 4) — для любого другого разрешения парсер молча не найдёт
 * ни одной записи. И `fillMissingOrigin` не может восстановить происхождение цепочки
 * (0,0), если в файле нет вообще ни одной другой записи того же порта (тривиальный
 * 1×1 экран) — метод исключения работает только когда есть с чем сравнивать.
 */
class NovaLctScrParserTest {

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

    private CabinetType typeNonSquare200x100() {
        CabinetType ct = new CabinetType();
        ct.setName("Test 200x100");
        ct.setWidthMm(500);
        ct.setHeightMm(250);
        ct.setResolutionWidth(200);
        ct.setResolutionHeight(100);
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
    void roundTrip_4x3ComplexChain_recoversRowColAndSeqOrder(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 3, 4, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());

        // Тот же серпантин, что в NovaLctScrWriterTest#matchesRealSample_4x3ComplexChain --
        // (0,0) намеренно НЕ в начале/конце цепочки, чтобы упражнять и column-major запись,
        // и fillMissingOrigin (seq=2 -- единственная дыра, которую парсер обязан восстановить).
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

        byte[] bytes = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        NovaLctScrParser.ImportResult result = NovaLctScrParser.parse(bytes);

        assertEquals(1, result.screens().size());
        NovaLctScrParser.ImportedScreen parsed = result.screens().get(0);
        assertEquals(4, parsed.width, "ширина грида в кабинетах (cols)");
        assertEquals(3, parsed.height, "высота грида в кабинетах (rows)");

        Map<String, List<NovaLctScrParser.CabinetEntry>> chains = parsed.chainsByCardPort();
        assertEquals(1, chains.size(), "все 12 кабинетов расключены через один порт одной карты");
        List<NovaLctScrParser.CabinetEntry> chain = chains.values().iterator().next();
        assertEquals(12, chain.size(), "все 12 ячеек, включая восстановленную (0,0)");

        for (int seq = 0; seq < seqOrderColRow.length; seq++) {
            int expectedCol = seqOrderColRow[seq][0];
            int expectedRow = seqOrderColRow[seq][1];
            NovaLctScrParser.CabinetEntry entry = chain.get(seq);
            assertEquals(seq, entry.seq());
            assertEquals(expectedRow, entry.row(), "row для seq=" + seq);
            assertEquals(expectedCol, entry.col(), "col для seq=" + seq);
        }
    }

    @Test
    void roundTrip_1x1Trivial_originCannotBeRecoveredWithoutOtherRecords(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());
        CabinetInstance origin = screen.cabinetAt(0, 0);
        model.addSignalChain(1, false, List.of(origin.getId()));

        byte[] bytes = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        NovaLctScrParser.ImportResult result = NovaLctScrParser.parse(bytes);

        assertEquals(1, result.screens().size());
        NovaLctScrParser.ImportedScreen parsed = result.screens().get(0);
        assertEquals(1, parsed.width);
        assertEquals(1, parsed.height);
        // Известное ограничение (не регрессия): без других записей того же порта
        // fillMissingOrigin не может методом исключения восстановить card/port/seq
        // единственного кабинета -- он просто не появляется в результате.
        assertTrue(parsed.cabinets.isEmpty(),
                "single-cabinet screen: origin (0,0) остаётся невосстановимым -- см. class-javadoc");
    }

    @Test
    void nonSquare128Cabinet_anchorMismatch_yieldsNoRecords(@TempDir Path dir) {
        // ANCHOR в парсере -- {0x00, 0x80, 0x00, 0x80, 0x00, 0x01}, зашитая константа под
        // 128x128 (см. class-javadoc NovaLctScrParser и NOVALCT_EXPORT.md секция 4: якорь --
        // это реальные пиксельные ширина/высота кабинета, НЕ фиксированный маркер). Парсер
        // писался до этого уточнения и не был обновлён -- для кабинета 200x100 писатель
        // корректно кладёт якорь {0x00,0xc8,0x00,0x64,0x00,0x01}, который эта константа не
        // находит вообще. Результат -- экран без единой записи, а не ошибка: тихая потеря
        // данных для любого не-128x128 кабинета. Фиксируем это здесь, чтобы не потерять
        // при будущей доработке импорта -- см. RIGGING_CALC_NOTES.md-style hedge convention.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(typeNonSquare200x100());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());
        CabinetInstance c0 = screen.cabinetAt(0, 0);
        CabinetInstance c1 = screen.cabinetAt(0, 1);
        model.addSignalChain(1, false, List.of(c0.getId(), c1.getId()));

        byte[] bytes = NovaLctScrWriter.write(screen, scene, model.getWorkspace());

        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> NovaLctScrParser.parse(bytes),
                "без единой находимой записи-якоря parse() обязан бросить, а не молча вернуть пустой результат");
        assertTrue(ex.getMessage().contains("запись"), ex.getMessage());
    }
}
