package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SignalChain;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Звенья цепочек — единственный способ развернуть id из {@code cabinetInstanceIds} (D3, D24–D26). Главное, что здесь
 * стережётся: на экране без модулей звенья — РОВНО прежние кабинеты (регрессия обязана быть нулевой, ведь на этот класс
 * потом переведут весь сигнал и питание), а на модульном — карты/блоки и обычные кабинеты, но никогда сам модуль.
 */
class ChainUnitsTest {

    @Test
    void screenWithoutModulesGivesExactlyTheOldVisibleCabinets() {
        ModularFixture f = new ModularFixture(2, 3);
        // делаем экран обычным: тип экрана и всех ячеек без флага модуля
        f.screen.setCabinetTypeId(f.ordinary.getId());
        f.cab(0, 1).setHidden(true);
        ChainUnits cu = ChainUnits.of(f.screen, f.ws);

        assertFalse(cu.isModular());
        List<String> expected = new ArrayList<>();
        for (CabinetInstance c : f.screen.getCabinets()) {
            if (!c.isHidden()) {
                expected.add(c.getId());
            }
        }
        for (ChainUnits.Mode mode : ChainUnits.Mode.values()) {
            List<String> got = new ArrayList<>();
            for (ChainUnits.Unit u : cu.units(mode)) {
                assertEquals(ChainUnits.Kind.CABINET, u.kind());
                got.add(u.id());
            }
            assertEquals(expected, got, "режим " + mode + ": порядок и состав как у списка кабинетов экрана");
        }
        ChainUnits.Unit u = cu.unitOf(f.id(1, 2));
        assertNotNull(u);
        assertEquals(128 * 128, cu.pixels(u));
        assertEquals(100.0, cu.watts(u), 1e-9);
        assertEquals(1, cu.cabinetsOf(u).size());
        // центр кабинета: 2 * 250 + 125, 1 * 250 + 125
        assertArrayEquals(new double[]{625, 375}, cu.anchor(u), 1e-9);
        assertNull(cu.unitOf(f.id(0, 1)), "скрытая ячейка — не звено");
        assertNull(cu.unitOf("чужой"));
    }

    @Test
    void screenWithCardsInSignalUsesCardsAndOrdinaryCabinetsAndNeverTheModulesThemselves() {
        ModularFixture f = new ModularFixture(2, 4);
        f.makeOrdinary(0, 3);
        f.makeOrdinary(1, 3);
        String ct = f.cardType("К", 8, null).getId();
        ReceiverCardInstance right = f.addCard(ct, 0, 2, 1, 2);
        ReceiverCardInstance left = f.addCard(ct, 0, 0, 1, 1);
        ChainUnits cu = ChainUnits.of(f.screen, f.ws);

        assertTrue(cu.isModular());
        List<ChainUnits.Unit> units = cu.units(ChainUnits.Mode.SIGNAL);
        assertEquals(4, units.size());
        assertEquals(left.getId(), units.get(0).id());
        assertEquals("К1", units.get(0).name());
        assertEquals(right.getId(), units.get(1).id());
        assertEquals("К2", units.get(1).name());
        assertEquals(ChainUnits.Kind.CABINET, units.get(2).kind());
        assertEquals(f.id(0, 3), units.get(2).id());
        assertNull(cu.unitOf(f.id(0, 0)), "модуль звеном не бывает (D26)");
        assertSame(units.get(0).card(), left);
    }

    @Test
    void cardPixelsAreTheSumOfItsModulesAndTheAnchorIsTheirCentroid() {
        ModularFixture f = new ModularFixture(2, 3);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 1, 2);
        ChainUnits cu = ChainUnits.of(f.screen, f.ws);
        ChainUnits.Unit u = cu.unitOf(card.getId());

        assertEquals(6 * 64 * 64, cu.pixels(u));
        assertEquals(6, cu.cabinetsOf(u).size());
        assertEquals(6 * 30.0, cu.watts(u), 1e-9, "у карты мощность — сумма модулей");
        // центр масс 2×3 модулей: x = (125 + 375 + 625) / 3 = 375, y = (125 + 375) / 2 = 250
        assertArrayEquals(new double[]{375, 250}, cu.anchor(u), 1e-9);
    }

    @Test
    void powerUnitsAreSuppliesWithRatedPowerAndTheirPlaceAsAnchor() {
        ModularFixture f = new ModularFixture(1, 4);
        f.makeOrdinary(0, 3);
        PowerSupplyType t = f.psuType("БП", 8, null, false);
        PowerSupplyInstance s = f.addSupply(t.getId(), 0, 0, 0, 2);
        s.setHostModuleId(f.id(0, 0));
        ChainUnits cu = ChainUnits.of(f.screen, f.ws);

        List<ChainUnits.Unit> units = cu.units(ChainUnits.Mode.POWER);
        assertEquals(2, units.size(), "блок и обычный кабинет; карты в питании не звенья");
        ChainUnits.Unit su = units.get(0);
        assertEquals(ChainUnits.Kind.POWER_SUPPLY, su.kind());
        assertEquals("БП1", su.name());
        assertEquals(300.0, cu.watts(su), 1e-9, "нагрузка фазы — по номиналу блока (D22)");
        assertArrayEquals(new double[]{125, 125}, cu.anchor(su), 1e-9, "линия цепочки идёт в место блока");

        t.setRatedPowerW(null);
        assertEquals(90.0, cu.watts(su), 1e-9, "номинал не указан — сумма потребления модулей");
    }

    @Test
    void modulesWithoutACardOrBlockAreNotUnits() {
        ModularFixture f = new ModularFixture(1, 3);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 0);
        ChainUnits cu = ChainUnits.of(f.screen, f.ws);

        assertEquals(List.of(card.getId()), cu.units(ChainUnits.Mode.SIGNAL).stream().map(ChainUnits.Unit::id).toList());
        assertTrue(cu.units(ChainUnits.Mode.POWER).isEmpty(), "блоков нет — звеньев питания тоже");
    }

    @Test
    void findLooksThroughAllScreensOfTheScene() {
        ModularFixture a = new ModularFixture(1, 2);
        ModularFixture b = new ModularFixture(1, 2);
        // экран b живёт в рабочем пространстве a: тип модуля берётся оттуда
        b.screen.setCabinetTypeId(a.module.getId());
        ReceiverCardInstance card = new ReceiverCardInstance("т", List.of(b.id(0, 0)));
        b.screen.getReceiverCards().add(card);
        Scene scene = new Scene();
        scene.getScreens().add(a.screen);
        scene.getScreens().add(b.screen);

        ChainUnits.Unit u = ChainUnits.find(scene, a.ws, card.getId());

        assertNotNull(u);
        assertSame(b.screen, u.screen());
        assertNull(ChainUnits.find(scene, a.ws, "нет"));
    }

    @Test
    void pruneUnknownDropsDeadIdsButKeepsPortsAndPhases() {
        ModularFixture f = new ModularFixture(1, 3);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 1);
        Scene scene = new Scene();
        scene.getScreens().add(f.screen);
        SignalChain signal = new SignalChain(1, false, List.of(card.getId(), "карта-старого-клиента", f.id(0, 2)));
        SignalChain onlyDead = new SignalChain(2, false, List.of("мёртвая"));
        PowerChain power = new PowerChain(2, List.of("блок-которого-нет"));
        scene.getSignalChains().add(signal);
        scene.getSignalChains().add(onlyDead);
        scene.getPowerChains().add(power);

        int removed = ChainUnits.pruneUnknown(scene);

        assertEquals(3, removed);
        assertEquals(List.of(card.getId(), f.id(0, 2)), signal.getCabinetInstanceIds());
        assertEquals(2, scene.getSignalChains().size(), "опустевшая цепочка остаётся за своим портом");
        assertEquals(2, scene.getSignalChains().get(1).getPortNumber());
        assertTrue(power.getCabinetInstanceIds().isEmpty());
        assertEquals(2, power.getPhase());
    }
}
