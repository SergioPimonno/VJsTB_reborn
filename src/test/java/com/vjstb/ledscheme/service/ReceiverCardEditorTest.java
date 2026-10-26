package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Назначение приёмных карт без UI (решения D8–D11): ручное и автоматическое, правило замены (карта никогда не
 * перекрывает другую; задетая заменяется целиком, её модули вне выделения остаются «без карты»), повторная протяжка
 * без последствий, место и ручной порядок. Главное, что здесь стережётся, — инвариант «модуль максимум в одной карте»:
 * на нём держатся и нумерация, и цепочки расключения.
 */
class ReceiverCardEditorTest {

    private static List<Integer> sizes(ModularFixture f) {
        List<Integer> out = new ArrayList<>();
        for (ReceiverCardInstance c : f.screen.getReceiverCards()) {
            out.add(c.getModuleIds().size());
        }
        out.sort(null);
        return out;
    }

    @Test
    void manualDragMakesOneCardOfTheSelectedTypeInReadingOrder() {
        ModularFixture f = new ModularFixture(3, 4);
        ReceiverCardType t = f.cardType("A8", 8, null);

        EditReport r = ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 2), t.getId());

        assertFalse(r.noop());
        assertEquals(1, f.screen.getReceiverCards().size());
        ReceiverCardInstance card = f.screen.getReceiverCards().get(0);
        assertEquals(t.getId(), card.getReceiverCardTypeId());
        assertEquals(List.of(f.id(0, 0), f.id(0, 1), f.id(0, 2), f.id(1, 0), f.id(1, 1), f.id(1, 2)),
                card.getModuleIds());
        assertEquals(List.of(card.getId()), r.createdIds());
        assertEquals("", r.message(), "ничего не заменено — строки замен нет");
    }

    @Test
    void dragBackwardsOrOutsideTheGridStillSelectsExistingModules() {
        ModularFixture f = new ModularFixture(2, 2);
        String t = f.cardType("A8", 8, null).getId();

        // протяжка справа налево и за край сетки
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(1, 5, 0, 1), t);

        assertEquals(List.of(2), sizes(f), "попали только существующие модули (0,1) и (1,1)");
    }

    @Test
    void ordinaryCabinetsOfAMixedScreenAreNotPutIntoCards() {
        ModularFixture f = new ModularFixture(2, 3);
        f.makeOrdinary(0, 2);
        f.makeOrdinary(1, 2);
        String t = f.cardType("A8", 8, null).getId();

        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 2), t);

        assertEquals(List.of(4), sizes(f), "обычные кабинеты — самостоятельные звенья, не модули (D3)");
    }

    @Test
    void autoSplitMakesFullCardsAndSmallerOnesForTheRemainder() {
        ModularFixture f = new ModularFixture(3, 5);
        String t = f.cardType("A4", 4, null).getId();

        EditReport r = ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(0, 0, 2, 4), t, 2, 2);

        // строки 0–1 и 2; столбцы 0–1, 2–3 и 4 → 4 + 4 + 2 + 2 + 2 + 1
        assertEquals(List.of(1, 2, 2, 2, 4, 4), sizes(f));
        assertEquals(6, r.createdIds().size());
        // каждый модуль ровно в одной карте
        Set<String> all = new HashSet<>();
        for (ReceiverCardInstance c : f.screen.getReceiverCards()) {
            for (String id : c.getModuleIds()) {
                assertTrue(all.add(id), "модуль в двух картах: " + id);
            }
        }
        assertEquals(15, all.size());
    }

    @Test
    void autoSplitRejectsAZeroSize() {
        ModularFixture f = new ModularFixture(2, 2);
        String t = f.cardType("A4", 4, null).getId();
        assertThrows(IllegalArgumentException.class,
                () -> ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 1), t, 0, 2));
    }

    @Test
    void anotherCardReplacesAHitOneAndItsModulesOutsideTheSelectionAreLeftWithoutACard() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t);
        String oldId = f.cardAt(0, 0).getId();

        // новая карта задевает столбец 1 старой: старая заменяется целиком, её столбец 0 — «без карты»
        EditReport r = ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 1, 1, 2), t);

        assertEquals(1, r.replaced());
        assertEquals(2, r.orphaned());
        assertEquals("Заменено карт: 1, модулей без карты: 2", r.message());
        assertEquals(List.of(oldId), r.removedIds());
        assertNull(f.cardAt(0, 0));
        assertNull(f.cardAt(1, 0));
        assertEquals(1, f.screen.getReceiverCards().size());
        List<String> without = ReceiverCardChecks.check(f.screen, f.ws).modulesWithoutCard();
        assertTrue(without.containsAll(List.of(f.id(0, 0), f.id(1, 0))), "оранжевые модули вне новой карты");
        assertEquals(4, without.size(), "левый столбец старой карты и правый столбец экрана");
    }

    @Test
    void coveringACardCompletelyReplacesItWithoutOrphans() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t);

        EditReport r = ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 3), t);

        assertEquals(1, r.replaced());
        assertEquals(0, r.orphaned());
        assertEquals("Заменено карт: 1", r.message());
        assertEquals(List.of(8), sizes(f));
    }

    @Test
    void draggingAgainOverTheSameCardWithTheSameTypeDoesNothing() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t);
        String id = f.cardAt(0, 0).getId();

        EditReport again = ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t);

        assertTrue(again.noop());
        assertEquals(id, f.cardAt(0, 0).getId(), "id карты (и её звено в цепочке) не меняется");
        assertEquals(1, f.screen.getReceiverCards().size());
    }

    @Test
    void draggingOverTheSameCardWithAnotherTypeReplacesIt() {
        ModularFixture f = new ModularFixture(2, 2);
        String a = f.cardType("A", 8, null).getId();
        String b = f.cardType("B", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), a);
        String id = f.cardAt(0, 0).getId();

        EditReport r = ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), b);

        assertFalse(r.noop());
        assertNotEquals(id, f.cardAt(0, 0).getId());
        assertEquals(b, f.cardAt(0, 0).getReceiverCardTypeId());
    }

    @Test
    void repeatedAutoDragGivingTheSameCardsKeepsThem() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.cardType("A4", 4, null).getId();
        ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t, 2, 2);
        List<String> ids = new ArrayList<>();
        f.screen.getReceiverCards().forEach(c -> ids.add(c.getId()));

        EditReport again = ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t, 2, 2);

        assertTrue(again.noop());
        List<String> after = new ArrayList<>();
        f.screen.getReceiverCards().forEach(c -> after.add(c.getId()));
        assertEquals(ids, after);

        EditReport other = ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t, 4, 2);
        assertFalse(other.noop(), "другой размер — это уже новая раскладка");
        assertEquals(List.of(8), sizes(f));
    }

    @Test
    void whateverTheSequenceOfDragsAModuleNeverBelongsToTwoCards() {
        ModularFixture f = new ModularFixture(6, 8);
        String t = f.cardType("A8", 8, null).getId();
        Random rnd = new Random(42);
        for (int i = 0; i < 60; i++) {
            int r0 = rnd.nextInt(6);
            int c0 = rnd.nextInt(8);
            int r1 = Math.min(5, r0 + rnd.nextInt(4));
            int c1 = Math.min(7, c0 + rnd.nextInt(4));
            if (rnd.nextBoolean()) {
                ReceiverCardEditor.assignManual(f.screen, f.ws, rect(r0, c0, r1, c1), t);
            } else {
                ReceiverCardEditor.assignAuto(f.screen, f.ws, rect(r0, c0, r1, c1), t, 1 + rnd.nextInt(3), 1 + rnd.nextInt(3));
            }
            if (i % 7 == 0 && !f.screen.getReceiverCards().isEmpty()) {
                ReceiverCardEditor.remove(f.screen, f.screen.getReceiverCards().get(0).getId());
            }
            Set<String> seen = new HashSet<>();
            for (ReceiverCardInstance c : f.screen.getReceiverCards()) {
                assertFalse(c.getModuleIds().isEmpty(), "пустая карта на шаге " + i);
                for (String id : c.getModuleIds()) {
                    assertTrue(seen.add(id), "модуль в двух картах на шаге " + i);
                }
            }
        }
    }

    @Test
    void removingACardLeavesItsModulesWithoutACard() {
        ModularFixture f = new ModularFixture(1, 3);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 2), t);
        String id = f.cardAt(0, 0).getId();

        EditReport r = ReceiverCardEditor.remove(f.screen, id);

        assertEquals("Карта снята, модулей без карты: 3", r.message());
        assertEquals(List.of(id), r.removedIds());
        assertTrue(f.screen.getReceiverCards().isEmpty());
        assertTrue(ReceiverCardEditor.remove(f.screen, id).noop(), "повторное снятие ничего не делает");
    }

    @Test
    void hostIsNearestToCenterByDefaultAndMustBeAModuleOfTheCard() {
        ModularFixture f = new ModularFixture(1, 5);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 4), t);
        ReceiverCardInstance card = f.cardAt(0, 0);

        assertEquals(f.id(0, 2), UnitNames.placeOf(f.screen, card));
        ReceiverCardEditor.setHost(f.screen, card.getId(), f.id(0, 0));
        assertEquals(f.id(0, 0), UnitNames.placeOf(f.screen, card));
        ReceiverCardEditor.resetHost(f.screen, card.getId());
        assertEquals(f.id(0, 2), UnitNames.placeOf(f.screen, card));

        assertThrows(IllegalArgumentException.class,
                () -> ReceiverCardEditor.setHost(f.screen, card.getId(), "чужой"));
    }

    @Test
    void manualOrderIsBuiltClickByClickAndResetsToTheTemplate() {
        ModularFixture f = new ModularFixture(1, 3);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 2), t);
        ReceiverCardInstance card = f.cardAt(0, 0);

        ReceiverCardEditor.setManualOrder(f.screen, card.getId(), List.of());
        assertEquals(3, ModuleNumbering.unnumbered(card, f.screen).size(), "режим включён, ничего не кликнуто");

        ReceiverCardEditor.appendManualOrder(f.screen, card.getId(), f.id(0, 2));
        ReceiverCardEditor.appendManualOrder(f.screen, card.getId(), f.id(0, 0));
        ReceiverCardEditor.appendManualOrder(f.screen, card.getId(), f.id(0, 2)); // повторный клик — ничего
        ReceiverCardEditor.appendManualOrder(f.screen, card.getId(), "чужой");
        assertEquals(List.of(f.id(0, 2), f.id(0, 0)), ModuleNumbering.order(card, f.screen));
        assertEquals(List.of(f.id(0, 1)), ModuleNumbering.unnumbered(card, f.screen));

        ReceiverCardEditor.resetOrder(f.screen, card.getId());
        assertNull(card.getManualPortOrder());
        assertEquals(List.of(f.id(0, 0), f.id(0, 1), f.id(0, 2)), ModuleNumbering.order(card, f.screen));
    }

    @Test
    void patternIsStoredAsACopy() {
        ModularFixture f = new ModularFixture(1, 1);
        ModuleNumberingPattern p = new ModuleNumberingPattern(ModuleNumberingPattern.Corner.BOTTOM_RIGHT,
                ModuleNumberingPattern.Axis.COLUMNS, true);
        ReceiverCardEditor.setPattern(f.screen, p);
        p.setSnake(false);
        assertTrue(f.screen.getModuleNumbering().isSnake());
        assertEquals(ModuleNumberingPattern.Corner.BOTTOM_RIGHT, f.screen.getModuleNumbering().getCorner());
    }
}
