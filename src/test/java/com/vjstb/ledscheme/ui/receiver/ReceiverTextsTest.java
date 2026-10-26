package com.vjstb.ledscheme.ui.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.PowerSupplyType;
import org.junit.jupiter.api.Test;

/** Тексты видов, которые не требуют дисплея: склонения, строка типа блока, фраза палитры нумерации. */
class ReceiverTextsTest {

    @Test
    void russianPluralsFollowTheLastDigits() {
        assertEquals("1 карта", RuPlural.count(1, "карта", "карты", "карт"));
        assertEquals("2 карты", RuPlural.count(2, "карта", "карты", "карт"));
        assertEquals("5 карт", RuPlural.count(5, "карта", "карты", "карт"));
        assertEquals("11 карт", RuPlural.count(11, "карта", "карты", "карт"));
        assertEquals("21 карта", RuPlural.count(21, "карта", "карты", "карт"));
        assertEquals("24 карты", RuPlural.count(24, "карта", "карты", "карт"));
        assertEquals("0 карт", RuPlural.count(0, "карта", "карты", "карт"));
    }

    @Test
    void supplyTypeRowListsOutputsPowerAndCable() {
        PowerSupplyType t = new PowerSupplyType();
        t.setName("Б");
        t.setOutputs(8);
        t.setCardOutput(true);
        t.setRatedPowerW(300.0);
        t.setCableLengthMm(700.0);
        assertEquals("8+К шл. · 300 Вт · 700 мм", PowerAssignPanel.typeParams(t));
        t.setCardOutput(false);
        t.setCableLengthMm(null);
        assertEquals("8 шл. · 300 Вт", PowerAssignPanel.typeParams(t));
        t.setOutputs(null);
        t.setRatedPowerW(null);
        assertEquals("шлейфов: ?", PowerAssignPanel.typeParams(t),
                "число шлейфов не указано — строка прямо говорит об этом, а не молчит (баг-репорт: были видны только ватты)");
        t.setRatedPowerW(300.0);
        assertEquals("шлейфов: ? · 300 Вт", PowerAssignPanel.typeParams(t));
    }

    @Test
    void paletteSentenceNamesCornerDirectionAndSnake() {
        assertEquals("Первый модуль — слева сверху, дальше вправо по строке",
                NumberingPalette.describe(new ModuleNumberingPattern()));
        assertEquals("Первый модуль — справа снизу, дальше вверх по столбцу, змейкой",
                NumberingPalette.describe(new ModuleNumberingPattern(ModuleNumberingPattern.Corner.BOTTOM_RIGHT,
                        ModuleNumberingPattern.Axis.COLUMNS, true)));
        assertEquals("Первый модуль — слева снизу, дальше вправо по строке",
                NumberingPalette.describe(new ModuleNumberingPattern(ModuleNumberingPattern.Corner.BOTTOM_LEFT,
                        ModuleNumberingPattern.Axis.ROWS, false)));
    }

    @Test
    void gridGeometryFitsKeepsAspectAndFindsCells() {
        ModuleGridGeometry g = ModuleGridGeometry.fit(4, 6, 2.0, 600, 400, 10, 1000);
        assertEquals(4, g.rows());
        assertEquals(6, g.cols());
        assertEquals(2.0, g.cellWidth() / g.cellHeight(), 1e-9, "пропорция клетки как у модуля 500×250");
        int[] c = g.cellAt(g.centerX(3), g.centerY(2), false);
        assertEquals(2, c[0]);
        assertEquals(3, c[1]);
        assertEquals(null, g.cellAt(-5, -5, false), "вне сетки — не клетка");
        int[] clamped = g.cellAt(-5, 9999, true);
        assertEquals(3, clamped[0]);
        assertEquals(0, clamped[1], "протяжка за край продолжает выделение до края");
    }
}
