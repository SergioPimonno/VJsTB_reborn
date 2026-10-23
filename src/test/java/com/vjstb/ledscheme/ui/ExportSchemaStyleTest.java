package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.ExportSchemaStyle;
import com.vjstb.ledscheme.settings.SchemaRenderMode;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Способ отрисовки» в «Параметрах экспорта» (запрос 2026-10-24): в режиме {@link
 * ExportSchemaStyle#PRINT} ВСЕ экспортные схемы — общая схема сигнала/питания (оба режима
 * отрисовки, блоком и схемой расключения), схема расключения экрана, обзор сцены, таблица
 * экранов, легенда портов — рисуются в печатном стиле (белый фон) даже при тёмной теме
 * интерфейса; {@link ExportSchemaStyle#FOLLOW} оставляет прежнее поведение.
 */
class ExportSchemaStyleTest {

    private static final int WHITE = Color.WHITE.getRGB();
    private boolean wasDark;

    @BeforeEach
    void darkTheme() {
        wasDark = Palette.isDark();
        Palette.applyTheme(true);
    }

    @AfterEach
    void restoreTheme() {
        Palette.applyTheme(wasDark);
    }

    private static SettingsManager settingsFor(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    private static int corner(BufferedImage img) {
        return img.getRGB(1, 1);
    }

    @Test
    void defaultIsFollowAndSurvivesReload(@TempDir Path dir) {
        SettingsManager s = settingsFor(dir);
        assertEquals(ExportSchemaStyle.FOLLOW, s.activeProfile().getExportSchemaStyle());
        s.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        assertEquals(ExportSchemaStyle.PRINT, settingsFor(dir).activeProfile().getExportSchemaStyle());
        assertEquals(SchemaStylePreset.PRINT, settingsFor(dir).activeProfile().effectiveExportSchemaStylePreset());
    }

    @Test
    void followUsesPersonalizationPresetOtherwiseOwnChoice(@TempDir Path dir) {
        SettingsManager s = settingsFor(dir);
        s.setSchemaStylePreset(SchemaStylePreset.PRINT);
        assertEquals(SchemaStylePreset.PRINT, s.activeProfile().effectiveExportSchemaStylePreset());
        s.setExportSchemaStyle(ExportSchemaStyle.SCREEN);
        assertEquals(SchemaStylePreset.SCREEN, s.activeProfile().effectiveExportSchemaStylePreset());
        s.setSchemaStylePreset(SchemaStylePreset.SCREEN);
        s.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        assertEquals(SchemaStylePreset.PRINT, s.activeProfile().effectiveExportSchemaStylePreset());
    }

    @Test
    void printScopeRestoresPaletteEvenOnFailure(@TempDir Path dir) {
        SettingsManager s = settingsFor(dir);
        s.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        Color bg = Palette.BG;
        Color text = Palette.TEXT;
        assertEquals(WHITE, ExportRendering.run(s.activeProfile(), () -> Palette.BG.getRGB()));
        assertThrows(IllegalStateException.class, () -> ExportRendering.run(s.activeProfile(), () -> {
            throw new IllegalStateException("boom");
        }));
        assertSame(bg, Palette.BG);
        assertSame(text, Palette.TEXT);
    }

    @Test
    void followKeepsDarkExportAndDoesNotTouchThePalette(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager s = settingsFor(dir);
        Scene scene = model.getCurrentScene();
        BufferedImage img = ExportRendering.run(s.activeProfile(),
                () -> SchemeRenderer.renderScreensOverviewImage(scene.getName(), model, scene.getScreens(), 1.0));
        assertEquals(Palette.BG.getRGB(), corner(img));
        assertNotEquals(WHITE, corner(img));
    }

    @Test
    void everyExportedSchemeIsWhiteInPrintStyleUnderDarkTheme(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager s = settingsFor(dir);
        s.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        var profile = s.activeProfile();
        Scene scene = model.getCurrentScene();
        Screen screen = scene.getScreens().get(0);

        // 1. Общая схема сигнала и питания — оба способа отрисовки, блоком и схемой расключения экрана.
        for (SchemaRenderMode renderMode : SchemaRenderMode.values()) {
            s.setSchemaRenderMode(renderMode);
            for (SchemaMode mode : SchemaMode.values()) {
                for (boolean asWiring : new boolean[]{false, true}) {
                    SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, mode, s);
                    BufferedImage img = ExportRendering.run(profile,
                            () -> canvas.renderImage(1600, 900, asWiring, 1.0));
                    assertEquals(SchemaStyle.print().background.getRGB(), corner(img),
                            "общая схема " + mode + "/" + renderMode + "/расключение=" + asWiring);
                    assertEquals(WHITE, corner(img));
                }
            }
        }
        s.setSchemaRenderMode(SchemaRenderMode.MODERN);

        // 2. Схема расключения экрана (питание и сигнал).
        for (boolean power : new boolean[]{true, false}) {
            BufferedImage img = ExportRendering.run(profile, () -> SchemeRenderer.renderImage(screen,
                    model.typeOf(screen), power, 120, model.getWorkspace(), model.powerChainsTouchingScreen(screen),
                    model.signalChainsTouchingScreen(screen), model.controllersInScene(screen), false, 1.0));
            assertEquals(WHITE, corner(img), "схема расключения экрана, питание=" + power);
        }

        // 3. Обзор сцены, таблица экранов, легенда портов.
        for (boolean power : new boolean[]{true, false}) {
            SceneCanvasPanel overview = new SceneCanvasPanel(model, s);
            overview.setDetailMode(true, power, false);
            java.awt.Dimension size = overview.getPreferredSize();
            BufferedImage img = ExportRendering.run(profile, () -> overview.renderImage(size.width, size.height, 1.0));
            assertEquals(WHITE, corner(img), "обзор сцены, питание=" + power);
        }
        assertEquals(WHITE, corner(ExportRendering.run(profile, () -> SchemeRenderer.renderScreensOverviewImage(
                scene.getName(), model, scene.getScreens(), 1.0))), "таблица экранов");
        assertEquals(WHITE, corner(ExportRendering.run(profile, () -> SchemeRenderer.renderPortLegendImage(
                scene.getName(), List.of(), 1.0))), "легенда портов");

        // Тема интерфейса после экспорта прежняя.
        assertEquals(true, Palette.isDark());
    }

    @Test
    void screenExportStyleStaysDarkEvenIfPersonalizationIsPrint(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager s = settingsFor(dir);
        s.setSchemaStylePreset(SchemaStylePreset.PRINT);
        s.setExportSchemaStyle(ExportSchemaStyle.SCREEN);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, s);
        BufferedImage img = canvas.renderImage(800, 600, false);
        assertEquals(SchemaStyle.screen().background.getRGB(), corner(img));
    }

    private static int count(BufferedImage img, int rgb) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void cabinetsStayDarkWithVisibleBordersInThePrintedScreensOverview(@TempDir Path dir) {
        // один тип кабинетов: заливка по типу не включается, видна именно заливка «по умолчанию»
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(new File(dir.toFile(), "w.json")));
        com.vjstb.ledscheme.model.CabinetType ct = new com.vjstb.ledscheme.model.CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        com.vjstb.ledscheme.model.CabinetType type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        model.selectScreen(model.addScreen("E", type.getId(), 4, 6, 0, 0));
        SettingsManager s = settingsFor(dir);
        s.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        Scene scene = model.getCurrentScene();
        BufferedImage img = ExportRendering.run(s.activeProfile(),
                () -> SchemeRenderer.renderScreensOverviewImage(scene.getName(), model, scene.getScreens(), 1.0));
        assertEquals(WHITE, corner(img), "лист белый");
        assertTrue(count(img, 0x2d333b) > 500, "заливка кабинетов тёмная");
        // контур сглажен (линия 1 px на целой координате ложится на два пикселя) — считаем «серые между заливкой и
        // белым листом»
        int outline = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y) & 0xFFFFFF;
                int r = c >> 16;
                int b = c & 0xFF;
                if (r >= 0x55 && r <= 0xB0 && b - r >= 0 && b - r <= 0x20) {
                    outline++;
                }
            }
        }
        assertTrue(outline > 100, "контуры кабинетов различимы на тёмной заливке: " + outline);
    }
}
