package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Экспорт схемы в JPEG: рендер изображения и запись файла в выбранную папку. */
class SchemeExportTest {

    private static final int BASE = 120;

    private CabinetType sampleType() {
        CabinetType ct = new CabinetType();
        ct.setName("Export 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }

    private Screen buildScreen(Path dir, int rows, int cols) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen scr = model.addScreen("Экран A", type.getId(), rows, cols, 0, 0);
        model.selectScreen(scr);
        List<String> ids = scr.getCabinets().stream().map(c -> c.getId()).limit(3).toList();
        model.addPowerChain(1, ids);
        return scr;
    }

    @Test
    void rendersImageWithExpectedSize(@TempDir Path dir) {
        Screen scr = buildScreen(dir, 3, 2);
        CabinetType type = sampleType();

        BufferedImage img = SchemeRenderer.renderImage(scr, type, true, BASE);

        assertNotNull(img);
        // ширина = колонки*ячейка + отступы(24*2); высота = строки*ячейка + отступы + заголовок
        // (104px — фиксированный, вдвое больше исходных 52, см. SchemeRenderer.renderImage
        // про отказ от прежнего "headerH = 20% полной высоты" — на большой сетке текст
        // разрастался за пределы картинки).
        assertEquals(2 * BASE + 48, img.getWidth());
        assertEquals(3 * BASE + 48 + 104, img.getHeight());
    }

    @Test
    void writesJpegFilesIntoChosenFolder(@TempDir Path dir) throws Exception {
        Screen scr = buildScreen(dir, 3, 2);
        CabinetType type = sampleType();
        File outDir = new File(dir.toFile(), "out");
        assertTrue(outDir.mkdirs());

        // как в UI: для экрана сохраняются обе схемы — питание и сигнал
        for (boolean power : new boolean[]{true, false}) {
            BufferedImage img = SchemeRenderer.renderImage(scr, type, power, BASE);
            File file = new File(outDir, "Экран A_" + (power ? "Питание" : "Сигнал") + ".jpg");
            SchemeRenderer.writeJpeg(img, file, 72);

            assertTrue(file.exists(), "Файл не создан: " + file);
            assertTrue(file.length() > 0, "Файл пустой: " + file);

            BufferedImage readBack = ImageIO.read(file);
            assertNotNull(readBack, "JPEG не читается: " + file);
            assertEquals(img.getWidth(), readBack.getWidth());
            assertEquals(img.getHeight(), readBack.getHeight());
        }

        File[] files = outDir.listFiles((d, n) -> n.endsWith(".jpg"));
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    @Test
    void writeJpeg_embedsRequestedDpiInJfifMetadata(@TempDir Path dir) throws Exception {
        Screen scr = buildScreen(dir, 1, 1);
        CabinetType type = sampleType();
        BufferedImage img = SchemeRenderer.renderImage(scr, type, true, BASE);
        File file = new File(dir.toFile(), "dpi300.jpg");

        SchemeRenderer.writeJpeg(img, file, 300);

        assertEquals(300, readJfifDpi(file));
    }

    @Test
    void renderImage_dpiScale_multipliesPixelDimensions(@TempDir Path dir) throws Exception {
        Screen scr = buildScreen(dir, 2, 2);
        CabinetType type = sampleType();
        BufferedImage base = SchemeRenderer.renderImage(scr, type, true, BASE, null, List.of(), List.of(), false, 1.0);
        // 300/72 — как в OutputStagePanel.generate().
        double scale = 300 / 72.0;
        BufferedImage scaled = SchemeRenderer.renderImage(scr, type, true, BASE, null, List.of(), List.of(), false, scale);

        assertEquals(Math.round(base.getWidth() * scale), scaled.getWidth());
        assertEquals(Math.round(base.getHeight() * scale), scaled.getHeight());
    }

    /** Читает Xdensity из app0JFIF-узла записанного JPEG — если resUnits не 1
     *  (dots per inch), считаем DPI неопределённым (0), т.к. остальные варианты
     *  (0 — просто соотношение сторон, 2 — dots per cm) этот код не пишет. */
    private static int readJfifDpi(File file) throws Exception {
        try (var iis = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReaders(iis);
            var reader = readers.next();
            reader.setInput(iis);
            var metadata = reader.getImageMetadata(0);
            var root = (org.w3c.dom.Element) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            var jfifNodes = root.getElementsByTagName("app0JFIF");
            assertTrue(jfifNodes.getLength() > 0, "app0JFIF узел не найден в записанном файле");
            var jfif = (org.w3c.dom.Element) jfifNodes.item(0);
            assertEquals("1", jfif.getAttribute("resUnits"), "resUnits должен быть 1 (dots per inch)");
            return Integer.parseInt(jfif.getAttribute("Xdensity"));
        }
    }
}
