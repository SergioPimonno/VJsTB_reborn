package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.SchemaCanvasPanel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
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

    /** T6.1 (PLAN.md §2.7): на печатном пресете роль "Синхро" должна быть заметна и
     *  без цвета (чёрно-белая распечатка) — линия рисуется пунктиром, не только
     *  фиолетовой. На "Экранном" пресете это правило не действует (там пунктир —
     *  только пользовательский переключатель "Пунктиром" конкретной связи). */
    @Test
    void printPresetDashesSyncEdgesAutomatically(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaNode blackmagic = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Blackmagic");
        SchemaNode mctrl1 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "MCTRL4K #1");
        SchemaNode d3main = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Disguise D3 (Main)");
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        CardPort genlockOut = SchemaFixtures.portOnCard(blackmagic, "Sync Generator", "Genlock (SDI)");
        SchemaEdge syncEdge = findEdge(model, blackmagic.getId(), genlockOut.getId(), mctrl1.getId());
        SchemaEdge videoEdge = findEdge(model, d3main.getId(), null, q8.getId());

        SettingsManager print = settings(dir, SchemaStylePreset.PRINT);
        SchemaCanvasPanel printCanvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, print);
        assertTrue(printCanvas.autoDashedForPrintSyncForTest(syncEdge),
                "синхро на печатном пресете — пунктиром, не только цветом");
        assertFalse(printCanvas.autoDashedForPrintSyncForTest(videoEdge),
                "видео (не синхро) не должно получать пунктир автоматически");

        SettingsManager screen = settings(dir, SchemaStylePreset.SCREEN);
        SchemaCanvasPanel screenCanvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, screen);
        assertFalse(screenCanvas.autoDashedForPrintSyncForTest(syncEdge),
                "автопунктир — только для печатного пресета, не для экранного");
    }

    private static SchemaEdge findEdge(AppModel model, String fromNodeId, String fromPortId, String toNodeId) {
        List<SchemaEdge> edges = model.getCurrentScene().getSchemaEdges();
        for (SchemaEdge e : edges) {
            if (fromNodeId.equals(e.getFromNodeId()) && toNodeId.equals(e.getToNodeId())
                    && (fromPortId == null || fromPortId.equals(e.getFromPortId()))) {
                return e;
            }
        }
        throw new IllegalStateException("Связь не найдена в фикстуре: " + fromNodeId + " -> " + toNodeId);
    }
}
