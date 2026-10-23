package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomOverrides;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Карточка библиотеки, брошенная на план или вид спереди, и «Сохранить как позицию» из параметров объекта. */
class RoomEditorDropTest {

    private static final RoomCatalog CAT = RoomPlanFixture.catalog();

    private static RoomItemSpec spec(RoomLibRef ref) {
        return CAT.resolve(ref);
    }

    @Test
    void towerAtUsesTheRotatedFootprint() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        // патч-панель: 48 см в ширину и всего 10 см в глубину — след сильно вытянут вдоль x
        RoomTower t = RoomRackLayout.newStackTower(l, RoomPlanFixture.eq("cat6pp"), 200, 200);
        assertNotNull(RoomEditor.towerAt(l, CAT, 220, 200), "вдоль длинной стороны");
        assertNull(RoomEditor.towerAt(l, CAT, 200, 220), "поперёк за пределами следа");
        t.setRotation(90);
        assertNull(RoomEditor.towerAt(l, CAT, 220, 200), "после поворота след вытянут вдоль y");
        assertNotNull(RoomEditor.towerAt(l, CAT, 200, 220));
    }

    @Test
    void rackBlockDroppedOnATowerFillsItsSlotOtherwiseMakesAStackTower() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = RoomRackLayout.newTower(l, RoomPlanFixture.cs("c4_35"), 200, 200);
        RoomEditor.Added in = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.eq("sw24")), 200, 200);
        assertEquals(t.getId(), in.selection().id());
        assertEquals(1, t.getSegments().get(0).getItems().size());

        RoomEditor.Added out = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.eq("sw24")), 500, 400);
        assertEquals(2, l.getTowers().size());
        assertTrue(RoomEditor.tower(l, out.selection().id()).getSegments().get(0).isStack());
    }

    @Test
    void caseDroppedOnThePlanBecomesATowerAtTheSnappedPoint() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomEditor.Added a = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.cs("c4_35")), 207, 193);
        RoomTower t = RoomEditor.tower(l, a.selection().id());
        assertEquals(205.0, t.getXCm(), 1e-9);
        assertEquals(195.0, t.getYCm(), 1e-9);
    }

    @Test
    void looseDroppedOnATowerStacksAndNearARunSnapsToIt() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomRackLayout.newStackTower(l, null, 100, 300);
        RoomRun run = new RoomRun();
        run.setId("r");
        run.setKind("net");
        run.getPoints().add(new RoomPoint(300, 0));
        run.getPoints().add(new RoomPoint(300, 200));
        l.getRuns().add(run);
        RoomEditor.Added onTower = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.eq("h2s")), 100, 300);
        assertEquals(RoomSelection.Type.SITEM, onTower.selection().type());
        RoomEditor.Added onRun = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.eq("h2s")), 310, 80);
        RoomLooseItem item = RoomEditor.loose(l, onRun.selection().id());
        assertEquals(RoomLooseItem.Mode.RUN, item.getMode());
        assertEquals(80.0, item.getSCm(), 1e-9);
    }

    @Test
    void deckDroppedOnThePlanSnapsToTheFiftyGridAndFurnitureToFive() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomEditor.Added deck = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.furn("dk200x100")), 217, 133);
        RoomFurniture d = RoomEditor.furniture(l, deck.selection().id());
        assertEquals(0.0, (d.getXCm() - 100) % 50, 1e-9);
        assertEquals(0.0, (d.getYCm() - 50) % 50, 1e-9);
        RoomEditor.Added chair = RoomEditor.dropOnPlan(l, CAT, spec(RoomPlanFixture.furn("chair")), 217, 133);
        RoomFurniture c = RoomEditor.furniture(l, chair.selection().id());
        assertEquals(215.0, c.getXCm(), 1e-9);
        assertEquals(135.0, c.getYCm(), 1e-9);
    }

    @Test
    void onlyFurnitureDecksAndMonitorsCanBeDroppedOnTheFrontView() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        assertNull(RoomEditor.dropOnElevation(l, CAT, spec(RoomPlanFixture.cs("c4_35")), 100, 0));
        assertNull(RoomEditor.dropOnElevation(l, CAT, spec(RoomPlanFixture.eq("sw24")), 100, 0));
        assertTrue(l.getTowers().isEmpty() && l.getFurniture().isEmpty());
        assertNotNull(RoomEditor.dropOnElevation(l, CAT, spec(RoomPlanFixture.furn("dk100x100")), 100, 0));
        assertEquals(1, l.getFurniture().size());
    }

    @Test
    void deckDroppedHighOnTheFrontViewLandsOnTopOfFloorDecks() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomFurniture base = new RoomFurniture();
        base.setRef(RoomPlanFixture.furn("dk200x100"));
        base.setXCm(200);
        base.setYCm(150);
        l.getFurniture().add(base);
        RoomEditor.Added a = RoomEditor.dropOnElevation(l, CAT, spec(RoomPlanFixture.furn("dk100x50")), 180, 40);
        assertTrue(RoomEditor.furniture(l, a.selection().id()).isOnTop());
        RoomEditor.Added floor = RoomEditor.dropOnElevation(l, CAT, spec(RoomPlanFixture.furn("dk100x50")), 560, 0);
        assertFalse(RoomEditor.furniture(l, floor.selection().id()).isOnTop());
    }

    @Test
    void adjustedFurnitureBecomesAPersonalLibraryItemAndTheObjectSwitchesToIt(@TempDir Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        RoomFurnitureType base = new RoomFurnitureType();
        base.setName("Стол 160");
        base.setKind(RoomFurnitureType.Kind.FURNITURE);
        base.setRole(RoomFurnitureType.Role.TABLE);
        base.setWidthMm(1600.0);
        base.setDepthMm(800.0);
        base.setHeightMm(750.0);
        m.addRoomFurnitureType(base);

        RoomFurniture f = new RoomFurniture();
        f.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, base.getId()));
        RoomOverrides o = new RoomOverrides();
        o.setWidthCm(200.0);
        o.setName("Стол 160");
        f.setOverrides(o);
        RoomLibraryAdapterHolder h = new RoomLibraryAdapterHolder(m);

        RoomFurnitureType made = RoomLibraryEdit.saveFurnitureAsPreset(m, f, RoomGeometry.effective(f, h.cat()));
        assertEquals("Стол 160 (своё)", made.getName(), "название занято — добавлено «(своё)»");
        assertEquals(2000.0, made.getWidthMm(), 1e-9, "ширина с переопределением, в миллиметрах");
        assertEquals(RoomFurnitureType.Role.TABLE, made.getRole());
        assertEquals(made.getId(), f.getRef().getId());
        assertNull(f.getOverrides());
        assertEquals(2, m.getRoomFurnitureTypes().size());
    }

    /** Каталог библиотеки модели на момент вызова. */
    private record RoomLibraryAdapterHolder(AppModel m) {
        RoomCatalog cat() {
            return RoomLibraryAdapter.of(m);
        }
    }
}
