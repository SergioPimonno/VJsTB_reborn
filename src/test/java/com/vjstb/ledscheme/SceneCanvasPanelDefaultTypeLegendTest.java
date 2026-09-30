package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-09-30, два раунда. Раунд 1 (скриншот легенды с одной строкой
 *  "MG7s Cube XO Studio"): легенда называла только ПЕРЕОПРЕДЕЛЁННЫЙ тип — для экрана,
 *  где переопределена только часть ячеек, имя БАЗОВОГО типа остальных ячеек было
 *  нигде не видно. Раунд 2 (скриншот с целиком закрашенным экраном): первая попытка
 *  чинить это закраской базовых ячеек сплошным цветом оказалась «плохо выглядит» —
 *  теперь легенда по-прежнему называет базовый тип, но на схеме он остаётся БЕЗ
 *  заливки (только пустой контур в самой легенде, не сплошной квадратик). */
class SceneCanvasPanelDefaultTypeLegendTest {

    private AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void legendAndTintCoverTheDefaultTypeWhenSomeCabinetIsOverridden(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);

        CabinetType wide = new CabinetType();
        wide.setName("MG7s XO Studio");
        wide.setWidthMm(500);
        wide.setHeightMm(500);
        model.addCabinetType(wide);

        CabinetType narrow = new CabinetType();
        narrow.setName("MG7s Cube XO Studio");
        narrow.setWidthMm(500);
        narrow.setHeightMm(500);
        model.addCabinetType(narrow);

        // 1 строка x 2 столбца: col 0 остаётся базовым типом (wide), col 1 переопределён.
        Screen screen = model.addScreen("E", wide.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);
        CabinetInstance col1 = screen.cabinetAt(0, 1);
        model.setCabinetTypeOverride(col1.getId(), narrow.getId());

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);

        // Легенда называет ОБА типа: и переопределённый, и базовый.
        List<String> legend = canvas.legendTypeNamesForTest(scene);
        assertTrue(legend.contains("MG7s Cube XO Studio"), "переопределённый тип должен быть в легенде: " + legend);
        assertTrue(legend.contains("MG7s XO Studio"),
                "базовый тип экрана с частичным переопределением тоже должен быть в легенде: " + legend);

        // Но сама БАЗОВАЯ (col 0, не переопределённая) ячейка заливки НЕ получает —
        // раунд 2 баг-репорта: закраска большинства ячеек экрана оказалась навязчивой,
        // от неё отказались. Геометрия — как в SceneCanvasPanelOverrideMarksRenderTest:
        // сетка начинается в (40,40), шаг колонки 500мм*0.25=125px, col 0 — [40;165]×
        // [40;165]; середина ячейки (подальше от рамки/подписи «1,1») должна остаться
        // фоном.
        BufferedImage img = canvas.renderImage(300, 300);
        int insideDefaultCabX = 40 + 125 / 2;
        int insideDefaultCabY = 40 + 125 / 2;
        assertEquals(Palette.BG.getRGB(), img.getRGB(insideDefaultCabX, insideDefaultCabY),
                "базовая (непереопределённая) ячейка не должна закрашиваться цветом типа");
    }

    @Test
    void legendOmitsDefaultTypeWhenEveryVisibleCabinetIsOverridden(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);

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

        // Единственная ячейка экрана — и та переопределена: базовому типу тут неоткуда
        // взяться на схеме, поэтому в легенде его быть не должно (иначе запись без
        // соответствующего цвета на канвасе).
        Screen screen = model.addScreen("E", wide.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);
        model.setCabinetTypeOverride(screen.getCabinets().get(0).getId(), narrow.getId());

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);

        List<String> legend = canvas.legendTypeNamesForTest(scene);
        assertEquals(List.of("MG14 250x500"), legend);
    }
}
