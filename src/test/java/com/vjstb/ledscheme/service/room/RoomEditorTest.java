package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Правки раскладки пультовой (порт операций прототипа). Здесь закреплено то, что пользователь
 * просил явно: кнопка «+» кладёт блок в кофр, а при нехватке места — стопкой без кофра; башню можно
 * поставить на стол и под стол; станок на виде спереди становится столом сверху на станках пола;
 * удаление трассы не теряет мелочь на ней.
 */
class RoomEditorTest {

    private static final RoomCatalog CAT = RoomPlanFixture.catalog();

    private static RoomItemSpec spec(RoomLibRef ref) {
        return CAT.resolve(ref);
    }

    private static ControlRoomLayout empty() {
        return new ControlRoomLayout("T");
    }

    // ------------------------------------------------------------ добавление из библиотеки

    @Test
    void caseBecomesANewTowerWithTheNextFreeLetter() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomEditor.Added a = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.cs("c4_35")), null, false);
        RoomTower t = RoomEditor.tower(l, a.selection().id());
        assertEquals("D", t.getTag(), "башни A, B, C уже есть");
        assertEquals(RoomSelection.Type.TOWER, a.selection().type());
    }

    @Test
    void rackItemGoesIntoTheSelectedTowersFirstFreeSlot() {
        ControlRoomLayout l = empty();
        RoomTower t = RoomRackLayout.newTower(l, RoomPlanFixture.cs("c4_35"), 100, 100);
        RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.eq("sw24")), RoomSelection.tower(t.getId()), false);
        assertEquals(1, t.getSegments().get(0).getItems().size());
        assertEquals(0, t.getSegments().get(0).getItems().get(0).getU0(), "ниже слотов нет — первый свободный");
        assertFalse(t.getSegments().get(0).isStack());
    }

    @Test
    void rackItemWithoutRoomFallsOntoAStackOnTop() {
        ControlRoomLayout l = empty();
        RoomTower t = RoomRackLayout.newTower(l, RoomPlanFixture.cs("c4_35"), 100, 100);
        for (int i = 0; i < 4; i++) {
            RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.eq("sw24")), RoomSelection.tower(t.getId()), false);
        }
        RoomEditor.Added a = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.eq("sw24")),
                RoomSelection.tower(t.getId()), false);
        assertEquals(2, t.getSegments().size(), "кофр заполнен — сверху появилась стопка");
        assertTrue(t.getSegments().get(1).isStack());
        assertTrue(a.message().contains("без кофра"));
    }

    @Test
    void rackItemWithNoTowersCreatesAStackTower() {
        ControlRoomLayout l = empty();
        RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.eq("sw24")), null, false);
        assertEquals(1, l.getTowers().size());
        assertTrue(l.getTowers().get(0).getSegments().get(0).isStack());
    }

    @Test
    void looseItemFollowsTheSelectedTowerOtherwiseLandsOnTheFloor() {
        ControlRoomLayout l = empty();
        RoomTower t = RoomRackLayout.newStackTower(l, null, 100, 100);
        RoomEditor.Added in = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.eq("h2s")),
                RoomSelection.tower(t.getId()), false);
        assertEquals(RoomSelection.Type.SITEM, in.selection().type());
        assertTrue(l.getLoose().isEmpty());

        ControlRoomLayout l2 = empty();
        RoomEditor.Added out = RoomEditor.addFromLibrary(l2, CAT, spec(RoomPlanFixture.eq("h2s")), null, false);
        assertEquals(RoomSelection.Type.LOOSE, out.selection().type());
        assertEquals(1, l2.getLoose().size());
    }

    @Test
    void looseItemDroppedNearARunSnapsToItAndRemembersTheDistanceFromTheEntry() {
        ControlRoomLayout l = empty();
        l.getRuns().add(runOf("r", RoomRunKind.SDI, 0, 0, 0, 200));
        RoomLooseItem near = RoomEditor.placeLoose(l, RoomPlanFixture.eq("h2s"), 20, 120);
        assertEquals(RoomLooseItem.Mode.RUN, near.getMode());
        assertEquals(120.0, near.getSCm(), 1e-9);
        RoomLooseItem far = RoomEditor.placeLoose(l, RoomPlanFixture.eq("h2s"), 200, 120);
        assertEquals(RoomLooseItem.Mode.FREE, far.getMode());
        assertEquals(200.0, far.getXCm());
    }

    @Test
    void deckOnTheFrontViewLandsOnTopOfFloorDecksAsATable() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        int before = l.getFurniture().size();
        RoomEditor.Added a = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.furn("dk100x50")), null, true);
        assertEquals(before + 1, l.getFurniture().size());
        assertTrue(RoomEditor.furniture(l, a.selection().id()).isOnTop(), "на виде спереди станок ставится сверху");
        RoomEditor.Added floor = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.furn("dk100x50")), null, false);
        assertFalse(RoomEditor.furniture(l, floor.selection().id()).isOnTop(), "на плане — на пол");
    }

    @Test
    void firstDeckOnAnEmptyRoomGoesToTheLeftWallOnTheGrid() {
        double[] spot = RoomEditor.findDeckSpot(empty(), CAT, spec(RoomPlanFixture.furn("dk200x100")));
        assertEquals(100.0, spot[0], 1e-9);
        assertEquals(0.0, (spot[1] - 50) % 50, 1e-9, "верхний край на линии сетки 50 см");
    }

    @Test
    void monitorIsPutOnATableAlongItsFarEdge() {
        ControlRoomLayout l = empty();
        RoomFurniture table = furnitureAt("t", "tbl180", 300, 150);
        l.getFurniture().add(table);
        RoomEditor.Added a = RoomEditor.addFromLibrary(l, CAT, spec(RoomPlanFixture.furn("mon27")), null, false);
        RoomFurniture m = RoomEditor.furniture(l, a.selection().id());
        assertTrue(m.getXCm() > 210 && m.getXCm() < 390, "по ширине стола");
        assertEquals(RoomEditor.step(120 + 11 + 3), m.getYCm(), 1e-9, "у дальнего края стола");
    }

    // ------------------------------------------------------------ вид спереди

    @Test
    void towerDraggedUpOntoATableStandsOnItAndLowTowerDraggedDownGoesUnderIt() {
        ControlRoomLayout l = empty();
        l.getFurniture().add(furnitureAt("t", "tbl180", 300, 150));
        RoomTower low = RoomRackLayout.newStackTower(l, RoomPlanFixture.eq("ledc1"), 100, 150);

        RoomEditor.ElevationSnap up = RoomEditor.elevationSnap(l, CAT, RoomSelection.Type.TOWER, low.getId(), 300, 75);
        assertTrue(up.on());
        assertEquals(RoomTower.Lay.ON, up.lay());
        assertTrue(up.y() >= 120 && up.y() <= 180, "глубина зажата в пределы стола");

        RoomEditor.ElevationSnap down = RoomEditor.elevationSnap(l, CAT, RoomSelection.Type.TOWER, low.getId(), 300, 0);
        assertFalse(down.on());
        assertEquals(RoomTower.Lay.UNDER, down.lay(), "низкая башня под столом");

        RoomEditor.ElevationSnap side = RoomEditor.elevationSnap(l, CAT, RoomSelection.Type.TOWER, low.getId(), 40, 0);
        assertNull(side.lay(), "в стороне от стола положение снова «авто»");
    }

    @Test
    void deckDraggedOntoFloorDecksBecomesATableAndSnapsToTheFiftyGrid() {
        ControlRoomLayout l = empty();
        RoomFurniture base = furnitureAt("base", "dk200x100", 100, 100);
        l.getFurniture().add(base);
        RoomFurniture top = furnitureAt("top", "dk100x50", 400, 100);
        l.getFurniture().add(top);

        RoomEditor.ElevationSnap s = RoomEditor.elevationSnap(l, CAT, RoomSelection.Type.FURN, "top", 112, 40);
        assertTrue(s.on(), "отпущен на высоте верха станка пола");
        assertEquals(0.0, (s.x() - 50) % 50, 1e-9, "левый край на сетке 50 см");
        RoomEditor.applyElevation(l, RoomSelection.Type.FURN, "top", s);
        assertTrue(top.isOnTop());

        RoomEditor.ElevationSnap down = RoomEditor.elevationSnap(l, CAT, RoomSelection.Type.FURN, "top", 112, 0);
        assertFalse(down.on(), "опущен вниз — снова на пол");
    }

    // ------------------------------------------------------------ удаление и поворот

    @Test
    void removingATowerOrAnItemTakesOnlyThatObject() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        int rackBefore = l.getTowers().get(0).getSegments().get(0).getItems().size();
        RoomEditor.removeSelection(l, RoomSelection.rackItem("a1", "tA", "cA1"));
        assertEquals(rackBefore - 1, l.getTowers().get(0).getSegments().get(0).getItems().size());
        RoomEditor.removeSelection(l, RoomSelection.tower("tB"));
        assertNull(RoomEditor.tower(l, "tB"));
        assertNotNull(RoomEditor.tower(l, "tA"));
    }

    @Test
    void removingARunLeavesItsLooseItemsLyingWhereTheyWere() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomLooseItem on = RoomEditor.loose(l, "l1");
        RoomPoint where = RoomEditor.locationOf(l, on);
        RoomEditor.removeSelection(l, RoomSelection.run("sdi"));
        assertNull(RoomEditor.run(l, "sdi"));
        assertEquals(RoomLooseItem.Mode.FREE, on.getMode());
        assertEquals(where.getX(), on.getXCm(), 1e-9);
        assertEquals(where.getY(), on.getYCm(), 1e-9);
    }

    @Test
    void rotateTurnsTowersAndFurnitureByNinetyDegrees() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomEditor.rotate(l, RoomSelection.tower("tA"));
        RoomEditor.rotate(l, RoomSelection.furniture("f1"));
        RoomEditor.rotate(l, RoomSelection.furniture("f1"));
        assertEquals(90, RoomEditor.tower(l, "tA").getRotation());
        assertEquals(180, RoomEditor.furniture(l, "f1").getRotation());
        for (int i = 0; i < 3; i++) {
            RoomEditor.rotate(l, RoomSelection.tower("tA"));
        }
        assertEquals(0, RoomEditor.tower(l, "tA").getRotation());
    }

    // ------------------------------------------------------------ трассы и мелочь

    @Test
    void newRunsGetTheirTypeNameWithANumberAndNeedTwoPoints() {
        ControlRoomLayout l = empty();
        assertNull(RoomEditor.addRun(l, RoomRunKind.SDI, List.of(new RoomPoint(0, 0))));
        RoomRun first = RoomEditor.addRun(l, RoomRunKind.SDI, List.of(new RoomPoint(0, 0), new RoomPoint(0, 100)));
        RoomRun second = RoomEditor.addRun(l, RoomRunKind.SDI, List.of(new RoomPoint(50, 0), new RoomPoint(50, 100)));
        assertEquals("SDI", first.getName());
        assertEquals("SDI 2", second.getName());
        assertEquals("sdi", second.getKind());
    }

    @Test
    void runPointsCanBeInsertedMovedWithSnapAndRemovedDownToTwo() {
        ControlRoomLayout l = empty();
        RoomRun r = RoomEditor.addRun(l, RoomRunKind.NET, List.of(new RoomPoint(0, 0), new RoomPoint(100, 0)));
        RoomEditor.insertRunPoint(r, 0);
        assertEquals(3, r.getPoints().size());
        assertEquals(50.0, r.getPoints().get(1).getX(), 1e-9);
        RoomEditor.moveRunPoint(l, r, 1, 52, 37);
        assertEquals(50.0, r.getPoints().get(1).getX(), 1e-9, "шаг 5 см");
        assertEquals(35.0, r.getPoints().get(1).getY(), 1e-9);
        RoomEditor.moveRunPoint(l, r, 1, 52, 7);
        assertEquals(0.0, r.getPoints().get(1).getY(), 1e-9, "ближе 12 см к стене — на стену");
        assertTrue(RoomEditor.removeRunPoint(r, 1));
        assertFalse(RoomEditor.removeRunPoint(r, 0), "меньше двух точек трасса не бывает");
    }

    @Test
    void looseModeSwitchKeepsTheItemWhereItIs() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomLooseItem sdi = RoomEditor.loose(l, "l1");
        RoomPoint p = RoomEditor.locationOf(l, sdi);
        RoomEditor.setLooseMode(l, sdi, RoomLooseItem.Mode.FREE, null);
        assertEquals(p.getX(), sdi.getXCm(), 1e-9);
        RoomEditor.setLooseMode(l, sdi, RoomLooseItem.Mode.RUN, "sdi");
        assertEquals("sdi", sdi.getRunId());
        assertEquals(195.0, sdi.getSCm(), 1e-6);
        RoomEditor.setLooseMode(l, sdi, RoomLooseItem.Mode.BOX, null);
        assertEquals(RoomLooseItem.Mode.BOX, sdi.getMode());
        assertNull(sdi.getRunId());
    }

    @Test
    void snapPositionUsesTheDeckGridOrFiveCentimetres() {
        double[] deck = RoomEditor.snapPosition(true, 200, 100, 117, 63);
        assertEquals(0.0, (deck[0] - 100) % 50, 1e-9);
        assertEquals(0.0, (deck[1] - 50) % 50, 1e-9);
        double[] other = RoomEditor.snapPosition(false, 60, 60, 117, 63);
        assertEquals(115.0, other[0], 1e-9);
        assertEquals(65.0, other[1], 1e-9);
    }

    // ------------------------------------------------------------ вспомогательное

    private static RoomRun runOf(String id, RoomRunKind kind, double... xy) {
        RoomRun r = new RoomRun();
        r.setId(id);
        r.setKind(kind.key());
        for (int i = 0; i < xy.length; i += 2) {
            r.getPoints().add(new RoomPoint(xy[i], xy[i + 1]));
        }
        return r;
    }

    private static RoomFurniture furnitureAt(String id, String lib, double x, double y) {
        RoomFurniture f = new RoomFurniture();
        f.setId(id);
        f.setRef(RoomPlanFixture.furn(lib));
        f.setXCm(x);
        f.setYCm(y);
        return f;
    }
}
