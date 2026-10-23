package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Слоты кофра, вставка «между позициями» со сдвигом соседей, стопки без кофра и порядок слоёв
 * башни. Запрос пользователя 2026-10-23: блок, брошенный на занятое место, вставляется между
 * соседями везде — в кофре, в стопке и между слоями башни; стопка без кофра — штатный слой.
 */
class RoomRackLayoutTest {

    private final RoomCatalog cat = RoomPlanFixture.catalog();

    private static List<String> ids(List<RoomRackItem> items) {
        return items.stream().map(RoomRackItem::getId).toList();
    }

    @Test
    void halfWidthBlocksShareARowAndTheThirdOneIsRefused() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment b = RoomPlanFixture.segment(l, "tB", "cB1"); // ряд 4 занят двумя половинками
        assertFalse(RoomRackLayout.fits(cat, b, 4, 1, null, 0, 6), "левая половинка занята");
        assertFalse(RoomRackLayout.fits(cat, b, 4, 1, null, 6, 6), "правая половинка занята");
        assertFalse(RoomRackLayout.fits(cat, b, 4, 1, null, 0, 12), "целиком не влезает");
        assertTrue(RoomRackLayout.fits(cat, b, 4, 1, "b5", 0, 6), "сам блок слот не загораживает");
    }

    @Test
    void insertingBetweenItemsShiftsTheNeighboursDownByTheMinimum() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment top = RoomPlanFixture.segment(l, "tA", "cA2"); // 8U: 0-1, 2-3, 4, 5, свободно 6-7
        RoomItemSpec patch = cat.resolve(RoomPlanFixture.eq("cat6pp"));
        RoomRackLayout.Push p = RoomRackLayout.pushLayout(cat, top, patch, 2, 0, null);
        assertNotNull(p);
        assertEquals(2, p.u0());
        assertEquals(Map.of("a6", 3, "a7", 5, "a8", 6), p.moves());
    }

    @Test
    void boundaryInsideABlockSnapsToItsNearestEdgeAndTiesGoUp() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment top = RoomPlanFixture.segment(l, "tA", "cA2");
        RoomItemSpec patch = cat.resolve(RoomPlanFixture.eq("cat6pp"));
        // граница 3 ровно посередине блока a6 (ряды 2-3): ничья уходит к верхнему краю, как в прототипе
        assertEquals(2, RoomRackLayout.pushLayout(cat, top, patch, 3, 0, null).u0());
        // трёхъюнитный блок: граница ближе к нижнему краю идёт вниз, ближе к верхнему — вверх
        RoomSegment seg = new RoomSegment();
        seg.setCaseRef(RoomPlanFixture.cs("c10_55"));
        RoomRackItem mix = new RoomRackItem();
        mix.setRef(RoomPlanFixture.eq("mix3"));
        mix.setU0(1);
        seg.getItems().add(mix);
        assertEquals(4, RoomRackLayout.pushLayout(cat, seg, patch, 3, 0, null).u0());
        assertEquals(1, RoomRackLayout.pushLayout(cat, seg, patch, 2, 0, null).u0());
    }

    @Test
    void insertionIsRefusedWhenTheCaseHasNoRoomToShift() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment bottom = RoomPlanFixture.segment(l, "tA", "cA1"); // 6U: 0,1,2, 4-5; свободен только ряд 3
        RoomItemSpec ups = cat.resolve(RoomPlanFixture.eq("ups2"));
        assertNull(RoomRackLayout.pushLayout(cat, bottom, ups, 0, 0, null));
    }

    @Test
    void movingAnItemWithinTheCaseIgnoresItselfWhenShifting() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment top = RoomPlanFixture.segment(l, "tA", "cA2");
        RoomItemSpec fan = cat.resolve(RoomPlanFixture.eq("fan1"));
        // вентиляторы (ряд 5) переносим на границу 0: остальные сдвигаются на 1, сам он не мешает
        RoomRackLayout.Push p = RoomRackLayout.pushLayout(cat, top, fan, 0, 0, "a8");
        assertNotNull(p);
        assertEquals(Map.of("a5", 1, "a6", 3, "a7", 5), p.moves());
    }

    @Test
    void autoPlaceUsesTheFirstFittingSlotAndSkipsStacks() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower a = RoomPlanFixture.tower(l, "tA");
        RoomItemSpec cat6 = cat.resolve(RoomPlanFixture.eq("cat6pp"));
        assertTrue(RoomRackLayout.autoPlace(cat, a, cat6));
        RoomSegment top = a.getSegments().get(1);
        assertEquals(6, top.getItems().get(top.getItems().size() - 1).getU0(), "первый свободный ряд верхнего кофра");
        RoomTower c = RoomPlanFixture.tower(l, "tC");
        assertFalse(RoomRackLayout.autoPlace(cat, c, cat6), "в башне из одной стопки слотов нет");
    }

    @Test
    void stackKeepsBottomUpOrderAndInsertsByTopDownPosition() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment s = RoomPlanFixture.segment(l, "tC", "sC1"); // снизу вверх: c1 c2 c3 c4
        RoomRackItem added = RoomRackLayout.addToStack(RoomPlanFixture.tower(l, "tC"), RoomPlanFixture.eq("h2s"));
        assertEquals(List.of("c1", "c2", "c3", "c4", added.getId()), ids(s.getItems()), "новый — сверху");
        RoomRackItem moved = s.getItems().get(0); // c1, самый нижний
        s.getItems().remove(moved);
        RoomRackLayout.insertStack(s, moved, 0, null);
        assertEquals("c1", s.getItems().get(s.getItems().size() - 1).getId(), "после переноса c1 — самый верхний");
    }

    @Test
    void movingInsideTheSameStackCorrectsThePosition() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomSegment s = RoomPlanFixture.segment(l, "tC", "sC1"); // сверху вниз: c4 c3 c2 c1
        RoomRackItem c4 = s.getItems().get(3);
        RoomRackLayout.insertStack(s, c4, 3, "c4"); // позиция «перед c1», то есть между c2 и c1
        List<String> topDown = new java.util.ArrayList<>(ids(s.getItems()));
        java.util.Collections.reverse(topDown);
        assertEquals(List.of("c3", "c2", "c4", "c1"), topDown);
    }

    @Test
    void anyItemCanLieOnTopOfTheStackWithoutACase() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower b = RoomPlanFixture.tower(l, "tB");
        RoomSegment before = b.getSegments().get(b.getSegments().size() - 1);
        assertFalse(before.isStack());
        RoomRackLayout.addToStack(b, RoomPlanFixture.eq("ledc1"));
        RoomSegment top = b.getSegments().get(b.getSegments().size() - 1);
        assertTrue(top.isStack(), "поверх кофра создаётся слой-стопка");
        assertEquals(2, b.getSegments().size());
    }

    @Test
    void layersCanBeInsertedBetweenOthersAndMovedWithinTheTower() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower a = RoomPlanFixture.tower(l, "tA"); // снизу вверх: cA1, cA2
        RoomSegment extra = new RoomSegment();
        extra.setId("x");
        extra.setCaseRef(RoomPlanFixture.cs("c4_35"));
        RoomRackLayout.insertSegment(a, extra, 1, null); // сверху вниз: cA2, x, cA1
        assertEquals(List.of("cA1", "x", "cA2"), a.getSegments().stream().map(RoomSegment::getId).toList());
        RoomSegment a1 = a.getSegments().get(0);
        RoomRackLayout.insertSegment(a, a1, 0, "cA1"); // нижний кофр — наверх
        assertEquals(List.of("x", "cA2", "cA1"), a.getSegments().stream().map(RoomSegment::getId).toList());
    }

    @Test
    void newTowersGetTheNextFreeLetter() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower t = RoomRackLayout.newStackTower(l, RoomPlanFixture.eq("ledc1"), 100, 100);
        assertEquals("D", t.getTag());
        assertEquals("Башня D", t.getName());
        assertTrue(t.getSegments().get(0).isStack());
        assertEquals(4, l.getTowers().size());
    }
}
