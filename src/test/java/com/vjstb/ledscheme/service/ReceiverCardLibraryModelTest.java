package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Library;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.store.LibraryStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Библиотека модульных экранов в модели клиента (трек A плана docs/receiver-cards): приёмные карты и блоки
 * питания — личные и общие записи, приход и удаление с сервера через синхронизацию, сохранение между
 * запусками и флаг «Приёмная карта отдельно» у кабинета. Главное, что здесь стережётся: необязательные
 * числа обязаны переживать запись на диск и синк как {@code null} («не указано, не проверять»), а не
 * превращаться в ноль.
 */
class ReceiverCardLibraryModelTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AppModel newModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
    }

    private static ReceiverCardType card(String name) {
        ReceiverCardType t = new ReceiverCardType();
        t.setName(name);
        t.setVendor("NovaStar");
        t.setModuleOutputs(8);
        t.setRibbonLengthMm(600.0);
        return t;
    }

    private static PowerSupplyType psu(String name) {
        PowerSupplyType t = new PowerSupplyType();
        t.setName(name);
        t.setOutputs(8);
        t.setRatedPowerW(300.0);
        t.setCardOutput(true);
        return t;
    }

    private static LibrarySyncClient.LibraryItemDto dto(String id, String kind, String name, Object payload,
                                                         boolean deleted) throws Exception {
        return new LibrarySyncClient.LibraryItemDto(id, kind, name, JSON.writeValueAsString(payload), 1, deleted);
    }

    @Test
    void personalCardsAndSuppliesAreAddedEditedAndRemoved(@TempDir Path dir) {
        AppModel m = newModel(dir);
        ReceiverCardType c = m.addReceiverCardType(card("A8s"));
        assertThrows(IllegalStateException.class, () -> m.addReceiverCardType(card("a8S")),
                "имя уникально без учёта регистра");
        ReceiverCardType edited = c.copy();
        edited.setModuleOutputs(16);
        edited.setRibbonLengthMm(null);
        m.updateReceiverCardType(edited);
        assertEquals(16, m.getReceiverCardTypes().get(0).getModuleOutputs());
        assertNull(m.getReceiverCardTypes().get(0).getRibbonLengthMm(), "очищенное число снова «не указано»");

        PowerSupplyType p = m.addPowerSupplyType(psu("PSU-8"));
        assertThrows(IllegalStateException.class, () -> m.addPowerSupplyType(psu("psu-8")));
        PowerSupplyType pe = p.copy();
        pe.setCardOutput(false);
        m.updatePowerSupplyType(pe);
        assertFalse(m.getPowerSupplyTypes().get(0).isCardOutput());

        m.deleteReceiverCardType(c.getId());
        m.deletePowerSupplyType(p.getId());
        assertTrue(m.getReceiverCardTypes().isEmpty());
        assertTrue(m.getPowerSupplyTypes().isEmpty());
    }

    @Test
    void sharedItemsAreMarkedAndCannotBeEditedLocally(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        ReceiverCardType shared = card("Общая карта");
        m.applyLibrarySyncItems(List.of(dto("rc-1", "RECEIVER_CARD", shared.getName(), shared, false)));
        assertTrue(m.isSharedReceiverCardType("rc-1"));
        ReceiverCardType edit = shared.copy();
        edit.setId("rc-1");
        assertThrows(IllegalArgumentException.class, () -> m.updateReceiverCardType(edit),
                "общая запись правится только администратором");
        assertFalse(m.isSharedReceiverCardType(null));
    }

    @Test
    void serverItemsArriveAndAreRemovedBySync(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        ReceiverCardType rc = card("MRV");
        rc.setMaxPixels(null);
        PowerSupplyType ps = psu("БП 5 В");
        ps.setCableLengthMm(null);
        LibrarySyncClient.LibraryItemDto rcIn = dto("rc1", "RECEIVER_CARD", rc.getName(), rc, false);
        LibrarySyncClient.LibraryItemDto psIn = dto("ps1", "POWER_SUPPLY", ps.getName(), ps, false);

        AppModel.LibrarySyncSummary first = m.applyLibrarySyncItems(List.of(rcIn, psIn));
        assertEquals(2, first.added());
        assertEquals("rc1", m.getReceiverCardTypes().get(0).getId(), "id записи на сервере становится локальным");
        assertEquals("ps1", m.getPowerSupplyTypes().get(0).getId());
        assertNotNull(m.getWorkspace().receiverCardTypeById("rc1"));
        assertNotNull(m.getWorkspace().powerSupplyTypeById("ps1"));
        assertNull(m.getReceiverCardTypes().get(0).getMaxPixels(), "не указано остаётся не указанным");
        assertNull(m.getPowerSupplyTypes().get(0).getCableLengthMm());
        assertEquals(8, m.getPowerSupplyTypes().get(0).getOutputs());
        assertTrue(m.getPowerSupplyTypes().get(0).isCardOutput());

        AppModel.LibrarySyncSummary again = m.applyLibrarySyncItems(List.of(rcIn));
        assertEquals(0, again.added());
        assertEquals(1, again.updated());

        AppModel.LibrarySyncSummary gone = m.applyLibrarySyncItems(List.of(
                dto("rc1", "RECEIVER_CARD", rc.getName(), rc, true),
                dto("ps1", "POWER_SUPPLY", ps.getName(), ps, true)));
        assertEquals(2, gone.deleted());
        assertTrue(m.getReceiverCardTypes().isEmpty());
        assertTrue(m.getPowerSupplyTypes().isEmpty());
    }

    @Test
    void personalDoubleOfAServerItemIsPromotedNotDuplicated(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        m.addReceiverCardType(card("A8s"));
        ReceiverCardType fromServer = card("a8s");
        m.applyLibrarySyncItems(List.of(dto("srv-9", "RECEIVER_CARD", fromServer.getName(), fromServer, false)));
        assertEquals(1, m.getReceiverCardTypes().size(), "личный двойник растворился в общем");
        assertEquals("srv-9", m.getReceiverCardTypes().get(0).getId());
    }

    @Test
    void personalLibrarySurvivesARestartWithNullsAndPhysical(@TempDir Path dir) {
        AppModel m = newModel(dir);
        ReceiverCardType c = card("Карта");
        c.setMaxWidthPx(null);
        PhysicalSpec phys = new PhysicalSpec();
        phys.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        phys.setWidthMm(80.0);
        c.setPhysical(phys);
        m.addReceiverCardType(c);
        PowerSupplyType p = psu("БП");
        p.setRatedPowerW(null);
        m.addPowerSupplyType(p);

        AppModel again = newModel(dir);
        ReceiverCardType backCard = again.getReceiverCardTypes().get(0);
        assertEquals(c.getId(), backCard.getId());
        assertEquals(8, backCard.getModuleOutputs());
        assertNull(backCard.getMaxWidthPx());
        assertEquals(80.0, backCard.getPhysical().getWidthMm());
        PowerSupplyType backPsu = again.getPowerSupplyTypes().get(0);
        assertNull(backPsu.getRatedPowerW());
        assertTrue(backPsu.isCardOutput());
    }

    @Test
    void oldLibraryFileWithoutTheNewFieldsLoadsWithEmptyLists(@TempDir Path dir) throws Exception {
        File file = new File(dir.toFile(), "library.json");
        java.nio.file.Files.writeString(file.toPath(), "{\"cabinetTypes\":[{\"name\":\"Старый\"}]}");
        Library lib = new LibraryStore(file).load();
        assertEquals(1, lib.getCabinetTypes().size());
        assertTrue(lib.getReceiverCardTypes().isEmpty());
        assertTrue(lib.getPowerSupplyTypes().isEmpty());
        assertTrue(lib.getSharedReceiverCardTypes().isEmpty());
        assertTrue(lib.getSharedPowerSupplyTypes().isEmpty());
        assertFalse(lib.getCabinetTypes().get(0).isReceiverCardSeparate(), "без поля — обычный кабинет");
    }

    @Test
    void moduleFlagOfACabinetTypeIsKeptByCopyEditAndRestart(@TempDir Path dir) {
        AppModel m = newModel(dir);
        CabinetType module = new CabinetType();
        module.setName("Модуль P2.5");
        module.setReceiverCardSeparate(true);
        m.addCabinetType(module);
        assertTrue(module.copy().isReceiverCardSeparate());

        CabinetType edited = module.copy();
        edited.setPowerConsumptionW(40.0);
        m.updateCabinetType(edited);
        assertTrue(m.getCabinetTypes().stream().filter(t -> t.getName().equals("Модуль P2.5")).findFirst().get()
                .isReceiverCardSeparate(), "правка кабинета не сбрасывает флаг");

        AppModel again = newModel(dir);
        assertTrue(again.getCabinetTypes().stream().filter(t -> t.getName().equals("Модуль P2.5")).findFirst().get()
                .isReceiverCardSeparate());
    }
}
