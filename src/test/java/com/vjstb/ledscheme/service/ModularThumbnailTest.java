package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Запрос пользователя: на общей схеме блок модульного экрана на мелком масштабе рисует только карты (тысячи мелких
 * модулей — сплошная каша), а отдельные модули — когда блок увеличен настолько, что подписи помещаются; режим
 * отключается свойством блока.
 */
class ModularThumbnailTest {

    private static final int CELL = 12;

    private static BufferedImage paint(ModularFixture f, SignalChain chain, boolean unitsOnly, int cell) {
        BufferedImage img = new BufferedImage(cell * 8 + 20, cell * 4 + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        SchemeRenderer.paintWiringDiagram(g, f.screen, f.module, false, cell, cell, 10, 10, f.ws, List.of(),
                List.of(chain), List.of(), false, unitsOnly);
        g.dispose();
        return img;
    }

    @Test
    void unitsOnlyDrawsTheCardAsOneAreaWithoutModuleBordersInside() {
        ModularFixture f = new ModularFixture(4, 8);
        ReceiverCardType t = f.cardType("К", 12, null);
        ReceiverCardInstance card = f.addCard(t.getId(), 0, 0, 3, 3);
        SignalChain chain = new SignalChain(1, false, List.of(card.getId()));

        BufferedImage modules = paint(f, chain, false, CELL);
        BufferedImage cards = paint(f, chain, true, CELL);

        // линия стыка двух модулей ВНУТРИ карты: с модулями — рамка ячейки, с «только карты» — сплошная заливка
        int x = 10 + CELL;
        int y = 10 + CELL / 2;
        assertEquals(Palette.CELL_BORDER.getRGB(), new Color(modules.getRGB(x, y)).getRGB(), "модули — рамка между ними");
        assertNotEquals(Palette.CELL_BORDER.getRGB(), new Color(cards.getRGB(x, y)).getRGB(),
                "только карты — внутри карты рамок модулей нет");
        // карта по-прежнему красится цветом своей цепочки
        Color inside = new Color(cards.getRGB(10 + CELL * 2 - 4, 10 + CELL * 2 - 4));
        assertNotEquals(Color.BLACK.getRGB(), inside.getRGB());
    }

    @Test
    void ordinaryCabinetsOfAMixedScreenAreStillDrawnInUnitsOnlyMode() {
        ModularFixture f = new ModularFixture(4, 8);
        f.makeOrdinary(0, 7);
        ReceiverCardType t = f.cardType("К", 12, null);
        ReceiverCardInstance card = f.addCard(t.getId(), 0, 0, 3, 3);
        SignalChain chain = new SignalChain(1, false, List.of(card.getId(), f.id(0, 7)));

        BufferedImage cards = paint(f, chain, true, CELL);

        // у угла ячейки: в центре цепочка рисует точку-разъём
        Color ordinary = new Color(cards.getRGB(10 + CELL * 7 + 3, 10 + 3));
        assertNotEquals(Color.BLACK.getRGB(), ordinary.getRGB(), "обычный кабинет на месте и закрашен");
    }

    @Test
    void labelThresholdIsTheSameOneTheRichSchemeUsesForText() {
        assertFalse(SchemeRenderer.cellLabelsFit(45, 40));
        assertFalse(SchemeRenderer.cellLabelsFit(50, 33));
        assertTrue(SchemeRenderer.cellLabelsFit(46, 34));
    }

    @Test
    void thumbnailOptionIsOnByDefaultAndSurvivesToggling() {
        SchemaNode n = new SchemaNode();
        assertTrue(n.isUnitsWhenSmall(), "по умолчанию включено — и для блоков из старых проектов");
        n.setUnitsWhenSmall(false);
        assertFalse(n.isUnitsWhenSmall());
    }
}
