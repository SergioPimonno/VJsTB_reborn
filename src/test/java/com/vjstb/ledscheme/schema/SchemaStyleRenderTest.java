package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.SchemaCanvasPanel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T3.1 — {@link com.vjstb.ledscheme.ui.SchemaStyle}
 *  пресеты не падают на рендере, «Печатный» даёт белый фон (угловой пиксель белый). */
class SchemaStyleRenderTest {

    private SettingsManager settings(Path dir, SchemaStylePreset preset) {
        SettingsManager s = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        s.setSchemaStylePreset(preset);
        return s;
    }

    @Test
    void bothPresetsRenderWithoutCrashing(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        for (SchemaStylePreset preset : SchemaStylePreset.values()) {
            SettingsManager s = settings(dir, preset);
            for (SchemaMode mode : SchemaMode.values()) {
                SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, mode, s);
                BufferedImage img = canvas.renderImage(800, 800, true, 1.0);
                assertNotNull(img);
            }
        }
    }

    @Test
    void printPresetCornerPixelIsWhite(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager s = settings(dir, SchemaStylePreset.PRINT);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, s);
        BufferedImage img = canvas.renderImage(800, 800, true, 1.0);

        // Верхний левый угол заведомо не накрыт ни одним узлом (см. фикстуру — все
        // координаты положительные и не примыкают к (0,0) вплотную).
        assertEquals(new Color(255, 255, 255), new Color(img.getRGB(0, 0)));
    }

    @Test
    void screenPresetCornerPixelMatchesDarkBackground(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager s = settings(dir, SchemaStylePreset.SCREEN);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, s);
        BufferedImage img = canvas.renderImage(800, 800, true, 1.0);

        Color corner = new Color(img.getRGB(0, 0));
        assertEquals(new Color(0x0d1117), corner, "Экранный пресет не должен менять фон по сравнению с прежним видом");
    }
}
