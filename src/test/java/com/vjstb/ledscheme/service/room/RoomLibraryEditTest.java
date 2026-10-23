package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Указать габариты» и «Предложить на сервер» в Планнере: куда попадают числа пользователя и что
 * уходит на сервер. Главные гарантии: локальная запись общей позиции не меняется (её правит только
 * администратор), предложение — это копия записи с габаритами, личная позиция пультовой правится сама,
 * а предлагать неполный блок нельзя.
 */
class RoomLibraryEditTest {

    private static AppModel newModel(Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        return m;
    }

    private static PhysicalSpec loose(double w, double h, double d) {
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        p.setWidthMm(w);
        p.setHeightMm(h);
        p.setDepthMm(d);
        return p;
    }

    private static void syncShared(AppModel m, String id, String kind, Object payload, String name) throws Exception {
        m.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(id, kind, name,
                new ObjectMapper().writeValueAsString(payload), 1, false)));
    }

    @Test
    void sharedPresetKeepsItsRecordAndGetsPersonalDimensions(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        EquipmentPreset preset = new EquipmentPreset();
        preset.setName("H5");
        syncShared(m, "srv-h5", "EQUIPMENT", preset, "H5");
        RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, "srv-h5");
        RoomLibraryEdit.Found f = RoomLibraryEdit.find(m, ref);
        assertNotNull(f);
        assertTrue(f.shared());
        assertEquals("EQUIPMENT", f.serverKind());

        Object draft = RoomLibraryEdit.saveDimensions(m, f, loose(300, 50, 200));

        assertNull(((EquipmentPreset) f.item()).getPhysical(), "общая запись не меняется");
        assertEquals(300.0, m.getPhysicalOverrides().get(ref.key()).getWidthMm());
        EquipmentPreset proposed = (EquipmentPreset) draft;
        assertNotSame(f.item(), proposed);
        assertEquals("srv-h5", proposed.getId(), "правка существующей записи сохраняет её id");
        assertEquals(200.0, proposed.getPhysical().getDepthMm());
    }

    @Test
    void personalRoomItemIsEditedInPlaceAndStaleOverrideDropped(@TempDir Path dir) {
        AppModel m = newModel(dir);
        RoomFurnitureType mine = new RoomFurnitureType();
        mine.setName("Тумба");
        m.addRoomFurnitureType(mine);
        RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, mine.getId());
        PhysicalSpec stale = loose(1, 1, 1);
        m.setPhysicalOverride(ref, stale);

        RoomLibraryEdit.Found f = RoomLibraryEdit.find(m, ref);
        assertFalse(f.shared());
        PhysicalSpec p = loose(600, 800, 500);
        p.setWeightKg(25.0);
        RoomLibraryEdit.saveDimensions(m, f, p);

        RoomFurnitureType saved = m.getWorkspace().roomFurnitureTypeById(mine.getId());
        assertEquals(600.0, saved.getWidthMm());
        assertEquals(500.0, saved.getDepthMm(), "глубина и высота не путаются местами");
        assertEquals(800.0, saved.getHeightMm());
        assertEquals(25.0, saved.getWeightKg());
        assertNull(m.getPhysicalOverrides().get(ref.key()), "своя запись главнее прежних личных габаритов");
    }

    @Test
    void completenessDependsOnTheKindOfRecord() {
        CaseType c = new CaseType();
        PhysicalSpec onlyUnits = new PhysicalSpec();
        onlyUnits.setRackUnits(10);
        assertFalse(RoomLibraryEdit.isComplete(c, onlyUnits), "кофру нужна ещё внутренняя глубина");
        onlyUnits.setDepthMm(550.0);
        assertTrue(RoomLibraryEdit.isComplete(c, onlyUnits));

        RoomFurnitureType table = new RoomFurnitureType();
        PhysicalSpec size = loose(1600, 800, 750);
        assertTrue(RoomLibraryEdit.isComplete(table, size));
        size.setHeightMm(null);
        assertFalse(RoomLibraryEdit.isComplete(table, size));
        assertFalse(RoomLibraryEdit.isComplete(new RoomEquipmentType(), null));
        assertFalse(RoomLibraryEdit.isComplete(new RoomEquipmentType(), new PhysicalSpec()));
    }

    @Test
    void draftIsACopyAndLeavesTheOriginalAlone() {
        RoomEquipmentType e = new RoomEquipmentType();
        e.setName("Пульт");
        RoomEquipmentType d = (RoomEquipmentType) RoomLibraryEdit.draftWith(e, loose(891, 256, 612));
        assertEquals(e.getId(), d.getId());
        assertNull(e.getPhysical());
        assertEquals(891.0, d.getPhysical().getWidthMm());
        PhysicalSpec given = loose(1, 2, 3);
        assertNotSame(given, ((RoomEquipmentType) RoomLibraryEdit.draftWith(e, given)).getPhysical(),
                "в черновик кладётся копия блока, а не тот же объект");
    }

    @Test
    void unknownReferenceIsNotFound(@TempDir Path dir) {
        AppModel m = newModel(dir);
        assertNull(RoomLibraryEdit.find(m, new RoomLibRef(RoomLibRef.Kind.CASE, "нет")));
        assertNull(RoomLibraryEdit.find(m, null));
    }

    @Test
    void usageCountSeesPlacedFurnitureAcrossLayouts(@TempDir Path dir) {
        AppModel m = newModel(dir);
        RoomFurnitureType t = new RoomFurnitureType();
        t.setName("Стол");
        m.addRoomFurnitureType(t);
        RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, t.getId());
        ControlRoomLayout a = m.addControlRoomLayout("A");
        ControlRoomLayout b = m.addControlRoomLayout("B");
        for (ControlRoomLayout l : List.of(a, b)) {
            RoomFurniture f = new RoomFurniture();
            f.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, t.getId()));
            l.getFurniture().add(f);
        }
        assertEquals(2, m.countRoomLayoutReferences(ref));
        m.deleteRoomFurnitureType(t.getId());
        assertEquals(2, m.countRoomLayoutReferences(ref), "удаление позиции раскладки не трогает — она станет «неизвестной»");
        assertSame(ref.getKind(), RoomLibRef.Kind.ROOM_FURNITURE);
    }
}
