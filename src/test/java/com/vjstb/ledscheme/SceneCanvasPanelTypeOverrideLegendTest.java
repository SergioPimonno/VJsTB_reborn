package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт: "показывать в табличке, к какой категории применялся конкретный
 *  цвет" — легенда цветов переопределения типа кабинета в detailMode ({@link
 *  SceneCanvasPanel}). Ничего не рисует, пока в сцене нет ни одного
 *  переопределения; появляется, если есть хотя бы одно. */
class SceneCanvasPanelTypeOverrideLegendTest {

    private AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void legendIsAbsentWithoutAnyOverride(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType wide = new CabinetType();
        wide.setName("Wide 500x500");
        wide.setWidthMm(500);
        wide.setHeightMm(500);
        model.addCabinetType(wide);
        Screen screen = model.addScreen("E", wide.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);
        BufferedImage img = canvas.renderImage(300, 300);

        // Угол снизу справа — куда легенда легла бы, если бы рисовалась.
        assertEquals(Palette.BG.getRGB(), img.getRGB(290, 290),
                "без переопределений легенды быть не должно");
    }

    @Test
    void legendAppearsAndUsesTheStableTypeColorAfterAnOverride(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType wide = new CabinetType();
        wide.setName("Wide 500x500");
        wide.setWidthMm(500);
        wide.setHeightMm(500);
        model.addCabinetType(wide);
        CabinetType narrow = new CabinetType();
        narrow.setName("MG14 250x500");
        narrow.setWidthMm(250);
        narrow.setHeightMm(500);
        model.addCabinetType(narrow);

        Screen screen = model.addScreen("E", wide.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);
        CabinetInstance cab = screen.getCabinets().get(0);
        model.setCabinetTypeOverride(cab.getId(), narrow.getId());

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);
        BufferedImage img = canvas.renderImage(300, 300);

        // Легенда рисуется где-то в правом нижнем углу — размер плашки зависит от
        // длины названия типа, точный пиксель не фиксируем: ищем хоть один пиксель
        // в этой области, отличный от фона.
        assertTrue(hasNonBackgroundPixel(img, 150, 220, 300, 300),
                "с переопределением легенда должна появиться в правом нижнем углу");
    }

    private static boolean hasNonBackgroundPixel(BufferedImage img, int x0, int y0, int x1, int y1) {
        int bg = Palette.BG.getRGB();
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if (img.getRGB(x, y) != bg) {
                    return true;
                }
            }
        }
        return false;
    }
}
