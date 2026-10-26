package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ModuleNumberingPattern.Axis;
import com.vjstb.ledscheme.model.ModuleNumberingPattern.Corner;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Имена К1…КN / БП1…БПn и «место» (D11, D15, D20, D21). Имена сквозные по экрану, считаются заново после любой правки
 * и подчиняются параметру Предпочтений; внутренний id, на котором держатся цепочки, при этом не меняется. На 4×4 модулях
 * стоят четыре карты 2×2 — по одной в углу, чтобы каждый вариант порядка давал однозначный ответ.
 */
class UnitNamesTest {

    private ModularFixture fixture;
    private ReceiverCardInstance tl;
    private ReceiverCardInstance tr;
    private ReceiverCardInstance bl;
    private ReceiverCardInstance br;

    private void fourCards() {
        fixture = new ModularFixture(4, 4);
        String type = fixture.cardType("К", 8, null).getId();
        // добавляем в «неудобном» порядке, чтобы имена не зависели от порядка списка
        br = fixture.addCard(type, 2, 2, 3, 3);
        tl = fixture.addCard(type, 0, 0, 1, 1);
        bl = fixture.addCard(type, 2, 0, 3, 1);
        tr = fixture.addCard(type, 0, 2, 1, 3);
    }

    private String name(ReceiverCardNumbering n, ReceiverCardInstance card) {
        return UnitNames.cardNames(fixture.screen, n).get(card.getId());
    }

    @Test
    void defaultOrderIsLeftToRightTopToBottom() {
        fourCards();
        assertEquals("К1", name(ReceiverCardNumbering.TL_ROWS, tl));
        assertEquals("К2", name(ReceiverCardNumbering.TL_ROWS, tr));
        assertEquals("К3", name(ReceiverCardNumbering.TL_ROWS, bl));
        assertEquals("К4", name(ReceiverCardNumbering.TL_ROWS, br));
        assertEquals(ReceiverCardNumbering.TL_ROWS, ReceiverCardNumbering.DEFAULT);
    }

    @Test
    void everyPreferenceVariantOrdersByItsStartCornerAndAxis() {
        fourCards();
        // порядок card → имя для каждого из восьми вариантов: [tl, tr, bl, br]
        Map<ReceiverCardNumbering, String[]> expected = Map.of(
                ReceiverCardNumbering.TL_ROWS, new String[]{"К1", "К2", "К3", "К4"},
                ReceiverCardNumbering.TR_ROWS, new String[]{"К2", "К1", "К4", "К3"},
                ReceiverCardNumbering.BL_ROWS, new String[]{"К3", "К4", "К1", "К2"},
                ReceiverCardNumbering.BR_ROWS, new String[]{"К4", "К3", "К2", "К1"},
                ReceiverCardNumbering.TL_COLUMNS, new String[]{"К1", "К3", "К2", "К4"},
                ReceiverCardNumbering.TR_COLUMNS, new String[]{"К3", "К1", "К4", "К2"},
                ReceiverCardNumbering.BL_COLUMNS, new String[]{"К2", "К4", "К1", "К3"},
                ReceiverCardNumbering.BR_COLUMNS, new String[]{"К4", "К2", "К3", "К1"});
        for (var e : expected.entrySet()) {
            ReceiverCardNumbering n = e.getKey();
            assertEquals(e.getValue()[0], name(n, tl), n + " tl");
            assertEquals(e.getValue()[1], name(n, tr), n + " tr");
            assertEquals(e.getValue()[2], name(n, bl), n + " bl");
            assertEquals(e.getValue()[3], name(n, br), n + " br");
        }
    }

    @Test
    void asModulesVariantFollowsTheScreenTemplateIgnoringSnake() {
        fourCards();
        fixture.screen.setModuleNumbering(new ModuleNumberingPattern(Corner.TOP_RIGHT, Axis.COLUMNS, true));
        assertEquals("К1", name(ReceiverCardNumbering.AS_MODULES, tr));
        assertEquals("К2", name(ReceiverCardNumbering.AS_MODULES, br));
        assertEquals("К3", name(ReceiverCardNumbering.AS_MODULES, tl));
        assertEquals("К4", name(ReceiverCardNumbering.AS_MODULES, bl));
    }

    @Test
    void namesStayConsecutiveAndIdsStayPutWhenACardDisappears() {
        fourCards();
        String idOfBr = br.getId();
        assertEquals("К4", name(ReceiverCardNumbering.TL_ROWS, br));

        ReceiverCardEditor.remove(fixture.screen, tl.getId());

        Map<String, String> names = UnitNames.cardNames(fixture.screen, ReceiverCardNumbering.TL_ROWS);
        assertEquals(3, names.size());
        assertEquals("К1", names.get(tr.getId()));
        assertEquals("К2", names.get(bl.getId()));
        assertEquals("К3", names.get(idOfBr), "без пропуска «К1»: имена сдвинулись, id нет");
    }

    @Test
    void cardAnchorIsItsModuleInTheStartCornerEvenForAnLShapedCard() {
        // Г-образная карта: в верхнем левом углу её описанного прямоугольника модуля нет
        ModularFixture f = new ModularFixture(2, 2);
        String type = f.cardType("К", 8, null).getId();
        ReceiverCardInstance l = new ReceiverCardInstance(type,
                java.util.List.of(f.id(0, 1), f.id(1, 0), f.id(1, 1)));
        f.screen.getReceiverCards().add(l);
        ReceiverCardInstance other = f.addCard(type, 0, 0, 0, 0);

        // от верхнего левого угла сначала идёт (0,0) — его карта К1; Г-образная — К2
        assertEquals("К1", UnitNames.cardNames(f.screen, ReceiverCardNumbering.TL_ROWS).get(other.getId()));
        assertEquals("К2", UnitNames.cardNames(f.screen, ReceiverCardNumbering.TL_ROWS).get(l.getId()));
    }

    @Test
    void placeDefaultsToTheModuleNearestTheCenterAndHonoursAnExplicitHost() {
        ModularFixture f = new ModularFixture(1, 5);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 4);

        assertEquals(f.id(0, 2), UnitNames.placeOf(f.screen, card));

        card.setHostModuleId(f.id(0, 4));
        assertEquals(f.id(0, 4), UnitNames.placeOf(f.screen, card));

        card.setHostModuleId("исчез");
        assertEquals(f.id(0, 2), UnitNames.placeOf(f.screen, card), "несуществующий хост — снова по умолчанию");
    }

    @Test
    void supplyNamesFollowThePlaceInTheSameOrderParameter() {
        ModularFixture f = new ModularFixture(2, 4);
        String type = f.psuType("БП", 8, null, false).getId();
        PowerSupplyInstance right = f.addSupply(type, 0, 2, 1, 3);
        PowerSupplyInstance left = f.addSupply(type, 0, 0, 1, 1);

        Map<String, String> names = UnitNames.supplyNames(f.screen, ReceiverCardNumbering.TL_ROWS);
        assertEquals("БП1", names.get(left.getId()));
        assertEquals("БП2", names.get(right.getId()));

        names = UnitNames.supplyNames(f.screen, ReceiverCardNumbering.TR_ROWS);
        assertEquals("БП1", names.get(right.getId()));
        assertEquals("БП2", names.get(left.getId()));

        // место блока двигает его в порядке: левый блок ставим на нижний ряд, и он уходит за правым
        left.setHostModuleId(f.id(1, 1));
        right.setHostModuleId(f.id(0, 2));
        names = UnitNames.supplyNames(f.screen, ReceiverCardNumbering.TL_ROWS);
        assertEquals("БП1", names.get(right.getId()));
        assertEquals("БП2", names.get(left.getId()));
    }
}
