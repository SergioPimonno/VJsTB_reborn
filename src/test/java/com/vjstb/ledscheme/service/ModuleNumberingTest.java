package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ModuleNumberingPattern.Axis;
import com.vjstb.ledscheme.model.ModuleNumberingPattern.Corner;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Номер модуля в приёмной карте = номер её выхода (решения D6–D8, D14 плана receiver-cards). Эти тесты стерегут
 * порт функций {@code seq}/{@code ports} прототипа: восемь шаблонов начинаются в своём углу и обходят прямоугольник
 * без пропусков и повторов, «змейка» разворачивает каждую вторую строку/столбец, а вырезы и модули чужих карт
 * пропускаются так, что номера идут подряд — иначе техник подключит шлейфы не туда, куда показала схема.
 */
class ModuleNumberingTest {

    private static String show(List<int[]> seq) {
        StringBuilder sb = new StringBuilder();
        for (int[] rc : seq) {
            sb.append('(').append(rc[0]).append(',').append(rc[1]).append(')');
        }
        return sb.toString();
    }

    @Test
    void everyOfTheEightTemplatesStartsInItsCornerAndCoversEachCellOnce() {
        // прямоугольник 3 в ширину × 2 в высоту; ожидаемый первый модуль по углу: {строка, столбец}
        int[][] firstByCorner = {{0, 0}, {0, 2}, {1, 0}, {1, 2}};
        List<ModuleNumberingPattern> palette = ModuleNumberingPattern.palette();
        assertEquals(8, palette.size());
        for (ModuleNumberingPattern p : palette) {
            List<int[]> seq = ModuleNumbering.sequence(3, 2, p);
            assertEquals(6, seq.size());
            Set<String> seen = new HashSet<>();
            for (int[] rc : seq) {
                assertTrue(seen.add(rc[0] + ":" + rc[1]), "клетка не должна повторяться: " + show(seq));
            }
            assertArrayEquals(firstByCorner[p.getCorner().ordinal()], seq.get(0), p.getCorner() + " " + p.getAxis());
        }
    }

    @Test
    void rowsAndColumnsWalkInTheDirectionTheNameSays() {
        assertEquals("(0,0)(0,1)(0,2)(1,0)(1,1)(1,2)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.TOP_LEFT, Axis.ROWS, false))));
        assertEquals("(0,2)(0,1)(0,0)(1,2)(1,1)(1,0)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.TOP_RIGHT, Axis.ROWS, false))));
        assertEquals("(1,0)(1,1)(1,2)(0,0)(0,1)(0,2)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.BOTTOM_LEFT, Axis.ROWS, false))));
        assertEquals("(0,0)(1,0)(0,1)(1,1)(0,2)(1,2)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.TOP_LEFT, Axis.COLUMNS, false))));
        assertEquals("(1,2)(0,2)(1,1)(0,1)(1,0)(0,0)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.BOTTOM_RIGHT, Axis.COLUMNS, false))));
    }

    @Test
    void snakeTurnsEveryOtherRowAndColumnAround() {
        assertEquals("(0,0)(0,1)(0,2)(1,2)(1,1)(1,0)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.TOP_LEFT, Axis.ROWS, true))));
        assertEquals("(0,0)(1,0)(2,0)(2,1)(1,1)(0,1)",
                show(ModuleNumbering.sequence(2, 3, new ModuleNumberingPattern(Corner.TOP_LEFT, Axis.COLUMNS, true))));
        // змейка от нижнего правого угла: первая строка справа налево, вторая — слева направо
        assertEquals("(1,2)(1,1)(1,0)(0,0)(0,1)(0,2)",
                show(ModuleNumbering.sequence(3, 2, new ModuleNumberingPattern(Corner.BOTTOM_RIGHT, Axis.ROWS, true))));
    }

    @Test
    void cutoutsAndForeignModulesAreSkippedWithoutGapsInNumbers() {
        ModularFixture f = new ModularFixture(2, 3);
        String type = f.cardType("К", 8, null).getId();
        // карта A забрала всё, кроме (0,1): оно принадлежит другой карте B
        ReceiverCardInstance a = f.addCard(type, 0, 0, 0, 0);
        a.getModuleIds().addAll(List.of(f.id(0, 2), f.id(1, 0), f.id(1, 1), f.id(1, 2)));
        f.addCard(type, 0, 1, 0, 1);

        List<String> order = ModuleNumbering.order(a, f.screen);

        assertEquals(List.of(f.id(0, 0), f.id(0, 2), f.id(1, 0), f.id(1, 1), f.id(1, 2)), order,
                "чужой модуль пропущен, номера 1…5 идут подряд");
        assertEquals(2, ModuleNumbering.numberOf(a, f.screen, f.id(0, 2)));
    }

    @Test
    void hiddenCellsDoNotGetNumbers() {
        ModularFixture f = new ModularFixture(1, 4);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 3);
        f.cab(0, 1).setHidden(true);

        assertEquals(List.of(f.id(0, 0), f.id(0, 2), f.id(0, 3)), ModuleNumbering.order(card, f.screen));
    }

    @Test
    void numberingFollowsTheScreenTemplateAndEachCardUsesItsOwnBoundingRectangle() {
        ModularFixture f = new ModularFixture(2, 4);
        String type = f.cardType("К", 8, null).getId();
        ReceiverCardInstance right = f.addCard(type, 0, 2, 1, 3);

        f.screen.setModuleNumbering(new ModuleNumberingPattern(Corner.TOP_RIGHT, Axis.ROWS, false));
        assertEquals(List.of(f.id(0, 3), f.id(0, 2), f.id(1, 3), f.id(1, 2)), ModuleNumbering.order(right, f.screen));

        f.screen.setModuleNumbering(new ModuleNumberingPattern(Corner.BOTTOM_LEFT, Axis.COLUMNS, true));
        assertEquals(List.of(f.id(1, 2), f.id(0, 2), f.id(0, 3), f.id(1, 3)), ModuleNumbering.order(right, f.screen),
                "карта нумеруется внутри СВОЕГО прямоугольника, а не от угла всего экрана");
    }

    @Test
    void manualOrderOverridesTheTemplateAndLeavesTheRestWithoutNumbers() {
        ModularFixture f = new ModularFixture(1, 4);
        ReceiverCardInstance card = f.addCard(f.cardType("К", 8, null).getId(), 0, 0, 0, 3);
        // чужой id и повтор отбрасываются, остальные модули остаются «?»
        card.setManualPortOrder(new ArrayList<>(List.of(f.id(0, 3), f.id(0, 1), "чужой", f.id(0, 3))));

        assertEquals(List.of(f.id(0, 3), f.id(0, 1)), ModuleNumbering.order(card, f.screen));
        assertEquals(1, ModuleNumbering.numberOf(card, f.screen, f.id(0, 3)));
        assertEquals(0, ModuleNumbering.numberOf(card, f.screen, f.id(0, 0)));
        assertEquals(List.of(f.id(0, 0), f.id(0, 2)), ModuleNumbering.unnumbered(card, f.screen));

        card.setManualPortOrder(null);
        assertEquals(4, ModuleNumbering.order(card, f.screen).size(), "сброс возвращает шаблон");
        assertTrue(ModuleNumbering.unnumbered(card, f.screen).isEmpty());
    }

    @Test
    void numberLabelFollowsThePreferenceFormat() {
        assertEquals("1", ModuleNumbering.label("{n}", "К3", 1));
        assertEquals("1", ModuleNumbering.label(null, "К3", 1), "без шаблона — просто число");
        assertEquals("1", ModuleNumbering.label("  ", "К3", 1));
        assertEquals("J12", ModuleNumbering.label("J{n}", "К3", 12));
        assertEquals("P2", ModuleNumbering.label("P{n}", "К3", 2));
        assertEquals("К3.1", ModuleNumbering.label("{card}.{n}", "К3", 1));
        assertEquals("порт 4", ModuleNumbering.label("порт {n}", "К1", 4));
        assertEquals("?", ModuleNumbering.label("J{n}", "К3", 0), "модуль без номера всегда «?»");
    }
}
