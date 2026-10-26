package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Целостность карт, блоков и цепочек при всём, что убирает модуль или звено (docs/receiver-cards/PLAN.md, раздел 5):
 * скрытие модуля, смена типа ячейки, смена сетки, снятие флага у типа, удаление экрана, удаление/замена карты и блока,
 * удаление и продвижение типов библиотеки. Общий принцип: в цепочке не должно остаться id несуществующего звена,
 * а в карте/блоке — ссылки на ячейку, которая перестала быть модулем. Звенья кладутся в цепочки напрямую в данных
 * (редактор цепочек на карты переводит трек D).
 */
class ModularScreenIntegrityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AppModel model;
    private CabinetType moduleType;
    private Screen screen;
    private Scene scene;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;

    private void setUp(Path dir, int rows, int cols) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        moduleType = new CabinetType();
        moduleType.setName("Модуль");
        moduleType.setWidthMm(250);
        moduleType.setHeightMm(250);
        moduleType.setResolutionWidth(64);
        moduleType.setResolutionHeight(64);
        moduleType.setReceiverCardSeparate(true);
        model.addCabinetType(moduleType);
        cardType = model.addReceiverCardType(typeCard("К8"));
        psuType = model.addPowerSupplyType(typePsu("БП8"));
        Project p = model.addProject("П");
        model.selectProject(p);
        scene = model.addScene("С");
        model.selectScene(scene);
        screen = model.addScreen("Э", moduleType.getId(), rows, cols, 0, 0);
        model.selectScreen(screen);
    }

    private static ReceiverCardType typeCard(String name) {
        ReceiverCardType t = new ReceiverCardType();
        t.setName(name);
        t.setModuleOutputs(8);
        return t;
    }

    private static PowerSupplyType typePsu(String name) {
        PowerSupplyType t = new PowerSupplyType();
        t.setName(name);
        t.setOutputs(8);
        return t;
    }

    private String id(int r, int c) {
        return screen.cabinetAt(r, c).getId();
    }

    private void chainUnits(String... ids) {
        scene.getSignalChains().add(new SignalChain(1, false, List.of(ids)));
        scene.getPowerChains().add(new PowerChain(1, List.of(ids)));
    }

    @Test
    void hidingTheOnlyModuleOfACardRemovesTheCardAndItsLinkInTheChain(@TempDir Path dir) {
        setUp(dir, 1, 3);
        model.assignReceiverCardsManual(rect(0, 0, 0, 0), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 1, 0, 2), cardType.getId());
        String single = screen.getReceiverCards().get(0).getId();
        String pair = screen.getReceiverCards().get(1).getId();
        chainUnits(single, pair);

        model.toggleCabinetHidden(id(0, 0));

        assertEquals(1, screen.getReceiverCards().size());
        assertEquals(pair, screen.getReceiverCards().get(0).getId());
        assertEquals(List.of(pair), scene.getSignalChains().get(0).getCabinetInstanceIds());
        assertEquals(List.of(pair), scene.getPowerChains().get(0).getCabinetInstanceIds());
    }

    @Test
    void hidingOneModuleOfABlockKeepsTheBlockWithTheRest(@TempDir Path dir) {
        setUp(dir, 1, 3);
        model.assignPowerSuppliesManual(rect(0, 0, 0, 2), psuType.getId());
        String block = screen.getPowerSupplies().get(0).getId();
        screen.getPowerSupplies().get(0).setHostModuleId(id(0, 0));
        chainUnits(block);

        model.toggleCabinetHidden(id(0, 0));

        assertEquals(List.of(id(0, 1), id(0, 2)), screen.getPowerSupplies().get(0).getModuleIds());
        assertNull(screen.getPowerSupplies().get(0).getHostModuleId(), "место ушло вместе с модулем — снова авто");
        assertEquals(List.of(block), scene.getPowerChains().get(0).getCabinetInstanceIds());

        model.toggleCabinetHidden(id(0, 1));
        model.toggleCabinetHidden(id(0, 2));
        assertTrue(screen.getPowerSupplies().isEmpty());
        assertTrue(scene.getPowerChains().isEmpty(), "опустевшая цепочка удалена, как при скрытии кабинета");
    }

    @Test
    void hidingAModuleDropsItFromTheManualOrder(@TempDir Path dir) {
        setUp(dir, 1, 3);
        model.assignReceiverCardsManual(rect(0, 0, 0, 2), cardType.getId());
        String card = screen.getReceiverCards().get(0).getId();
        model.setReceiverCardManualOrder(card, List.of(id(0, 2), id(0, 0), id(0, 1)));

        model.toggleCabinetHidden(id(0, 0));

        assertEquals(List.of(id(0, 2), id(0, 1)), screen.getReceiverCards().get(0).getManualPortOrder());
    }

    @Test
    void changingACellToAnOrdinaryTypeTakesItOutOfCardsAndBlocks(@TempDir Path dir) {
        setUp(dir, 1, 2);
        CabinetType ordinary = new CabinetType();
        ordinary.setName("Кабинет");
        model.addCabinetType(ordinary);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 0, 1), psuType.getId());

        model.setCabinetTypeOverride(id(0, 1), ordinary.getId());

        assertEquals(List.of(id(0, 0)), screen.getReceiverCards().get(0).getModuleIds());
        assertEquals(List.of(id(0, 0)), screen.getPowerSupplies().get(0).getModuleIds());
        model.undo();
        assertEquals(2, screen.getReceiverCards().get(0).getModuleIds().size(), "отмена возвращает модуль в карту");
    }

    @Test
    void shrinkingTheGridRemovesLostModulesFromCards(@TempDir Path dir) {
        setUp(dir, 1, 4);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 0, 3), cardType.getId());
        String right = screen.getReceiverCards().get(1).getId();
        chainUnits(right);

        model.updateScreenGrid(screen, "Э", moduleType.getId(), 1, 2);

        assertEquals(1, screen.getReceiverCards().size());
        assertEquals(2, screen.getReceiverCards().get(0).getModuleIds().size());
        assertTrue(scene.getSignalChains().isEmpty(), "карта за краем ушла, цепочка опустела");
    }

    @Test
    void deletingTheScreenRemovesItsCardsAndBlocksFromTheChains(@TempDir Path dir) {
        setUp(dir, 1, 2);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 0, 1), psuType.getId());
        chainUnits(screen.getReceiverCards().get(0).getId(), screen.getPowerSupplies().get(0).getId());

        model.deleteScreen(screen);

        assertTrue(scene.getSignalChains().isEmpty());
        assertTrue(scene.getPowerChains().isEmpty());
        model.undo();
        assertEquals(1, scene.getSignalChains().size(), "структурная отмена возвращает и экран, и цепочки");
        assertEquals(2, scene.getSignalChains().get(0).getCabinetInstanceIds().size());
    }

    @Test
    void replacingOrRemovingACardPullsItsLinkOutOfTheChain(@TempDir Path dir) {
        setUp(dir, 1, 4);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 0, 3), cardType.getId());
        String a = screen.getReceiverCards().get(0).getId();
        String b = screen.getReceiverCards().get(1).getId();
        chainUnits(a, b);

        model.assignReceiverCardsManual(rect(0, 1, 0, 1), cardType.getId()); // задевает A
        assertEquals(List.of(b), scene.getSignalChains().get(0).getCabinetInstanceIds());

        model.removeReceiverCard(b);
        assertTrue(scene.getSignalChains().isEmpty());
    }

    @Test
    void clearingTheModuleFlagAsksFirstAndThenCleansEveryScreen(@TempDir Path dir) {
        setUp(dir, 1, 3);
        model.assignReceiverCardsManual(rect(0, 0, 0, 2), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 0, 1), psuType.getId());
        model.assignPowerSuppliesManual(rect(0, 2, 0, 2), psuType.getId());
        chainUnits(screen.getReceiverCards().get(0).getId(), screen.getPowerSupplies().get(0).getId());

        ModuleIntegrity.Impact impact = model.moduleFlagRemovalImpact(moduleType.getId());
        assertEquals(1, impact.cardsRemoved());
        assertEquals(2, impact.suppliesRemoved());
        assertEquals(0, impact.cardsShrunk());
        assertTrue(impact.any());
        assertFalse(model.moduleFlagRemovalImpact("другой-тип").any());
        assertEquals(1, screen.getReceiverCards().size(), "подсчёт ничего не меняет");

        CabinetType edited = moduleType.copy();
        edited.setReceiverCardSeparate(false);
        model.updateCabinetType(edited);

        assertTrue(screen.getReceiverCards().isEmpty());
        assertTrue(screen.getPowerSupplies().isEmpty());
        assertTrue(scene.getSignalChains().isEmpty());
        assertTrue(scene.getPowerChains().isEmpty());
    }

    @Test
    void clearingTheFlagOfOneOfTwoTypesLeavesTheCardWithTheOtherModules(@TempDir Path dir) {
        setUp(dir, 1, 3);
        CabinetType other = new CabinetType();
        other.setName("Модуль B");
        other.setReceiverCardSeparate(true);
        model.addCabinetType(other);
        model.setCabinetTypeOverride(id(0, 2), other.getId());
        model.assignReceiverCardsManual(rect(0, 0, 0, 2), cardType.getId());

        ModuleIntegrity.Impact impact = model.moduleFlagRemovalImpact(other.getId());
        assertEquals(0, impact.cardsRemoved());
        assertEquals(1, impact.cardsShrunk());

        CabinetType edited = other.copy();
        edited.setReceiverCardSeparate(false);
        model.updateCabinetType(edited);
        assertEquals(2, screen.getReceiverCards().get(0).getModuleIds().size());
    }

    @Test
    void aTypeUsedByACardOrBlockCannotBeDeleted(@TempDir Path dir) {
        setUp(dir, 1, 2);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 0, 1), psuType.getId());

        assertThrows(IllegalStateException.class, () -> model.deleteReceiverCardType(cardType.getId()));
        assertThrows(IllegalStateException.class, () -> model.deletePowerSupplyType(psuType.getId()));
        assertTrue(model.isReceiverCardTypeReferenced(cardType.getId()));

        model.removeReceiverCard(screen.getReceiverCards().get(0).getId());
        model.removePowerSupply(screen.getPowerSupplies().get(0).getId());
        model.deleteReceiverCardType(cardType.getId());
        model.deletePowerSupplyType(psuType.getId());
        assertTrue(model.getReceiverCardTypes().isEmpty());
    }

    @Test
    void promotingAPersonalTypeToTheSharedLibraryMovesTheReferences(@TempDir Path dir) throws Exception {
        setUp(dir, 1, 2);
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
        model.assignPowerSuppliesAuto(rect(0, 0, 0, 1), psuType.getId());

        // сервер присылает записи с теми же именами: личные «растворяются» в общих, ссылки переезжают на id сервера
        ReceiverCardType sharedCard = typeCard("к8");
        PowerSupplyType sharedPsu = typePsu("бп8");
        model.applyLibrarySyncItems(List.of(
                new LibrarySyncClient.LibraryItemDto("srv-card", "RECEIVER_CARD", "к8",
                        JSON.writeValueAsString(sharedCard), 1, false),
                new LibrarySyncClient.LibraryItemDto("srv-psu", "POWER_SUPPLY", "бп8",
                        JSON.writeValueAsString(sharedPsu), 1, false)));

        assertEquals("srv-card", screen.getReceiverCards().get(0).getReceiverCardTypeId());
        assertEquals("srv-psu", screen.getPowerSupplies().get(0).getPowerSupplyTypeId());
        assertEquals("srv-psu", screen.getPowerAutoAreas().get(0).getPowerSupplyTypeId(),
                "автообласть помнит тип — он тоже переехал");
    }

    @Test
    void sharedTypeDeletedOnTheServerStaysWhileAScreenUsesIt(@TempDir Path dir) throws Exception {
        setUp(dir, 1, 2);
        ReceiverCardType shared = typeCard("Общая");
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto("srv-1", "RECEIVER_CARD", "Общая",
                JSON.writeValueAsString(shared), 1, false)));
        model.assignReceiverCardsManual(rect(0, 0, 0, 1), "srv-1");

        AppModel.LibrarySyncSummary gone = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "RECEIVER_CARD", "Общая", JSON.writeValueAsString(shared), 2, true)));

        assertEquals(0, gone.deleted());
        assertEquals(1, gone.skippedDeleted(), "как у типа кабинета: фоновый синк не обнуляет живой экран");
        assertTrue(model.getWorkspace().receiverCardTypeById("srv-1") != null);
    }

    @Test
    void ordinaryHidingOfNonModulesDoesNotTouchCards(@TempDir Path dir) {
        setUp(dir, 1, 3);
        CabinetType ordinary = new CabinetType();
        ordinary.setName("Кабинет");
        model.addCabinetType(ordinary);
        model.setCabinetTypeOverride(id(0, 2), ordinary.getId());
        model.assignReceiverCardsManual(rect(0, 0, 0, 2), cardType.getId());
        ReceiverCardInstance card = screen.getReceiverCards().get(0);

        model.toggleCabinetHidden(id(0, 2));

        assertEquals(2, card.getModuleIds().size());
    }
}
