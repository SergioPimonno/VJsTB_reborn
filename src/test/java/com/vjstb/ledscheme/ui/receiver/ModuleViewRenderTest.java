package com.vjstb.ledscheme.ui.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.PowerSupplyEditor;
import com.vjstb.ledscheme.service.ReceiverCardEditor;
import com.vjstb.ledscheme.service.ScreenLogic;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Чистый рендер холстов (без Swing-компонентов и без дисплея): тот же {@code render}, которым экспорт «Карта
 * подключения» рисует печатную схему. Проверяются цвета состояний по пикселям в углу клетки, где нет подписей:
 * серый модуль, оранжевый «без карты», заливка карты, голубое выделение, область блока, красный длинный шлейф.
 */
class ModuleViewRenderTest {

    private static final int CELL = 60;
    private static final int X0 = 20;
    private static final int Y0 = 20;

    private Workspace ws;
    private Screen screen;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;
    private ModuleGridGeometry geo;
    private final ModuleViewStyle st = ModuleViewStyle.print();

    @BeforeEach
    void setUp() {
        ws = new Workspace();
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setReceiverCardSeparate(true);
        ws.getCabinetTypes().add(module);
        cardType = new ReceiverCardType();
        cardType.setName("К");
        cardType.setModuleOutputs(8);
        cardType.setRibbonLengthMm(300.0);
        ws.getReceiverCardTypes().add(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("Б");
        psuType.setOutputs(8);
        psuType.setCableLengthMm(700.0);
        ws.getPowerSupplyTypes().add(psuType);
        screen = new Screen();
        screen.setName("Э");
        screen.setCabinetTypeId(module.getId());
        screen.setRows(4);
        screen.setCols(6);
        ScreenLogic.buildGrid(screen);
        geo = ModuleGridGeometry.of(X0, Y0, CELL, CELL, 4, 6);
    }

    private BufferedImage canvas() {
        BufferedImage img = new BufferedImage(X0 * 2 + CELL * 6, Y0 * 2 + CELL * 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(st.background);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        return img;
    }

    /** Пиксель у левого верхнего угла клетки внутри — там нет ни номера, ни плашки. */
    private static Color cornerOf(BufferedImage img, int row, int col) {
        return new Color(img.getRGB(X0 + col * CELL + 7, Y0 + row * CELL + 7));
    }

    private BufferedImage renderCards(ReceiverCardsCanvas.Options o) {
        BufferedImage img = canvas();
        Graphics2D g = img.createGraphics();
        ReceiverCardsCanvas.render(g, ReceiverCardsCanvas.Data.of(screen, ws, ReceiverCardNumbering.DEFAULT, "{n}"), o,
                geo, st);
        g.dispose();
        return img;
    }

    @Test
    void cardsViewColorsModulesByState() {
        ReceiverCardEditor.assignManual(screen, ws, new GridRect(0, 0, 1, 2), cardType.getId());
        BufferedImage img = renderCards(ReceiverCardsCanvas.Options.NONE);

        assertEquals(st.orphanFill.getRGB(), cornerOf(img, 3, 5).getRGB(), "модуль без карты — оранжевый");
        Color inCard = cornerOf(img, 0, 0);
        assertNotEquals(st.orphanFill.getRGB(), inCard.getRGB());
        assertNotEquals(st.moduleFill.getRGB(), inCard.getRGB(), "карта подкрашивает модули");
        assertTrue(inCard.getGreen() > inCard.getRed() && inCard.getGreen() > inCard.getBlue(),
                "заливка карты зелёная (цвет «Приёмных карт»): " + inCard);
        assertEquals(st.background.getRGB(), new Color(img.getRGB(3, 3)).getRGB(), "вне сетки — фон");
    }

    @Test
    void selectionAndLongRibbonAreVisible() {
        ReceiverCardEditor.assignManual(screen, ws, new GridRect(0, 0, 1, 2), cardType.getId());
        BufferedImage img = renderCards(new ReceiverCardsCanvas.Options(false, null, new GridRect(3, 3, 3, 4), java.util.List.of()));
        assertEquals(st.selectionFill.getRGB(), cornerOf(img, 3, 3).getRGB(), "протягиваемая область — голубая");
        assertEquals(st.selectionFill.getRGB(), cornerOf(img, 3, 4).getRGB());
        assertEquals(st.orphanFill.getRGB(), cornerOf(img, 3, 5).getRGB());

        // шлейф 300 мм: место карты — (0,1) или (1,1), модули (1,0)/(1,2) или (0,0)/(0,2) на 353 мм — красная линия
        var card = screen.getReceiverCards().get(0);
        String host = com.vjstb.ledscheme.service.UnitNames.placeOf(screen, card);
        int hr = screen.cabinetById(host).getRowIndex();
        int hc = screen.cabinetById(host).getColIndex();
        int fr = 1 - hr;
        int fc = 0;
        BufferedImage plain = renderCards(ReceiverCardsCanvas.Options.NONE);
        int mx = (int) ((geo.centerX(hc) + geo.centerX(fc)) / 2);
        int my = (int) ((geo.centerY(hr) + geo.centerY(fr)) / 2);
        boolean red = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                Color c = new Color(plain.getRGB(mx + dx, my + dy));
                red |= c.getRed() > 200 && c.getGreen() < 110 && c.getBlue() < 110;
            }
        }
        assertTrue(red, "длинный шлейф красный, даже без флажка «шлейфы»");
    }

    @Test
    void autoPreviewIsDrawnDashed() {
        ReceiverCardEditor.assignManual(screen, ws, new GridRect(0, 0, 0, 0), cardType.getId());
        BufferedImage without = renderCards(ReceiverCardsCanvas.Options.NONE);
        BufferedImage with = renderCards(new ReceiverCardsCanvas.Options(false, null, null,
                java.util.List.of(new GridRect(2, 2, 3, 3))));
        boolean differs = false;
        for (int x = X0 + 2 * CELL; x < X0 + 4 * CELL && !differs; x++) {
            differs = without.getRGB(x, Y0 + 2 * CELL + 2) != with.getRGB(x, Y0 + 2 * CELL + 2);
        }
        assertTrue(differs, "пунктир предпросмотра виден по верхней границе будущей карты");
    }

    @Test
    void powerViewPaintsBlockAreasAndUnpoweredModules() {
        PowerSupplyEditor.assignManual(screen, ws, new GridRect(0, 0, 1, 1), psuType.getId());
        BufferedImage img = canvas();
        Graphics2D g = img.createGraphics();
        PowerSuppliesCanvas.render(g, PowerSuppliesCanvas.Data.of(screen, ws, ReceiverCardNumbering.DEFAULT),
                PowerSuppliesCanvas.Options.NONE, geo, st);
        g.dispose();

        assertEquals(st.areaFill(0).getRGB(), cornerOf(img, 1, 1).getRGB(), "модуль блока — цвет области БП1");
        assertEquals(st.orphanFill.getRGB(), cornerOf(img, 3, 5).getRGB(), "без блока — оранжевый");
    }

    @Test
    void styleHelpersBlendAndPickReadableText() {
        assertEquals(new Color(128, 128, 128), ModuleViewStyle.blend(Color.BLACK, Color.WHITE, 0.5));
        for (int i = 0; i < 6; i++) {
            Color fill = st.areaFill(i);
            Color text = st.areaText(i);
            double lf = (0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue()) / 255;
            double lt = (0.299 * text.getRed() + 0.587 * text.getGreen() + 0.114 * text.getBlue()) / 255;
            assertTrue(Math.abs(lf - lt) > 0.3, "подпись читается на заливке области " + i);
        }
    }
}
