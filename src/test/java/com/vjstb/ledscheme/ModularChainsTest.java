package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ChainLinks;
import com.vjstb.ledscheme.service.ChainUnits;
import com.vjstb.ledscheme.service.NovaLctControllerResolver;
import com.vjstb.ledscheme.service.NovaLctScrWriter;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Цепочки расключения из приёмных карт и блоков питания (docs/receiver-cards/PLAN.md, трек D, D24–D26): добавление,
 * пиксели карты, ватты блока, клик по модулю → звено, модуль без карты/блока, смешанный экран, занятость, NovaLCT.
 * Без Swing: логика контроллера построения цепочки и модели.
 */
class ModularChainsTest {

    private AppModel model;
    private Scene scene;
    private Screen screen;
    private CabinetType module;
    private CabinetType ordinary;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;

    private static GridRect rect(int r0, int c0, int r1, int c1) {
        return new GridRect(r0, c0, r1, c1);
    }

    /** Экран 2×4 из модулей 250 мм, 64×64 px, 30 Вт; обычный кабинет — 128×128 px, 100 Вт. */
    private void setUp(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setPowerConsumptionW(30);
        module.setReceiverCardSeparate(true);
        model.addCabinetType(module);
        ordinary = new CabinetType();
        ordinary.setName("Кабинет");
        ordinary.setWidthMm(250);
        ordinary.setHeightMm(250);
        ordinary.setResolutionWidth(128);
        ordinary.setResolutionHeight(128);
        ordinary.setPowerConsumptionW(100);
        model.addCabinetType(ordinary);
        cardType = new ReceiverCardType();
        cardType.setName("К8");
        cardType.setModuleOutputs(8);
        model.addReceiverCardType(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("БП8");
        psuType.setOutputs(8);
        psuType.setRatedPowerW(300.0);
        model.addPowerSupplyType(psuType);
        model.selectProject(model.addProject("Проект"));
        scene = model.addScene("Сцена");
        model.selectScene(scene);
        screen = model.addScreen("A", module.getId(), 2, 4, 0, 0);
        model.selectScreen(screen);
    }

    private String cardAt(int row, int col) {
        String id = screen.cabinetAt(row, col).getId();
        return ChainLinks.unitContaining(screen, id, ChainUnits.Mode.SIGNAL);
    }

    private String supplyAt(int row, int col) {
        String id = screen.cabinetAt(row, col).getId();
        return ChainLinks.unitContaining(screen, id, ChainUnits.Mode.POWER);
    }

    // ------------------------------------------------------------------ добавление, пиксели, ватты

    @Test
    void signalChainTakesCardIdsAndCountsPixelsAsSumOfModules(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        String a = cardAt(0, 0);
        String b = cardAt(0, 2);

        model.addSignalChain(1, false, List.of(a, b));

        SignalChain chain = scene.getSignalChains().get(0);
        assertEquals(List.of(a, b), chain.getCabinetInstanceIds());
        assertEquals(2 * 4 * 64 * 64, model.signalChainPixelLoad(scene, chain), "пиксели карты — сумма модулей");
        assertEquals("2 карты", ChainLinks.countsText(scene, chain.getCabinetInstanceIds()));
        assertTrue(model.isCabinetWiredForSignal(a));
        assertTrue(model.isCabinetWiredForSignal(screen.cabinetAt(1, 1).getId()), "модуль занят, если занята его карта");
        assertEquals(1, model.signalChainsTouchingScreen(screen).size());
    }

    @Test
    void powerChainTakesSupplyIdsAndLoadsPhaseByRatedPower(@TempDir Path dir) {
        setUp(dir);
        model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId());
        model.assignPowerSuppliesManual(rect(0, 2, 1, 3), psuType.getId());
        String a = supplyAt(0, 0);
        String b = supplyAt(1, 3);

        model.addPowerChain(2, List.of(a, b));

        PowerChain chain = scene.getPowerChains().get(0);
        assertEquals(2 * 300.0, model.powerChainLoadWatts(scene, chain), "нагрузка фазы — номинал блоков (D22)");
        assertEquals("2 блока", ChainLinks.countsText(scene, chain.getCabinetInstanceIds()));
        for (CabinetInstance c : screen.getCabinets()) {
            assertEquals(2, c.getPhase(), "фаза ставится на все модули блока");
        }
        assertTrue(model.isCabinetWiredForPower(screen.cabinetAt(0, 1).getId()));
    }

    @Test
    void unitWithoutRatedPowerFallsBackToModuleSum(@TempDir Path dir) {
        setUp(dir);
        psuType.setRatedPowerW(null);
        model.assignPowerSuppliesManual(rect(0, 0, 0, 1), psuType.getId());
        model.addPowerChain(1, List.of(supplyAt(0, 0)));
        assertEquals(60.0, model.powerChainLoadWatts(scene, scene.getPowerChains().get(0)));
    }

    // ------------------------------------------------------------------ валидация, D26

    @Test
    void moduleOrForeignUnitIsRejectedAsChainLink(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId());
        String moduleId = screen.cabinetAt(0, 0).getId();

        assertThrows(IllegalArgumentException.class, () -> model.addSignalChain(1, false, List.of(moduleId)));
        assertThrows(IllegalArgumentException.class, () -> model.addPowerChain(1, List.of(moduleId)));
        assertThrows(IllegalArgumentException.class, () -> model.addSignalChain(1, false, List.of(supplyAt(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> model.addPowerChain(1, List.of(cardAt(0, 0))));
        assertTrue(scene.getSignalChains().isEmpty());
        assertTrue(scene.getPowerChains().isEmpty());
    }

    // ------------------------------------------------------------------ клик по модулю → звено

    @Test
    void clickOnModuleBuildsChainOfItsCard(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        String a = cardAt(0, 0);
        String b = cardAt(0, 2);
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        List<ChainUnits.Mode> hints = new ArrayList<>();
        ctrl.setUnitMode(ChainUnits.Mode.SIGNAL, hints::add);
        ctrl.setStarter(first -> model.isCabinetWiredForSignal(first) ? null : ids -> model.addSignalChain(1, false, ids));
        ctrl.setOccupancyCheck(model::isCabinetWiredForSignal);

        ctrl.cabinetClicked(screen.cabinetAt(1, 1).getId());
        ctrl.cabinetClicked(screen.cabinetAt(0, 0).getId());
        ctrl.cabinetClicked(screen.cabinetAt(0, 3).getId());
        // повторный проход по модулям уже добавленной карты звено не дублирует
        ctrl.cabinetClicked(screen.cabinetAt(0, 2).getId());

        assertEquals(List.of(a, b), ctrl.activeChainCabIds());
        assertTrue(hints.isEmpty());
        ctrl.finish();
        assertEquals(List.of(a, b), scene.getSignalChains().get(0).getCabinetInstanceIds());
    }

    @Test
    void moduleWithoutCardIsNotAddedAndRaisesHint(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        List<ChainUnits.Mode> hints = new ArrayList<>();
        ctrl.setUnitMode(ChainUnits.Mode.SIGNAL, hints::add);
        ctrl.setStarter(first -> ids -> model.addSignalChain(1, false, ids));

        ctrl.cabinetClicked(screen.cabinetAt(0, 3).getId());

        assertFalse(ctrl.isChainBuilding(), "клик по модулю без карты цепочку не начинает");
        assertEquals(List.of(ChainUnits.Mode.SIGNAL), hints);

        ctrl.cabinetClicked(screen.cabinetAt(0, 0).getId());
        ctrl.cabinetClicked(screen.cabinetAt(0, 3).getId());
        assertEquals(List.of(cardAt(0, 0)), ctrl.activeChainCabIds());
        assertEquals(2, hints.size());
    }

    @Test
    void powerClicksUseSuppliesNotCards(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 1, 3), psuType.getId());
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        ctrl.setUnitMode(ChainUnits.Mode.POWER, m -> { });
        ctrl.setStarter(first -> ids -> model.addPowerChain(1, ids));

        ctrl.cabinetClicked(screen.cabinetAt(1, 2).getId());
        assertEquals(List.of(supplyAt(1, 2)), ctrl.activeChainCabIds());
        ctrl.finish();
        assertEquals(1, scene.getPowerChains().size());
    }

    @Test
    void quickConnectAreaCollapsesCellsIntoUnitsInOrder(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        ctrl.setUnitMode(ChainUnits.Mode.SIGNAL, m -> { });
        ctrl.setStarter(first -> ids -> model.addSignalChain(2, false, ids));

        List<String> cells = new ArrayList<>();
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 2; r++) {
                cells.add(screen.cabinetAt(r, c).getId());
            }
        }
        assertNull(ctrl.buildAndCommit(cells));

        assertEquals(List.of(cardAt(0, 0), cardAt(0, 2)), scene.getSignalChains().get(0).getCabinetInstanceIds());
    }

    @Test
    void ordinaryScreenKeepsCellsAsLinks(@TempDir Path dir) {
        setUp(dir);
        CabinetType plain = new CabinetType();
        plain.setName("Обычный");
        plain.setWidthMm(500);
        plain.setHeightMm(500);
        plain.setResolutionWidth(128);
        plain.setResolutionHeight(128);
        model.addCabinetType(plain);
        Screen flat = model.addScreen("B", plain.getId(), 1, 3, 0, 0);
        model.selectScreen(flat);
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        ctrl.setUnitMode(ChainUnits.Mode.SIGNAL, m -> fail("подсказка на немодульном экране"));
        ctrl.setStarter(first -> ids -> model.addSignalChain(1, false, ids));

        ctrl.cabinetClicked(flat.cabinetAt(0, 0).getId());
        ctrl.cabinetClicked(flat.cabinetAt(0, 1).getId());
        ctrl.finish();

        assertEquals(List.of(flat.cabinetAt(0, 0).getId(), flat.cabinetAt(0, 1).getId()),
                scene.getSignalChains().get(0).getCabinetInstanceIds());
        assertEquals("2 каб.", ChainLinks.countsText(scene, scene.getSignalChains().get(0).getCabinetInstanceIds()));
    }

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    // ------------------------------------------------------------------ смешанный экран

    @Test
    void mixedScreenChainTakesCardAndOrdinaryCabinet(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        String plainId = screen.cabinetAt(0, 3).getId();
        model.setCabinetTypeOverride(plainId, ordinary.getId());
        String a = cardAt(0, 0);

        assertEquals(plainId, ChainLinks.unitIdFor(screen, model.getWorkspace(), ChainUnits.Mode.SIGNAL, plainId),
                "обычный кабинет смешанного экрана — сам себе звено");
        assertEquals(a, ChainLinks.unitIdFor(screen, model.getWorkspace(), ChainUnits.Mode.SIGNAL,
                screen.cabinetAt(1, 1).getId()));
        assertNull(ChainLinks.unitIdFor(screen, model.getWorkspace(), ChainUnits.Mode.SIGNAL,
                screen.cabinetAt(1, 2).getId()), "модуль без карты");

        model.addSignalChain(3, false, List.of(a, plainId));

        SignalChain chain = scene.getSignalChains().get(0);
        assertEquals(4 * 64 * 64 + 128 * 128, model.signalChainPixelLoad(scene, chain));
        assertEquals("1 карта, 1 каб.", ChainLinks.countsText(scene, chain.getCabinetInstanceIds()));
    }

    // ------------------------------------------------------------------ подписи состава

    @Test
    void countsTextDeclinesWords(@TempDir Path dir) {
        setUp(dir);
        assertEquals("карта", ChainLinks.plural(1, "карта", "карты", "карт"));
        assertEquals("карты", ChainLinks.plural(3, "карта", "карты", "карт"));
        assertEquals("карт", ChainLinks.plural(5, "карта", "карты", "карт"));
        assertEquals("карт", ChainLinks.plural(11, "карта", "карты", "карт"));
        assertEquals("карт", ChainLinks.plural(14, "карта", "карты", "карт"));
        assertEquals("карта", ChainLinks.plural(21, "карта", "карты", "карт"));
        assertEquals("блока", ChainLinks.plural(4, "блок", "блока", "блоков"));
        assertEquals("блоков", ChainLinks.plural(12, "блок", "блока", "блоков"));
        assertEquals("0 каб.", ChainLinks.countsText(scene, List.of()));
    }

    // ------------------------------------------------------------------ целостность и NovaLCT

    @Test
    void removingCardDropsItsLinkFromChain(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        String a = cardAt(0, 0);
        String b = cardAt(0, 2);
        model.addSignalChain(1, false, List.of(a, b));

        model.removeReceiverCard(a);

        assertEquals(List.of(b), scene.getSignalChains().get(0).getCabinetInstanceIds());
    }

    @Test
    void novaLctExportResolvesCardsAsLinksOfThePort(@TempDir Path dir) {
        setUp(dir);
        EquipmentPreset controllerType = model.addControllerPreset("Test controller", "", 20, 1000, 0, false,
                List.of(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20)))));
        ControllerInstance controller = model.addControllerToScreen(screen, controllerType.getId(), null);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        model.addSignalChain(1, false, List.of(cardAt(0, 2), cardAt(0, 0)));

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);

        assertEquals(2, recs.size(), "карта — одно звено порта");
        assertEquals(2, recs.get(0).col(), "порядок на порту — порядок цепочки");
        assertEquals(0, recs.get(0).seq());
        assertEquals(0, recs.get(1).col());
        assertEquals(1, recs.get(1).seq());
        assertFalse(NovaLctScrWriter.hasUnwiredCabinets(screen, scene, model.getWorkspace()),
                "остальные модули карты расключены вместе с ней");
        assertFalse(NovaLctScrWriter.hasUnwiredCabinetsForController(scene, controller, model));
        assertNotNull(NovaLctScrWriter.write(screen, scene, model.getWorkspace()));
    }

    @Test
    void novaLctUnwiredWarningStillFiresForCardOutsideChains(@TempDir Path dir) {
        setUp(dir);
        EquipmentPreset controllerType = model.addControllerPreset("Test controller", "", 20, 1000, 0, false,
                List.of(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20)))));
        ControllerInstance controller = model.addControllerToScreen(screen, controllerType.getId(), null);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        model.addSignalChain(1, false, List.of(cardAt(0, 0)));

        assertTrue(NovaLctScrWriter.hasUnwiredCabinets(screen, scene, model.getWorkspace()));
        assertTrue(NovaLctScrWriter.hasUnwiredCabinetsForController(scene, controller, model));
    }
}
