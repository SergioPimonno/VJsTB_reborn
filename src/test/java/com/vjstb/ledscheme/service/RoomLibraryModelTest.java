package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.service.room.RoomLibraryAdapter;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Библиотека Планера пультовой в модели: личные и общие позиции («оборудование пультовой» и
 * «мебель/станки/мониторы»), приход и удаление с сервера через синхронизацию, личные габариты и —
 * главное — сохранность ссылок раскладок. Когда запись с сервера совпадает по имени с личной, личная
 * «продвигается» и теряет свой id; раскладки и личные габариты обязаны уехать на новый id, иначе
 * позиция тихо станет «неизвестной» (тот же класс бага, что уже ловили на Screen.cabinetTypeId).
 */
class RoomLibraryModelTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AppModel newModel(Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        return m;
    }

    private static RoomFurnitureType deck(String name) {
        RoomFurnitureType t = new RoomFurnitureType();
        t.setName(name);
        t.setKind(RoomFurnitureType.Kind.DECK);
        t.setRole(RoomFurnitureType.Role.TABLE);
        t.setWidthMm(2000.0);
        t.setDepthMm(1000.0);
        t.setHeightMm(400.0);
        t.setWeightKg(35.0);
        return t;
    }

    private static LibrarySyncClient.LibraryItemDto dto(String id, String kind, String name, Object payload,
                                                         boolean deleted) throws Exception {
        return new LibrarySyncClient.LibraryItemDto(id, kind, name, JSON.writeValueAsString(payload), 1, deleted);
    }

    @Test
    void personalItemsAreAddedEditedAndRemoved(@TempDir Path dir) {
        AppModel m = newModel(dir);
        RoomFurnitureType d = m.addRoomFurnitureType(deck("Станок 2×1"));
        assertThrows(IllegalStateException.class, () -> m.addRoomFurnitureType(deck("станок 2×1")),
                "имя уникально без учёта регистра");
        RoomFurnitureType edited = d.copy();
        edited.setHeightMm(200.0);
        m.updateRoomFurnitureType(edited);
        assertEquals(200.0, m.getRoomFurnitureTypes().get(0).getHeightMm());

        RoomEquipmentType e = new RoomEquipmentType();
        e.setName("Пульт X32");
        m.addRoomEquipmentType(e);
        assertEquals(1, m.getRoomEquipmentTypes().size());
        m.deleteRoomEquipmentType(e.getId());
        m.deleteRoomFurnitureType(d.getId());
        assertTrue(m.getRoomEquipmentTypes().isEmpty());
        assertTrue(m.getRoomFurnitureTypes().isEmpty());
    }

    @Test
    void sharedItemsCannotBeEditedLocally(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        RoomFurnitureType shared = deck("Станок общий");
        m.applyLibrarySyncItems(List.of(dto("srv-1", "ROOM_FURNITURE", shared.getName(), shared, false)));
        assertTrue(m.isSharedRoomFurnitureType("srv-1"));
        RoomFurnitureType edit = shared.copy();
        edit.setId("srv-1");
        assertThrows(IllegalArgumentException.class, () -> m.updateRoomFurnitureType(edit),
                "общая позиция правится только администратором — личные габариты вместо этого");
    }

    @Test
    void serverItemsArriveAndAreRemovedBySync(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        RoomEquipmentType eq = new RoomEquipmentType();
        eq.setName("ИБП 3 кВА");
        RoomFurnitureType fu = deck("Стол 160");
        m.applyLibrarySyncItems(List.of(dto("e1", "ROOM_EQUIPMENT", eq.getName(), eq, false),
                dto("f1", "ROOM_FURNITURE", fu.getName(), fu, false)));
        assertEquals("e1", m.getRoomEquipmentTypes().get(0).getId(), "id записи на сервере становится локальным");
        assertEquals("f1", m.getRoomFurnitureTypes().get(0).getId());
        assertNotNull(m.getWorkspace().roomFurnitureTypeById("f1"));

        m.applyLibrarySyncItems(List.of(dto("e1", "ROOM_EQUIPMENT", eq.getName(), eq, true),
                dto("f1", "ROOM_FURNITURE", fu.getName(), fu, true)));
        assertTrue(m.getRoomEquipmentTypes().isEmpty());
        assertTrue(m.getRoomFurnitureTypes().isEmpty());
    }

    @Test
    void promotingAPersonalItemKeepsLayoutsAndPersonalDimensionsPointingAtIt(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        RoomFurnitureType mine = m.addRoomFurnitureType(deck("Станок 2×1"));
        String oldId = mine.getId();
        ControlRoomLayout layout = m.addControlRoomLayout("FOH");
        RoomFurniture placed = new RoomFurniture();
        placed.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, oldId));
        layout.getFurniture().add(placed);
        PhysicalSpec own = new PhysicalSpec();
        own.setWidthMm(1.0);
        m.setPhysicalOverride(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, oldId), own);

        RoomFurnitureType fromServer = deck("станок 2×1");
        m.applyLibrarySyncItems(List.of(dto("srv-9", "ROOM_FURNITURE", fromServer.getName(), fromServer, false)));

        assertEquals(1, m.getRoomFurnitureTypes().size(), "личный двойник растворился в общем");
        assertEquals("srv-9", placed.getRef().getId(), "позиция раскладки переехала на новый id");
        assertNull(m.getPhysicalOverrides().get("ROOM_FURNITURE:" + oldId));
        assertNotNull(m.getPhysicalOverrides().get("ROOM_FURNITURE:srv-9"));
    }

    @Test
    void personalDimensionsFillGapsButNeverOverrideCompleteSharedData(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        RoomEquipmentType bare = new RoomEquipmentType();
        bare.setName("Новый пульт");
        m.applyLibrarySyncItems(List.of(dto("e7", "ROOM_EQUIPMENT", bare.getName(), bare, false)));
        RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, "e7");
        assertFalse(RoomLibraryAdapter.of(m).resolve(ref).complete(), "в библиотеке габаритов нет");

        PhysicalSpec own = new PhysicalSpec();
        own.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        own.setWidthMm(500.0);
        own.setHeightMm(100.0);
        own.setDepthMm(300.0);
        m.setPhysicalOverride(ref, own);
        RoomLibraryAdapter withOwn = RoomLibraryAdapter.of(m);
        RoomItemSpec s = withOwn.resolve(ref);
        assertTrue(s.complete());
        assertEquals(50, s.widthCm(), 1e-9);
        assertTrue(withOwn.usesPersonalPhysical(ref));

        // администратор заполнил позицию на сервере — общие данные главнее личных
        PhysicalSpec shared = new PhysicalSpec();
        shared.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        shared.setWidthMm(900.0);
        shared.setHeightMm(100.0);
        shared.setDepthMm(300.0);
        bare.setPhysical(shared);
        m.applyLibrarySyncItems(List.of(dto("e7", "ROOM_EQUIPMENT", bare.getName(), bare, false)));
        RoomLibraryAdapter after = RoomLibraryAdapter.of(m);
        assertEquals(90, after.resolve(ref).widthCm(), 1e-9);
        assertFalse(after.usesPersonalPhysical(ref));
    }

    @Test
    void personalLibraryAndDimensionsSurviveARestart(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        RoomFurnitureType d = m.addRoomFurnitureType(deck("Станок 2×1"));
        PhysicalSpec own = new PhysicalSpec();
        own.setWeightKg(12.0);
        RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.CASE, "any-case");
        m.setPhysicalOverride(ref, own);

        AppModel again = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        assertEquals(d.getId(), again.getRoomFurnitureTypes().get(0).getId());
        assertEquals(12.0, again.getPhysicalOverrides().get(ref.key()).getWeightKg());
    }
}
