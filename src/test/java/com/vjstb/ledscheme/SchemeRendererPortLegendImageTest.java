package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** «Экспорт легенды портов…» (SchemaPanel, PNG по аналогии с «Экспорт схемы…») —
 *  только структурные инварианты размера отрисованной картинки (растёт с числом
 *  строк/длиной текста, не падает на пустом списке), как и у других *SchemaImageWriter
 *  тестов (см. TESTS.md) — не побайтовое сравнение. */
class SchemeRendererPortLegendImageTest {

    @Test
    void emptyRowsStillProducesValidNonEmptyImage() {
        BufferedImage img = SchemeRenderer.renderPortLegendImage("Сцена", List.of(), 1.0);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    void moreRowsProduceTallerImage() {
        List<AppModel.SignalPortLegendRow> oneRow = List.of(
                new AppModel.SignalPortLegendRow("L2", "Контроллер 1 P1-7", "Контроллер 1 P9-15"));
        List<AppModel.SignalPortLegendRow> fourRows = List.of(
                new AppModel.SignalPortLegendRow("L2", "Контроллер 1 P1-7", "Контроллер 1 P9-15"),
                new AppModel.SignalPortLegendRow("L1", "Контроллер 2 P1-10", "Контроллер 3 P1-10"),
                new AppModel.SignalPortLegendRow("R1", "Контроллер 6 P1-10", "Контроллер 7 P1-10"),
                new AppModel.SignalPortLegendRow("R2", "Контроллер 8 P1-7", "—"));

        BufferedImage imgOne = SchemeRenderer.renderPortLegendImage("Сцена", oneRow, 1.0);
        BufferedImage imgFour = SchemeRenderer.renderPortLegendImage("Сцена", fourRows, 1.0);

        assertTrue(imgFour.getHeight() > imgOne.getHeight());
    }

    @Test
    void longerScreenNameWidensImage() {
        List<AppModel.SignalPortLegendRow> shortName = List.of(
                new AppModel.SignalPortLegendRow("R2", "Контроллер 8 P1-7", "—"));
        List<AppModel.SignalPortLegendRow> longName = List.of(
                new AppModel.SignalPortLegendRow("Очень длинное имя экрана для проверки ширины колонки",
                        "Контроллер 8 P1-7", "—"));

        BufferedImage imgShort = SchemeRenderer.renderPortLegendImage("Сцена", shortName, 1.0);
        BufferedImage imgLong = SchemeRenderer.renderPortLegendImage("Сцена", longName, 1.0);

        assertTrue(imgLong.getWidth() > imgShort.getWidth());
    }

    @Test
    void dpiScaleMultipliesPixelDimensions() {
        List<AppModel.SignalPortLegendRow> rows = List.of(
                new AppModel.SignalPortLegendRow("R2", "Контроллер 8 P1-7", "—"));

        BufferedImage img1x = SchemeRenderer.renderPortLegendImage("Сцена", rows, 1.0);
        BufferedImage img2x = SchemeRenderer.renderPortLegendImage("Сцена", rows, 2.0);

        assertTrue(img2x.getWidth() >= img1x.getWidth() * 2 - 2);
        assertTrue(img2x.getHeight() >= img1x.getHeight() * 2 - 2);
    }
}
