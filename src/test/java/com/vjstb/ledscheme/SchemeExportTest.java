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
        // ширина = колонки*ячейка + отступы(24*2); высота = строки*ячейка + отступы + заголовок(52)
        assertEquals(2 * BASE + 48, img.getWidth());
        assertEquals(3 * BASE + 48 + 52, img.getHeight());
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
            SchemeRenderer.writeJpeg(img, file);

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
}
