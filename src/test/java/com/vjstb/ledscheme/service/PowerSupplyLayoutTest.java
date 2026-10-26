package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Раскладка выходов блока (D17–D21) и замечания по блокам. Порядок шлейфов — сначала карта (если она не на «К»), затем
 * модули в порядке чтения: по этим номерам техник подключает кабели питания, поэтому порядок проверяется явно.
 * Расстояния — по прямой между центрами модулей, шаг 250 мм.
 */
class PowerSupplyLayoutTest {

    private static PowerSupplyLayout.Result layout(ModularFixture f) {
        return PowerSupplyLayout.compute(f.screen, f.ws, ReceiverCardNumbering.DEFAULT);
    }

    @Test
    void cardTakesTheFirstLoopThenModulesFollowInReadingOrder() {
        ModularFixture f = new ModularFixture(2, 2);
        PowerSupplyType t = f.psuType("БП", 8, null, false);
        PowerSupplyInstance s = f.addSupply(t.getId(), 0, 0, 1, 1);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 1, 1);
        card.setHostModuleId(f.id(0, 1));

        PowerSupplyLayout.Supply sup = layout(f).ofSupply(s.getId());

        assertEquals("БП1", sup.name());
        assertEquals(1, sup.cards().size());
        assertFalse(sup.cards().get(0).dedicated());
        assertEquals(1, sup.cards().get(0).output(), "карта без «К» забирает шлейф №1");
        assertEquals(2, sup.outputOf(f.id(0, 0)));
        assertEquals(3, sup.outputOf(f.id(0, 1)));
        assertEquals(4, sup.outputOf(f.id(1, 0)));
        assertEquals(5, sup.outputOf(f.id(1, 1)));
        assertEquals(5, sup.used());
        assertEquals(0, sup.over());
        assertTrue(sup.feedsCards());
    }

    @Test
    void dedicatedOutputKeepsAllLoopsForModulesAndTakesOnlyTheFirstCard() {
        ModularFixture f = new ModularFixture(2, 2);
        PowerSupplyType t = f.psuType("БП", 8, null, true);
        PowerSupplyInstance s = f.addSupply(t.getId(), 0, 0, 1, 1);
        String ct = f.cardType("К", 8, null).getId();
        ReceiverCardInstance first = f.addCard(ct, 0, 0, 0, 0);
        ReceiverCardInstance second = f.addCard(ct, 1, 1, 1, 1);

        PowerSupplyLayout.Supply sup = layout(f).ofSupply(s.getId());

        assertEquals(2, sup.cards().size());
        assertEquals(first.getId(), sup.cards().get(0).cardId(), "порядок карт — по нумерации К1, К2");
        assertTrue(sup.cards().get(0).dedicated());
        assertEquals("К", sup.cards().get(0).label());
        assertFalse(sup.cards().get(1).dedicated(), "второй карте «К» не хватило — принудительный шлейф");
        assertEquals(second.getId(), sup.cards().get(1).cardId());
        assertEquals(1, sup.cards().get(1).output());
        assertEquals(2, sup.outputOf(f.id(0, 0)));
        assertEquals(5, sup.used());
    }

    @Test
    void overloadIsUsedLoopsAboveOutputsAndIgnoredWhenOutputsAreUnknown() {
        ModularFixture f = new ModularFixture(1, 4);
        PowerSupplyType t = f.psuType("БП", 4, null, false);
        PowerSupplyInstance s = f.addSupply(t.getId(), 0, 0, 0, 3);
        f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 3);

        assertEquals(1, layout(f).ofSupply(s.getId()).over(), "4 модуля + принудительный шлейф карты = 5 из 4");
        assertTrue(layout(f).ofSupply(s.getId()).hasProblem());

        t.setCardOutput(true);
        assertEquals(0, layout(f).ofSupply(s.getId()).over(), "с «К» карта шлейфов не занимает");

        t.setCardOutput(false);
        t.setOutputs(null);
        assertEquals(0, layout(f).ofSupply(s.getId()).over(), "число шлейфов не указано — не проверяем");
    }

    @Test
    void cableLongerThanTheTypeAllowsIsMeasuredFromThePlace() {
        ModularFixture f = new ModularFixture(1, 5);
        PowerSupplyType t = f.psuType("БП", 8, 600.0, false);
        PowerSupplyInstance s = f.addSupply(t.getId(), 0, 0, 0, 4);

        assertTrue(layout(f).ofSupply(s.getId()).longModuleIds().isEmpty(),
                "место в центре: до крайних модулей 500 мм");

        s.setHostModuleId(f.id(0, 0));
        assertEquals(List.of(f.id(0, 3), f.id(0, 4)), layout(f).ofSupply(s.getId()).longModuleIds(),
                "от крайнего модуля 750 и 1000 мм");

        t.setCableLengthMm(750.0);
        assertEquals(List.of(f.id(0, 4)), layout(f).ofSupply(s.getId()).longModuleIds(), "ровно 750 — ещё допустимо");

        t.setCableLengthMm(null);
        assertTrue(layout(f).ofSupply(s.getId()).longModuleIds().isEmpty(), "длина не указана — не проверяем");
    }

    @Test
    void cardWhosePlaceIsOutsideEveryBlockIsNotPowered() {
        ModularFixture f = new ModularFixture(1, 4);
        PowerSupplyType t = f.psuType("БП", 8, null, false);
        f.addSupply(t.getId(), 0, 0, 0, 1);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 3);
        card.setHostModuleId(f.id(0, 3)); // место вне области блока

        PowerSupplyLayout.Result r = layout(f);
        assertNull(r.ofCard(card.getId()));
        assertEquals(List.of(card.getId()),
                PowerSupplyChecks.check(f.screen, f.ws, ReceiverCardNumbering.DEFAULT).cardsWithoutSupply());

        card.setHostModuleId(f.id(0, 1));
        assertEquals(r.ofModule(f.id(0, 0)).instance().getId(),
                layout(f).ofCard(card.getId()).instance().getId(), "место в области блока — блок питает карту");
    }

    @Test
    void checksListModulesWithoutPowerAndProblemBlocks() {
        ModularFixture f = new ModularFixture(1, 4);
        PowerSupplyType t = f.psuType("БП", 2, null, false);
        f.addSupply(t.getId(), 0, 0, 0, 2);

        PowerSupplyChecks.Result r = PowerSupplyChecks.check(f.screen, f.ws, ReceiverCardNumbering.DEFAULT);

        assertEquals(List.of(f.id(0, 3)), r.modulesWithoutSupply());
        assertEquals(1, r.problems().size());
        assertEquals("БП1", r.problems().get(0).name());
        assertEquals(1, r.problems().get(0).over(), "3 модуля на 2 шлейфа");
        assertFalse(r.ok());
    }

    @Test
    void blockWithAnUnknownTypeIsLaidOutButNotChecked() {
        ModularFixture f = new ModularFixture(1, 3);
        PowerSupplyInstance s = f.addSupply("нет-такого", 0, 0, 0, 2);

        PowerSupplyLayout.Supply sup = layout(f).ofSupply(s.getId());

        assertNull(sup.type());
        assertNull(sup.outputs());
        assertEquals(3, sup.used());
        assertFalse(sup.hasProblem());
    }
}
