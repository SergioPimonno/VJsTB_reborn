package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Поля и границы при экспорте общей схемы (запрос 2026-10-24: «схема режется по крайнему элементу, добавим поля
 * ~5%», затем «размер схемы не учитывает линии с подписями»): {@link SchemaCanvasPanel#renderImageWithMargin}
 * охватывает блоки, ВСЕ точки маршрутов и чипы подписей линий и добавляет 5% с каждой стороны; содержимое то же,
 * что у обычного {@code renderImage}, просто сдвинуто; фон полей — фон схемы.
 */
class SchemaExportMarginTest {

    private static SettingsManager settingsFor(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void marginIsFivePercentPerSideAndContentIsJustShifted(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settingsFor(dir));
        Dimension size = canvas.getPreferredSize();
        double[] b = canvas.exportContentBounds(size.width, size.height);
        int minX = (int) Math.floor(b[0]);
        int minY = (int) Math.floor(b[1]);
        int cw = (int) Math.ceil(b[2]) - minX;
        int ch = (int) Math.ceil(b[3]) - minY;
        int mx = (int) Math.round(cw * 0.05);
        int my = (int) Math.round(ch * 0.05);

        BufferedImage framed = canvas.renderImageWithMargin(size.width, size.height, false, 1.0);
        assertEquals(cw + 2 * mx, framed.getWidth());
        assertEquals(ch + 2 * my, framed.getHeight());

        int bg = SchemaStyle.screen().background.getRGB();
        assertEquals(bg, framed.getRGB(0, 0), "поле закрашено фоном схемы");
        assertEquals(bg, framed.getRGB(framed.getWidth() - 1, framed.getHeight() - 1));

        // содержимое совпадает с обычным рендером, сдвинутым на поле
        BufferedImage plain = canvas.renderImage(size.width, size.height, false, 1.0);
        int diffs = 0;
        for (int y = 0; y < plain.getHeight(); y += 7) {
            for (int x = 0; x < plain.getWidth(); x += 7) {
                int fx = x + mx - minX;
                int fy = y + my - minY;
                if (fx >= 0 && fy >= 0 && fx < framed.getWidth() && fy < framed.getHeight()
                        && plain.getRGB(x, y) != framed.getRGB(fx, fy)) {
                    diffs++;
                }
            }
        }
        assertEquals(0, diffs);
    }

    @Test
    void dpiScaleScalesTheWholeFramedImage(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settingsFor(dir));
        Dimension size = canvas.getPreferredSize();
        BufferedImage one = canvas.renderImageWithMargin(size.width, size.height, false, 1.0);
        BufferedImage two = canvas.renderImageWithMargin(size.width, size.height, false, 2.0);
        assertEquals(one.getWidth() * 2, two.getWidth(), 2);
        assertEquals(one.getHeight() * 2, two.getHeight(), 2);
    }

    @Test
    void blockLeftOfZeroGetsExtraRoomInsteadOfBeingCut(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settingsFor(dir));
        SchemaNode first = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).get(0);
        first.setX(-150);
        Dimension size = canvas.getPreferredSize();
        double[] b = canvas.exportContentBounds(size.width, size.height);
        assertTrue(b[0] <= -150, "левая граница учитывает блок левее нуля");
        BufferedImage framed = canvas.renderImageWithMargin(size.width, size.height, false, 1.0);
        int mx = (int) Math.round(size.width * 0.05);
        assertTrue(framed.getWidth() >= size.width + 2 * mx + 150);
    }

    @Test
    void labelChipFarBeyondTheBlocksIsInsideTheExportedArea(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settingsFor(dir));
        SchemaEdge edge = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).get(0);
        edge.setLabel("Длинная подпись у самого края");
        Dimension size = canvas.getPreferredSize();
        double[] before = canvas.exportContentBounds(size.width, size.height);

        edge.setLabelDx(size.width + 1500);
        edge.setLabelDy(size.height + 900);
        double[] after = canvas.exportContentBounds(size.width, size.height);
        assertTrue(after[2] > before[2] + 1000, "правая граница выросла вслед за чипом подписи");
        assertTrue(after[3] > before[3] + 500, "нижняя граница выросла вслед за чипом подписи");

        BufferedImage framed = canvas.renderImageWithMargin(size.width, size.height, false, 1.0);
        assertTrue(framed.getWidth() > size.width + 1500, "картинка охватывает подпись: " + framed.getWidth());
    }

    @Test
    void routePointsBeyondTheBlocksExtendTheBounds(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settingsFor(dir));
        Dimension size = canvas.getPreferredSize();
        double[] plain = canvas.exportContentBounds(size.width, size.height);
        // блок, переехавший вправо за прежнюю границу, тянет за собой и линии к нему
        SchemaNode far = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).get(0);
        far.setX(plain[2] + 2000);
        Dimension grown = canvas.getPreferredSize();
        double[] b = canvas.exportContentBounds(grown.width, grown.height);
        assertTrue(b[2] >= far.getX() + far.getWidth());
        BufferedImage framed = canvas.renderImageWithMargin(grown.width, grown.height, false, 1.0);
        assertTrue(framed.getWidth() >= b[2] - b[0]);
    }
}
