package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T1.3, §2.3 — контрольные случаи
 *  авто-угадывания транзита, взятые из реального проекта «Бармицва». */
class ThruResolverTest {

    @Test
    void prokhodnayaCee32aIsThruButCee16aIsNot() {
        CardPort in32 = new CardPort("CEE 32A", PortDirection.IN, 1);
        CardPort out32 = new CardPort("CEE 32A", PortDirection.OUT, 1);
        CardPort out16 = new CardPort("CEE 16A", PortDirection.OUT, 6);
        List<CardPort> powerConnectors = List.of(in32, out32, out16);

        assertTrue(ThruResolver.isThru(out32, powerConnectors, null), "1 вход + 1 выход того же типа — транзит");
        assertFalse(ThruResolver.isThru(out16, powerConnectors, null), "нет входа 16A на этом щите — отходящие");
    }

    @Test
    void mctrl4kGenlockLoopIsThru() {
        CardPort hdmiIn = new CardPort("HDMI 2.0", PortDirection.IN, 1);
        CardPort genlockIn = new CardPort("Genlock (SDI)", PortDirection.IN, 1);
        CardPort genlockOut = new CardPort("Genlock (SDI)", PortDirection.OUT, 1);
        List<CardPort> basicSet = List.of(hdmiIn, genlockIn, genlockOut);

        assertTrue(ThruResolver.isThru(genlockOut, basicSet, null));
    }

    @Test
    void disguiseD3XlrIsNotThruBecauseCountIsTwoNotOne() {
        CardPort xlrIn = new CardPort("XLR", PortDirection.IN, 2);
        CardPort xlrOut = new CardPort("XLR", PortDirection.OUT, 2);
        List<CardPort> basicSet = List.of(xlrIn, xlrOut);

        assertFalse(ThruResolver.isThru(xlrOut, basicSet, null), "count==2 на обеих группах — не по одному");
    }

    @Test
    void q8HdmiInAndOutOnDifferentCardsIsNotThru() {
        // Симуляция: вызывающий код (NodePortLayout) передаёт siblings ТОЛЬКО с карты
        // выхода — HDMI IN физически на другой карте и в этот список не попадает.
        CardPort hdmiOut = new CardPort("HDMI 2.0", PortDirection.OUT, 4);
        CardPort sdiOut = new CardPort("SDI", PortDirection.OUT, 4);
        List<CardPort> outputCardOnly = List.of(hdmiOut, sdiOut);

        assertFalse(ThruResolver.isThru(hdmiOut, outputCardOnly, null));
    }

    @Test
    void directionOtherThanOutIsNeverThru() {
        CardPort in = new CardPort("CEE 32A", PortDirection.IN, 1);
        CardPort ioPort = new CardPort("SDI", PortDirection.IN_OUT, 1);
        assertFalse(ThruResolver.isThru(in, List.of(in), null));
        assertFalse(ThruResolver.isThru(ioPort, List.of(ioPort), null));
    }

    @Test
    void explicitOverridesOutrankAutoGuessInBothDirections() {
        CardPort in32 = new CardPort("CEE 32A", PortDirection.IN, 1);
        CardPort out32 = new CardPort("CEE 32A", PortDirection.OUT, 1);
        List<CardPort> siblings = List.of(in32, out32);

        out32.setThru(Boolean.FALSE); // библиотека явно говорит "не транзит", хотя структура похожа
        assertFalse(ThruResolver.isThru(out32, siblings, null));

        CardPort out16 = new CardPort("CEE 16A", PortDirection.OUT, 6);
        PortPlacement placement = new PortPlacement(out16.getId());
        placement.setThruOverride(Boolean.TRUE); // пользователь в проекте пометил как транзит вручную
        assertTrue(ThruResolver.isThru(out16, List.of(out16), placement));
    }
}
