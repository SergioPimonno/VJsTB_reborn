package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Замечания по приёмным картам (D5, D12, D13, D16). Числа типа необязательны: {@code null} — «не указано, не
 * проверять», и тест на это стоит рядом с каждой проверкой, иначе библиотечная карта без паспортных данных
 * заливала бы экран красным. Модуль 250 мм: соседи — 250, диагональ — 353,6.
 */
class ReceiverCardChecksTest {

    @Test
    void modulesWithoutACardAreListedInReadingOrder() {
        ModularFixture f = new ModularFixture(2, 2);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 0), t);

        ReceiverCardChecks.Result r = ReceiverCardChecks.check(f.screen, f.ws);

        assertEquals(List.of(f.id(0, 1), f.id(1, 0), f.id(1, 1)), r.modulesWithoutCard());
        assertFalse(r.ok());
    }

    @Test
    void ordinaryCabinetsAreNeverReportedAsWithoutACard() {
        ModularFixture f = new ModularFixture(1, 3);
        f.makeOrdinary(0, 2);
        String t = f.cardType("A8", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 2), t);

        ReceiverCardChecks.Result r = ReceiverCardChecks.check(f.screen, f.ws);

        assertTrue(r.modulesWithoutCard().isEmpty());
        assertTrue(r.ok());
    }

    @Test
    void moreModulesThanOutputsMarksTheNumbersBeyondTheLimit() {
        ModularFixture f = new ModularFixture(1, 5);
        ReceiverCardType t = f.cardType("A4", 4, null);
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 4), t.getId());
        ReceiverCardInstance card = f.cardAt(0, 0);

        ReceiverCardChecks.CardCheck c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());

        assertEquals(5, c.moduleCount());
        assertEquals(4, c.outputs());
        assertTrue(c.overOutputs(), "«5/4» в списке карт");
        assertEquals(List.of(f.id(0, 4)), c.overLimitModuleIds(), "красным — номер 5");

        t.setModuleOutputs(null);
        c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());
        assertFalse(c.overOutputs(), "лимит не указан — не проверяем");
        assertTrue(c.overLimitModuleIds().isEmpty());
    }

    @Test
    void ribbonLongerThanTheTypeAllowsIsMeasuredStraightFromThePlace() {
        ModularFixture f = new ModularFixture(1, 5);
        ReceiverCardType t = f.cardType("A8", 8, 600.0);
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 4), t.getId());
        ReceiverCardInstance card = f.cardAt(0, 0);

        // место по умолчанию — средний модуль: до крайних 500 мм
        assertEquals(0, ReceiverCardChecks.check(f.screen, f.ws).card(card.getId()).longRibbonCount());

        // место на крайнем модуле: до модулей через 3 и 4 шага — 750 и 1000 мм
        ReceiverCardEditor.setHost(f.screen, card.getId(), f.id(0, 0));
        ReceiverCardChecks.CardCheck c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());
        assertEquals(List.of(f.id(0, 3), f.id(0, 4)), c.longRibbonModuleIds());
        assertEquals(2, c.longRibbonCount());

        t.setRibbonLengthMm(null);
        assertEquals(0, ReceiverCardChecks.check(f.screen, f.ws).card(card.getId()).longRibbonCount(),
                "длина шлейфа не указана — не проверяем");
    }

    @Test
    void ribbonLengthIsStraightLineSoADiagonalCounts() {
        ModularFixture f = new ModularFixture(3, 3);
        ReceiverCardType t = f.cardType("A9", 9, 600.0);
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 2, 2), t.getId());
        ReceiverCardInstance card = f.cardAt(0, 0);
        ReceiverCardEditor.setHost(f.screen, card.getId(), f.id(0, 0));

        // (0,0) → (2,2): sqrt(500² + 500²) = 707 мм > 600, а (1,1) — 353 мм
        List<String> longOnes = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId()).longRibbonModuleIds();
        assertTrue(longOnes.contains(f.id(2, 2)));
        assertFalse(longOnes.contains(f.id(2, 1)), "hypot(500, 250) = 559 мм укладывается в 600");
        assertFalse(longOnes.contains(f.id(1, 1)));
    }

    @Test
    void pixelLimitsOfTheTypeAreCheckedAgainstTheBoundingRectangleAndTotal() {
        ModularFixture f = new ModularFixture(2, 3);
        ReceiverCardType t = f.cardType("A8", 8, null);
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 2), t.getId());
        ReceiverCardInstance card = f.cardAt(0, 0);

        ReceiverCardChecks.CardCheck c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());
        assertEquals(192, c.resolutionWidthPx());
        assertEquals(128, c.resolutionHeightPx());
        assertEquals(6 * 64 * 64, c.pixels());
        assertFalse(c.widthExceeded() || c.heightExceeded() || c.pixelsExceeded());

        t.setMaxWidthPx(128);
        t.setMaxHeightPx(128);
        t.setMaxPixels(20000);
        c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());
        assertTrue(c.widthExceeded());
        assertFalse(c.heightExceeded());
        assertTrue(c.pixelsExceeded());
        assertTrue(c.hasProblem());
    }

    @Test
    void cardWithAnUnknownTypeIsNotChecked() {
        ModularFixture f = new ModularFixture(1, 3);
        ReceiverCardInstance card = f.addCard("нет-такого-типа", 0, 0, 0, 2);

        ReceiverCardChecks.CardCheck c = ReceiverCardChecks.check(f.screen, f.ws).card(card.getId());

        assertNotNull(c);
        assertFalse(c.hasProblem());
        assertEquals(3, c.moduleCount());
    }
}
