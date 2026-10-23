package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomWarning.Level;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Расчёт раскладки пультовой на «примере проекта» из прототипа ({@code planner.html}): итоги,
 * замечания и правила станков/столов, которые пользователь проверил вживую и потребовал сохранить —
 * башня на столе и под ним, станок-стол сверху на станках пола, ошибка «висит в воздухе», линия
 * глаз от пола станка. Эталонные числа сняты с прототипа; единственное расхождение — «занято
 * юнитов»: две половинки в одном ряду теперь считаются одним юнитом (в прототипе — дважды).
 */
class RoomPlanCalcTest {

    private final RoomCatalog cat = RoomPlanFixture.catalog();

    private RoomPlanCalc.Result calc(ControlRoomLayout l) {
        return RoomPlanCalc.calc(l, cat);
    }

    private List<RoomWarning> level(RoomPlanCalc.Result r, Level lv) {
        return r.warnings().stream().filter(w -> w.level() == lv).toList();
    }

    @Test
    void sampleTotalsMatchThePrototype() {
        RoomPlanCalc.Result r = calc(RoomPlanFixture.sample());
        RoomPlanCalc.Totals t = r.totals();
        assertEquals(3, t.towers());
        assertEquals(24, t.unitsTotal(), "кофры 6U + 8U + 10U");
        assertEquals(21, t.unitsUsed(), "половинки в одном ряду — один юнит");
        assertEquals(4, t.cases(), "три рэк-кофра и кейс мелочёвки");
        assertEquals(9, t.deck().count(), "восемь нижних станков и один станок-стол");
        assertEquals(700, t.deck().widthCm(), 1e-9);
        assertEquals(150, t.deck().depthCm(), 1e-9);
        assertEquals(10.5, t.deck().areaM2(), 1e-9, "площадь по нижнему ряду, станок-стол не считается");
    }

    @Test
    void sampleHasOnlyTheRearGapWarningAndTheFreeLooseNote() {
        RoomPlanCalc.Result r = calc(RoomPlanFixture.sample());
        assertTrue(level(r, Level.ERROR).isEmpty(), level(r, Level.ERROR).toString());
        List<RoomWarning> warn = level(r, Level.WARNING);
        assertEquals(1, warn.size(), warn.toString());
        assertEquals("Медиасервер: до задней крышки 0 см, кабелям тесно", warn.get(0).text());
        assertEquals(1, level(r, Level.INFO).size());
        assertTrue(level(r, Level.INFO).get(0).text().startsWith("Без привязки лежит: 2 шт."));
        assertTrue(r.air().isEmpty() && r.bad().isEmpty());
    }

    @Test
    void stackOnlyTowerStandsOnTheTableWithoutAnError() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower c = RoomPlanFixture.tower(l, "tC");
        c.setXCm(360);
        c.setYCm(260); // целиком внутри стола 180×60 (270..450 × 215..275)
        RoomPlanCalc.Result r = calc(l);
        assertTrue(level(r, Level.ERROR).isEmpty(), "пересечение со столом разрешено положением «на»");
        List<RoomBounds> b = r.bounds();
        RoomBounds tw = b.stream().filter(x -> x.id.equals("tC")).findFirst().orElseThrow();
        RoomBounds host = RoomGeometry.hostOf(tw, b);
        assertNotNull(host);
        assertEquals(RoomGeometry.EffLay.ON, RoomGeometry.layMode(tw, host), "стопка без кофров — на стол");
        assertEquals(115, RoomGeometry.towerBase(tw, b), 1e-9, "станок 40 + стол 75");
    }

    @Test
    void lowCasedTowerGoesUnderTheTableAndTallOneIsRefused() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower low = RoomRackLayout.newTower(l, RoomPlanFixture.cs("c4_35"), 360, 250);
        RoomPlanCalc.Result r = calc(l);
        assertTrue(level(r, Level.ERROR).isEmpty(), level(r, Level.ERROR).toString());
        RoomBounds tw = r.bounds().stream().filter(x -> x.id.equals(low.getId())).findFirst().orElseThrow();
        RoomBounds host = RoomGeometry.hostOf(tw, r.bounds());
        assertEquals(RoomGeometry.EffLay.UNDER, RoomGeometry.layMode(tw, host));
        assertEquals(40, RoomGeometry.towerBase(tw, r.bounds()), 1e-9, "под столом, на полу станка");

        RoomTower tall = RoomPlanFixture.tower(l, "tA");
        tall.setXCm(360);
        tall.setYCm(250);
        tall.setLay(RoomTower.Lay.UNDER);
        RoomPlanCalc.Result r2 = calc(l);
        assertTrue(level(r2, Level.ERROR).stream().anyMatch(w -> w.text().contains("не помещается под «Стол 180×60»")),
                level(r2, Level.ERROR).toString());
    }

    @Test
    void towerStraddlingATableWithoutAModeIsAnError() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomTower c = RoomPlanFixture.tower(l, "tC");
        c.setXCm(262);
        c.setYCm(250); // центр левее стола, но тело заходит на него
        RoomPlanCalc.Result r = calc(l);
        assertTrue(level(r, Level.ERROR).stream().anyMatch(w -> w.text().contains("пересекается с «Стол 180×60»")),
                level(r, Level.ERROR).toString());
    }

    @Test
    void tableHangingOffTheDecksIsAnErrorAndIsMarked() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomFurniture f2 = RoomPlanFixture.furniture(l, "f2");
        f2.setXCm(640);
        f2.setYCm(330); // 580..700 × 300..360: ряд станков кончается на 350
        RoomPlanCalc.Result r = calc(l);
        assertTrue(r.air().contains("f2"));
        assertTrue(level(r, Level.ERROR).stream()
                .anyMatch(w -> w.text().equals("Стол 120×60: висит в воздухе, часть опоры за краем станка")));
    }

    @Test
    void deckTableWithoutDecksUnderItIsAnError() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomFurniture d9 = RoomPlanFixture.furniture(l, "d9");
        d9.setXCm(100);
        d9.setYCm(100);
        RoomPlanCalc.Result r = calc(l);
        assertTrue(r.air().contains("d9"));
        assertTrue(level(r, Level.ERROR).stream()
                .anyMatch(w -> w.text().equals("Станок 1×0,5 м: висит в воздухе, под ним нет станков")));
    }

    @Test
    void deckTableStandsOnTopOfTheLowerDecks() {
        RoomPlanCalc.Result r = calc(RoomPlanFixture.sample());
        RoomBounds d9 = r.bounds().stream().filter(x -> x.id.equals("d9")).findFirst().orElseThrow();
        assertEquals(40, d9.base, 1e-9);
        assertEquals(80, d9.top, 1e-9);
        assertEquals(80, RoomGeometry.deckHeightAt(RoomGeometry.decks(r.bounds()), 650, 225), 1e-9);
    }

    @Test
    void eyeLineIsMeasuredFromTheDeckFloorUnderTheChairs() {
        RoomPlanCalc.Result r = calc(RoomPlanFixture.sample());
        List<RoomGeometry.EyeLevel> eyes = RoomGeometry.eyeLevels(r.bounds());
        assertEquals(1, eyes.size());
        assertEquals(40, eyes.get(0).deckHeightCm(), 1e-9);
        assertEquals(160, eyes.get(0).eyeHeightCm(), 1e-9, "120 см над полом станка");
        assertEquals(List.of(360.0, 530.0), eyes.get(0).xs());
    }

    @Test
    void withoutDecksTheEyeLineIs120FromTheGround() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        l.getFurniture().removeIf(f -> f.getRef().getId().startsWith("dk"));
        List<RoomGeometry.EyeLevel> eyes = RoomGeometry.eyeLevels(calc(l).bounds());
        assertEquals(120, eyes.get(0).eyeHeightCm(), 1e-9);
    }

    @Test
    void monitorOnTheTableGetsTheTableHeightAsSupport() {
        RoomPlanCalc.Result r = calc(RoomPlanFixture.sample());
        assertEquals(75, RoomGeometry.supportAt(r.bounds(), 315, 230), 1e-9);
        assertEquals(0, RoomGeometry.supportAt(r.bounds(), 50, 400), 1e-9);
    }

    @Test
    void depthLargerThanTheCaseIsAnErrorAndHeavyOnTopIsAWarning() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        // медиасервер (глубина 55) в кофр с внутренней глубиной 45 и тяжёлый блок в верхнюю половину
        RoomTower a = RoomPlanFixture.tower(l, "tA");
        com.vjstb.ledscheme.model.RoomRackItem ms = new com.vjstb.ledscheme.model.RoomRackItem();
        ms.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, "msrv4"));
        ms.setU0(2);
        a.getSegments().get(1).getItems().clear(); // верхний кофр 8U
        a.getSegments().get(1).getItems().add(ms);
        RoomPlanCalc.Result r = calc(l);
        assertTrue(level(r, Level.ERROR).stream().anyMatch(w -> w.text().startsWith("Медиасервер: глубина 55 см больше")));
        assertTrue(level(r, Level.WARNING).stream().anyMatch(w -> w.text().contains("в верхней половине")),
                level(r, Level.WARNING).toString());
    }

    @Test
    void looseBoxPicksTheSmallestCaseWithPackingMargin() {
        RoomPlanCalc.BoxCalc box = RoomPlanCalc.box(RoomPlanFixture.sample(), cat);
        assertEquals(11, box.count());
        assertNotNull(box.pick());
        assertEquals(35, box.pick()[0], 1e-9);
        assertFalse(box.fill() > 1);
    }

    @Test
    void unknownLibraryItemDoesNotBreakTheCalculation() {
        ControlRoomLayout l = RoomPlanFixture.sample();
        RoomFurniture ghost = new RoomFurniture();
        ghost.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, "deleted-from-library"));
        ghost.setXCm(50);
        ghost.setYCm(420);
        l.getFurniture().add(ghost);
        RoomPlanCalc.Result r = calc(l);
        assertEquals(3, r.totals().towers());
        assertFalse(RoomItemSpec.unknown(ghost.getRef()).complete(), "такая позиция помечена как без габаритов");
    }
}
