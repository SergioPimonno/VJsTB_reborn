package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.PowerCalc;
import com.vjstb.ledscheme.service.SchemaLoadCalc;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Значок загрузки силового блока (запрос 2026-10-17): кольцо пока перегрузки нет,
 * предупреждающий треугольник при перегрузке — раньше «⚠» рисовался глифом цвета заливки
 * блока и не был виден. Плюс разбивка для окна «Статистика блока…».
 */
class NodeLoadBadgeTest {

    private static final int WARN_FILL_RGB = 0xffd400;

    @BeforeEach
    void resetCalcDefaults() {
        PowerCalc.Defaults.reset();
    }

    /** Блок питания на CEE 16A с экраном {@code cols}×{@code rows} по 150 Вт на кабинет. */
    private record Fixture(AppModel model, SettingsManager settings, Scene scene, SchemaNode source) {
    }

    private static Fixture fixture(Path dir, int cols, int rows) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        CabinetType type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("Экран А", type.getId(), cols, rows, 0, 0);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 300, 20,
                screen.getId());
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 20, 20, null);
        model.addPowerConnectorToNode(source, "CEE 16A", PortDirection.IN, 1, 1, null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), "CEE 16A → щит");
        return new Fixture(model, settings, scene, source);
    }

    private static int rgbAt(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xffffff;
    }

    private static boolean hasColorNear(BufferedImage img, int cx, int cy, int rgb, int radius) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (rgbAt(img, x, y) == rgb) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void ringColorFollowsLoadThresholds() {
        assertEquals(new Color(0x3f, 0xb9, 0x50), LoadBadge.ringColor(0.0));
        assertEquals(new Color(0x3f, 0xb9, 0x50), LoadBadge.ringColor(0.69));
        assertEquals(new Color(0xe3, 0xb3, 0x41), LoadBadge.ringColor(0.70));
        assertEquals(new Color(0xe3, 0xb3, 0x41), LoadBadge.ringColor(0.89));
        assertEquals(new Color(0xff, 0x5a, 0x36), LoadBadge.ringColor(0.90));
        assertEquals(new Color(0xff, 0x5a, 0x36), LoadBadge.ringColor(1.0));
    }

    @Test
    void ringFillsClockwiseFromTopProportionallyToLoad() {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 40, 40);
        LoadBadge.paintRing(g2, 20, 20, 0.5); // половина — правая сторона, зелёная
        g2.dispose();
        int green = 0x3fb950;
        // дуга проходит на радиусе 7.5 от центра: справа заполнена, слева — только бледная дорожка
        assertTrue(hasColorNear(img, 27, 20, green, 1), "правая половина кольца должна быть закрашена");
        assertFalse(hasColorNear(img, 13, 20, green, 1), "левая половина кольца при 50% закрашена быть не должна");
    }

    @Test
    void warningTriangleIsDrawnInOwnContrastingColorsRegardlessOfNodeFill() {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0xf0883e)); // как у Palette.WARN — прежний значок сливался с такой заливкой
        g2.fillRect(0, 0, 40, 40);
        LoadBadge.paintWarning(g2, 20, 20);
        g2.dispose();
        assertEquals(WARN_FILL_RGB, rgbAt(img, 24, 25), "тело треугольника — жёлтое, отличается от фона");
        assertNotEquals(rgbAt(img, 2, 2), rgbAt(img, 24, 25));
    }

    @Test
    void overloadedBlockGetsWarningTriangleOnTheCanvasImage(@TempDir Path dir) {
        Fixture f = fixture(dir, 6, 5); // 4500 Вт на ~3260 Вт ёмкости
        assertTrue(SchemaLoadCalc.evaluate(f.source(), f.scene(), f.model()).overloaded());
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(f.model(), SchemaMode.POWER, f.settings());

        BufferedImage img = canvas.renderImage(600, 300, false);
        int cx = LoadBadge.centerX((int) f.source().getX(), (int) f.source().getWidth());
        int cy = LoadBadge.centerY((int) f.source().getY());
        assertEquals(WARN_FILL_RGB, rgbAt(img, cx + 4, cy + 5), "при перегрузке в углу блока рисуется жёлтый треугольник");
    }

    @Test
    void notOverloadedBlockHasRingButNoTriangleInExportWhenOptionOn(@TempDir Path dir) {
        Fixture f = fixture(dir, 3, 5); // 2250 Вт на ~3260 Вт
        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(f.source(), f.scene(), f.model());
        assertFalse(load.overloaded());
        assertEquals(2250.0 / load.capacityWatts(), load.fraction(), 1e-9);
        assertTrue(f.settings().activeProfile().isExportLoadRingsEnabled(), "по умолчанию кольца в экспорте включены");
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(f.model(), SchemaMode.POWER, f.settings());

        // renderImage — путь экспорта
        BufferedImage img = canvas.renderImage(600, 300, false);
        int cx = LoadBadge.centerX((int) f.source().getX(), (int) f.source().getWidth());
        int cy = LoadBadge.centerY((int) f.source().getY());
        assertFalse(hasColorNear(img, cx, cy, WARN_FILL_RGB, 12));
        assertTrue(hasColorNear(img, cx, cy, 0x3fb950, 12), "в экспорте кольцо загрузки должно рисоваться");
    }

    @Test
    void ringIsSkippedInExportWhenOptionOffButStillOnScreen(@TempDir Path dir) {
        Fixture f = fixture(dir, 3, 5);
        f.settings().setExportLoadRingsEnabled(false);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(f.model(), SchemaMode.POWER, f.settings());
        int cx = LoadBadge.centerX((int) f.source().getX(), (int) f.source().getWidth());
        int cy = LoadBadge.centerY((int) f.source().getY());

        BufferedImage exported = canvas.renderImage(600, 300, false);
        assertFalse(hasColorNear(exported, cx, cy, 0x3fb950, 12), "при выключенной настройке кольца в экспорте нет");

        canvas.setSize(600, 300);
        BufferedImage screen = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = screen.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        assertTrue(hasColorNear(screen, cx, cy, 0x3fb950, 12), "на экране кольцо рисуется независимо от настройки");
    }

    @Test
    void overloadTriangleStaysInExportWhenRingOptionOff(@TempDir Path dir) {
        Fixture f = fixture(dir, 6, 5);
        f.settings().setExportLoadRingsEnabled(false);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(f.model(), SchemaMode.POWER, f.settings());

        BufferedImage img = canvas.renderImage(600, 300, false);
        int cx = LoadBadge.centerX((int) f.source().getX(), (int) f.source().getWidth());
        int cy = LoadBadge.centerY((int) f.source().getY());
        assertEquals(WARN_FILL_RGB, rgbAt(img, cx + 4, cy + 5), "треугольник перегрузки не зависит от настройки колец");
    }

    @Test
    void statsBreakdownMatchesEvaluateAndListsInputsAndOutputs(@TempDir Path dir) {
        Fixture f = fixture(dir, 6, 5);
        SchemaLoadCalc.Stats stats = SchemaLoadCalc.stats(f.source(), f.scene(), f.model());
        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(f.source(), f.scene(), f.model());

        assertEquals(load, stats.load());
        assertEquals(1, stats.inputs().size());
        assertEquals("CEE 16A", stats.inputs().get(0).connectorType());
        assertEquals(load.capacityWatts(), stats.inputs().get(0).capacityWatts(), 1e-9);
        assertEquals(1, stats.outputs().size());
        assertEquals("Экран А", stats.outputs().get(0).targetLabel());
        assertEquals(load.loadWatts(), stats.outputs().get(0).watts(), 1e-9);
        assertFalse(stats.deratingManual());
        assertEquals(0, stats.unrecognizedInputs());
    }

    @Test
    void statsReportManualDeratingAndUnrecognizedInputs(@TempDir Path dir) {
        Fixture f = fixture(dir, 3, 5);
        f.model().setSchemaNodeLoadDeratingPercent(f.source(), 80.0);
        f.model().addPowerConnectorToNode(f.source(), "Что-то своё", PortDirection.IN, 1, 1, null);
        SchemaLoadCalc.Stats stats = SchemaLoadCalc.stats(f.source(), f.scene(), f.model());

        assertTrue(stats.deratingManual());
        assertEquals(80.0, stats.deratingPercent(), 1e-9);
        assertEquals(1, stats.unrecognizedInputs());
        assertEquals(1, stats.inputs().size(), "нераспознанный разъём в ёмкость не входит");
    }

    @Test
    void statsHtmlShowsLoadVerdictAndTables(@TempDir Path dir) {
        Fixture f = fixture(dir, 6, 5);
        SchemaLoadCalc.Stats stats = SchemaLoadCalc.stats(f.source(), f.scene(), f.model());
        String html = NodeLoadStatsDialog.buildHtml("Щит <A>", stats, true);

        assertTrue(html.contains("Щит &lt;A&gt;"), "название экранируется");
        assertTrue(html.contains("Перегрузка на"));
        assertTrue(html.contains("CEE 16A"));
        assertTrue(html.contains("Экран А"));
        assertTrue(html.contains("по умолчанию для типа блока"));
    }
}
