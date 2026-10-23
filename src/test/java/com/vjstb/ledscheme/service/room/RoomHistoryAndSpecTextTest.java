package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import org.junit.jupiter.api.Test;

/** Отмена правок раскладки и текст спецификации. Отмена обязана возвращать раскладку на месте (тот же
 *  объект, тот же id и название), потому что окно и список раскладок сцены держат на неё ссылки. */
class RoomHistoryAndSpecTextTest {

    private static final RoomCatalog CAT = RoomPlanFixture.catalog();

    @Test
    void undoRestoresTheSameLayoutObjectAndRedoBringsTheChangeBack() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        String id = l.getId();
        String name = l.getName();
        RoomHistory h = new RoomHistory();
        h.push(l);
        RoomEditor.removeSelection(l, RoomSelection.tower("tB"));
        assertNull(RoomEditor.tower(l, "tB"));
        assertTrue(h.canUndo());

        assertTrue(h.undo(l));
        assertNotNull(RoomEditor.tower(l, "tB"), "башня вернулась");
        assertEquals(id, l.getId());
        assertEquals(name, l.getName());
        assertEquals(3, l.getTowers().size());
        assertEquals(4, l.getRuns().size());

        assertTrue(h.canRedo());
        assertTrue(h.redo(l));
        assertNull(RoomEditor.tower(l, "tB"));
        assertFalse(h.redo(l), "возвращать больше нечего");
    }

    @Test
    void aNewEditClearsRedoAndHistoryIsCapped() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomHistory h = new RoomHistory();
        h.push(l);
        l.setRoomWidthCm(800);
        h.undo(l);
        assertTrue(h.canRedo());
        h.push(l);
        assertFalse(h.canRedo());
        for (int i = 0; i < RoomHistory.LIMIT + 20; i++) {
            h.push(l);
        }
        int steps = 0;
        while (h.undo(l)) {
            steps++;
        }
        assertEquals(RoomHistory.LIMIT, steps);
    }

    @Test
    void specTextListsTowersCasesDecksMonitorsAndLooseByRun() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        String txt = RoomSpecText.of(l, CAT, RoomPlanCalc.calc(l, CAT));
        assertTrue(txt.startsWith("ПЛАННЕР ПУЛЬТОВОЙ · площадка 7 × 4,5 м"), txt);
        assertTrue(txt.contains("Башня A — Башня A · LED-процессинг"));
        assertTrue(txt.contains("  Кофр 6U (снаружи"));
        assertTrue(txt.contains("U1  Коммутатор 24×1G  (1U, гл. 30 см)"));
        assertTrue(txt.contains("1U 1/2"), "доля ширины названа словами");
        assertTrue(txt.contains("  Без кофра, стопкой (сверху вниз):"));
        assertTrue(txt.contains("Пол из станков:"));
        assertTrue(txt.contains("Мониторы:"));
        assertTrue(txt.contains("  3 × Монитор") || txt.contains("  2 × Монитор 27″"), txt);
        assertTrue(txt.contains("SDI, "));
        assertTrue(txt.contains(" м от ввода: Конвертер HDMI→SDI"));
        assertTrue(txt.contains("  свободно: Разветвитель HDMI 1×4"));
        assertTrue(txt.contains("  в кофре: Блок питания внешний"));
        assertTrue(txt.contains("Кейс мелочёвки:"));
    }

    @Test
    void emptyLayoutProducesAShortValidSpec() {
        ControlRoomLayout l = new ControlRoomLayout("Пустая");
        String txt = RoomSpecText.of(l, CAT, RoomPlanCalc.calc(l, CAT));
        assertTrue(txt.contains("Итого: 0 башн."));
        assertFalse(txt.contains("Мониторы:"));
    }

    // ------------------------------------------------------------ стойки

    private static RoomTower towerWithCase(ControlRoomLayout l, String caseId) {
        return RoomRackLayout.newTower(l, RoomPlanFixture.cs(caseId), 100, 100);
    }

    @Test
    void blockDroppedOnAFreeSlotPlacesWithoutShifting() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = towerWithCase(l, "c6_45");
        RoomSegment seg = t.getSegments().get(0);
        RoomItemSpec sw = CAT.resolve(RoomPlanFixture.eq("sw24"));
        RoomEditor.SlotPlan plan = RoomEditor.planSlot(CAT, seg, sw, null, 3.5, 0.5);
        assertTrue(plan.ok());
        assertFalse(plan.insert());
        assertEquals(3, plan.u0(), "центр блока под указателем: 3,5 − 0,5 строки");
        RoomSelection sel = RoomEditor.dropIntoSlot(l, t.getId(), seg.getId(), plan, sw.ref(), null);
        assertEquals(RoomSelection.Type.RITEM, sel.type());
        assertEquals(1, seg.getItems().size());
    }

    @Test
    void blockDroppedBetweenFullWidthNeighboursPushesThemDown() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = towerWithCase(l, "c6_45");
        RoomSegment seg = t.getSegments().get(0);
        RoomItemSpec pdu = CAT.resolve(RoomPlanFixture.eq("pdu"));
        for (int u = 0; u < 3; u++) {
            RoomRackItem it = new RoomRackItem();
            it.setRef(pdu.ref());
            it.setU0(u);
            seg.getItems().add(it);
        }
        RoomItemSpec sw = CAT.resolve(RoomPlanFixture.eq("sw24"));
        RoomEditor.SlotPlan plan = RoomEditor.planSlot(CAT, seg, sw, null, 1.0, 0.5);
        assertTrue(plan.ok());
        assertTrue(plan.insert(), "место занято — вставка на границу со сдвигом");
        RoomEditor.dropIntoSlot(l, t.getId(), seg.getId(), plan, sw.ref(), null);
        assertEquals(4, seg.getItems().size());
        int maxU = seg.getItems().stream().mapToInt(RoomRackItem::getU0).max().getAsInt();
        assertEquals(3, maxU, "нижний сосед уехал на ряд вниз");
    }

    @Test
    void blockThatCannotFitIsRefused() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = towerWithCase(l, "c4_35");
        RoomSegment seg = t.getSegments().get(0);
        RoomItemSpec ups = CAT.resolve(RoomPlanFixture.eq("ups2"));
        for (int u = 0; u < 4; u += 2) {
            RoomRackItem it = new RoomRackItem();
            it.setRef(ups.ref());
            it.setU0(u);
            seg.getItems().add(it);
        }
        assertFalse(RoomEditor.planSlot(CAT, seg, ups, null, 2.0, 0.5).ok());
    }

    @Test
    void movingABlockWithinTheCaseKeepsItsIdAndRemovesItFromTheOldSlot() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = towerWithCase(l, "c6_45");
        RoomSegment seg = t.getSegments().get(0);
        RoomItemSpec sw = CAT.resolve(RoomPlanFixture.eq("sw24"));
        RoomRackItem it = new RoomRackItem();
        it.setRef(sw.ref());
        it.setU0(0);
        seg.getItems().add(it);
        RoomEditor.SlotPlan plan = RoomEditor.planSlot(CAT, seg, sw, it.getId(), 4.5, 0.5);
        RoomEditor.dropIntoSlot(l, t.getId(), seg.getId(), plan, sw.ref(), it.getId());
        assertEquals(1, seg.getItems().size());
        assertEquals(it.getId(), seg.getItems().get(0).getId());
        assertEquals(4, seg.getItems().get(0).getU0());
    }

    @Test
    void stackDropsReorderInsideAndMoveBetweenTowers() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower a = RoomRackLayout.newStackTower(l, RoomPlanFixture.eq("ledc1"), 100, 100);
        RoomSegment segA = a.getSegments().get(0);
        RoomRackLayout.addToStack(a, RoomPlanFixture.eq("ctl_m"));
        String topId = segA.getItems().get(segA.getItems().size() - 1).getId();
        // тот предмет, что сверху, переносим вниз стопки (позиция 1 сверху — после самого верхнего)
        RoomEditor.dropIntoStack(l, CAT, a.getId(), segA.getId(), 2, RoomPlanFixture.eq("ctl_m"), topId);
        assertEquals(topId, segA.getItems().get(0).getId(), "предмет теперь самый нижний");

        RoomTower b = RoomRackLayout.newStackTower(l, null, 200, 100);
        RoomSelection sel = RoomEditor.dropIntoStack(l, CAT, b.getId(), null, 0, RoomPlanFixture.eq("ctl_m"), topId);
        assertEquals(1, b.getSegments().get(0).getItems().size());
        assertEquals(1, segA.getItems().size(), "из старой стопки предмет убран");
        assertEquals(RoomSelection.Type.SITEM, sel.type());
    }

    @Test
    void droppingOnNothingMakesANewStackTower() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomEditor.dropIntoStack(l, CAT, null, null, 0, RoomPlanFixture.eq("ctl_s"), null);
        assertEquals(1, l.getTowers().size());
        assertTrue(l.getTowers().get(0).getSegments().get(0).isStack());
        assertEquals(1, l.getTowers().get(0).getSegments().get(0).getItems().size());
    }

    @Test
    void casesCanBeAddedMovedBetweenTowersAndLiftedOut() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower a = towerWithCase(l, "c4_35");
        RoomTower b = RoomRackLayout.newTower(l, RoomPlanFixture.cs("c6_45"), 300, 100);
        RoomEditor.dropCaseIntoTower(l, a.getId(), 0, RoomPlanFixture.cs("c8_45"));
        assertEquals(2, a.getSegments().size());
        assertEquals("c8_45", a.getSegments().get(1).getCaseRef().getId(), "новый кофр сверху башни");

        String segId = a.getSegments().get(1).getId();
        RoomEditor.moveSegment(l, a.getId(), segId, b.getId(), 0);
        assertEquals(1, a.getSegments().size());
        assertEquals(2, b.getSegments().size());

        RoomEditor.moveSegment(l, a.getId(), a.getSegments().get(0).getId(), b.getId(), 0);
        assertNull(RoomEditor.tower(l, a.getId()), "опустевшая башня исчезла");

        RoomSelection lifted = RoomEditor.liftSegmentToNewTower(l, CAT, b.getId(), b.getSegments().get(0).getId());
        assertEquals(2, l.getTowers().size());
        assertNotNull(RoomEditor.tower(l, lifted.id()));
        assertEquals(2, b.getSegments().size());
    }

    @Test
    void stackItemMovesUpAndDownByOneAndStopsAtTheEnds() {
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = RoomRackLayout.newStackTower(l, RoomPlanFixture.eq("ledc1"), 100, 100);
        RoomRackItem second = RoomRackLayout.addToStack(t, RoomPlanFixture.eq("ctl_m"));
        RoomSegment seg = t.getSegments().get(0);
        String bottom = seg.getItems().get(0).getId();
        RoomEditor.moveStackItem(l, bottom, true);
        assertEquals(bottom, seg.getItems().get(1).getId());
        RoomEditor.moveStackItem(l, bottom, true);
        assertEquals(bottom, seg.getItems().get(1).getId(), "выше некуда");
        RoomEditor.moveStackItem(l, bottom, false);
        assertEquals(bottom, seg.getItems().get(0).getId(), "и обратно вниз");
        RoomEditor.moveStackItem(l, bottom, false);
        assertEquals(bottom, seg.getItems().get(0).getId(), "ниже некуда");
        assertEquals(second.getId(), seg.getItems().get(1).getId());
    }

    @Test
    void libRefsUsedByTheRackOperationsAreKept() {
        RoomLibRef ref = RoomPlanFixture.eq("pdu");
        ControlRoomLayout l = new ControlRoomLayout("T");
        RoomTower t = towerWithCase(l, "c4_35");
        RoomSegment seg = t.getSegments().get(0);
        RoomEditor.SlotPlan plan = RoomEditor.planSlot(CAT, seg, CAT.resolve(ref), null, 0.5, 0.1);
        RoomEditor.dropIntoSlot(l, t.getId(), seg.getId(), plan, ref, null);
        assertEquals(ref, seg.getItems().get(0).getRef());
    }
}
