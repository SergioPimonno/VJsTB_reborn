package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.MaskLimits;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Запрос пользователя 2026-09-30 (п. 2 плана v2.6, решение D7): «убрать ограничение 16k на
 * сторону маски/канваса». Маски больше не рисуются целиком в {@code BufferedImage} — экспорт
 * пишет их полосами ({@link MaskImage#writePng} → {@link StreamingPngWriter}), рисуя каждую
 * полосу тем же paint-кодом со сдвигом и clip. Главная гарантия — потоковый PNG ПОПИКСЕЛЬНО
 * совпадает с прежним путём {@code renderX()} + {@code ImageIO} (на небольшом канвасе, но с
 * маленькой высотой полосы: экраны и текст плашки пересекают границы полос). Плюс — масштаб
 * превью, понижение превью по бюджету памяти, списки «≥16k / &gt;30k» с учётом множителя
 * экрана-«сетки», XML Resolume и .jsx After Effects для больших размеров.
 */
class MaskStreamingExportTest {

    private static final class Fixture {
        final AppModel model;
        final SettingsManager settings;
        final Scene scene;
        final CabinetType type;

        Fixture(Path dir) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
            settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
            type = new CabinetType();
            type.setName("Base");
            type.setResolutionWidth(128);
            type.setResolutionHeight(128);
            model.addCabinetType(type);
            Project project = model.addProject("Проект");
            model.selectProject(project);
            scene = model.addScene("Сцена");
            model.selectScene(scene);
        }

        /** Канвас 600×420: экран A 2×2 (256×256) в (100,60) со всеми элементами маски и
         *  скрытым кабинетом — его плашка имени (центр по y≈188) пересекает границу полосы 200;
         *  экран B 1×3 «сетка» ×2 (384×256) в (380,150) вылезает за правый край (обрезка). */
        ContentCanvas canvas() {
            Screen a = model.addScreen("Экран A", type.getId(), 2, 2, 0, 0);
            a.getCabinets().get(1).setHidden(true);
            Screen b = model.addScreen("Экран B", type.getId(), 1, 3, 0, 0);
            model.setMaskMesh(b, true);
            ContentCanvas c = model.addCanvas("Канвас", 600, 420);
            model.addScreenToCanvas(c, a.getId(), 100, 60);
            model.addScreenToCanvas(c, b.getId(), 380, 150);
            CanvasPlacement pa = c.getPlacements().get(0);
            model.updatePlacementMask(pa, p -> {
                p.setShowCircle(true);
                p.setShowCross(true);
                p.setShowCorner(true);
            });
            return c;
        }
    }

    private static BufferedImage viaImageIo(BufferedImage img, File f) throws IOException {
        assertTrue(ImageIO.write(img, "png", f));
        return ImageIO.read(f);
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertNotNull(actual);
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        int w = expected.getWidth();
        int h = expected.getHeight();
        int[] e = expected.getRGB(0, 0, w, h, null, 0, w);
        int[] a = actual.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < e.length; i++) {
            if (e[i] != a[i]) {
                throw new AssertionError("пиксель (" + (i % w) + "," + (i / w) + "): ожидалось "
                        + Integer.toHexString(e[i]) + ", получено " + Integer.toHexString(a[i]));
            }
        }
        assertArrayEquals(e, a);
    }

    @Test
    void streamedCanvasMaskEqualsRenderCanvasMaskPixelForPixel(@TempDir Path dir) throws IOException {
        Fixture f = new Fixture(dir);
        ContentCanvas c = f.canvas();

        BufferedImage reference = viaImageIo(PixelGridRenderer.renderCanvasMask(c, f.scene, f.model, f.settings),
                dir.resolve("ref.png").toFile());
        File streamed = dir.resolve("streamed.png").toFile();
        MaskImage.canvas(c, f.scene, f.model, f.settings).writePng(streamed, 50, MaskImage.WriteProgress.NONE);

        assertSamePixels(reference, ImageIO.read(streamed));
    }

    @Test
    void streamedScreenMaskEqualsRenderMask(@TempDir Path dir) throws IOException {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("Экран", f.type.getId(), 2, 3, 0, 0);
        f.model.setMaskMesh(scr, true);
        PixelGridRenderer.GridRenderOptions opts = PixelGridRenderer.GridRenderOptions.defaultForScreen(scr);

        BufferedImage reference = viaImageIo(PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(), opts),
                dir.resolve("ref.png").toFile());
        File streamed = dir.resolve("streamed.png").toFile();
        MaskImage.screen(scr, f.type, f.model.getWorkspace(), opts).writePng(streamed, 37, MaskImage.WriteProgress.NONE);

        assertEquals(512, reference.getHeight(), "маска экрана-«сетки» ×2");
        assertSamePixels(reference, ImageIO.read(streamed));
    }

    @Test
    void streamedGapMaskEqualsRenderCanvasGapMaskWithAlpha(@TempDir Path dir) throws IOException {
        Fixture f = new Fixture(dir);
        ContentCanvas c = f.canvas();

        BufferedImage reference = viaImageIo(PixelGridRenderer.renderCanvasGapMask(c, f.scene, f.model),
                dir.resolve("ref.png").toFile());
        File streamed = dir.resolve("streamed.png").toFile();
        MaskImage.canvasGap(c, f.scene, f.model).writePng(streamed, 45, MaskImage.WriteProgress.NONE);

        BufferedImage read = ImageIO.read(streamed);
        assertTrue(read.getColorModel().hasAlpha());
        assertSamePixels(reference, read);
        assertEquals(0, read.getRGB(110, 70) >>> 24, "над видимым кабинетом — прозрачно");
        assertEquals(0xFF, read.getRGB(5, 5) >>> 24, "вне экранов — непрозрачно-чёрный");
    }

    @Test
    void streamedOverlayEqualsRenderCanvasOverlayWithAlpha(@TempDir Path dir) throws IOException {
        Fixture f = new Fixture(dir);
        ContentCanvas c = f.canvas();

        BufferedImage reference = viaImageIo(PixelGridRenderer.renderCanvasOverlay(c, f.scene, f.model),
                dir.resolve("ref.png").toFile());
        File streamed = dir.resolve("streamed.png").toFile();
        // 7 строк — подписи (высота ~20 px) гарантированно режутся границами полос.
        MaskImage.canvasOverlay(c, f.scene, f.model).writePng(streamed, 7, MaskImage.WriteProgress.NONE);

        assertSamePixels(reference, ImageIO.read(streamed));
    }

    @Test
    void renderAtQuarterIsExactlyAQuarterOfTheSize(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        ContentCanvas c = f.canvas();
        BufferedImage q = MaskImage.canvas(c, f.scene, f.model, f.settings).render(0.25);
        assertEquals(150, q.getWidth());
        assertEquals(105, q.getHeight());
        BufferedImage third = new MaskImage(300, 90, false, g -> { }).render(1.0 / 3);
        assertEquals(100, third.getWidth(), "1/3 от 300 — ровно 100, без лишнего пикселя от погрешности double");
        assertEquals(30, third.getHeight());
    }

    @Test
    void cancelledWriteThrowsAndDeletesPartialFile(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        ContentCanvas c = f.canvas();
        File target = dir.resolve("cancel.png").toFile();
        int[] strips = {0};
        MaskImage.WriteProgress cancelAfterFirst = new MaskImage.WriteProgress() {
            @Override
            public void progress(int rowsDone, int totalRows) {
                strips[0]++;
            }

            @Override
            public boolean cancelled() {
                return strips[0] >= 1;
            }
        };
        assertThrows(CancellationException.class,
                () -> MaskImage.canvas(c, f.scene, f.model, f.settings).writePng(target, 50, cancelAfterFirst));
        assertFalse(target.exists(), "недописанный PNG должен удаляться");
    }

    @Test
    void stripHeightFollowsWidthBudget() {
        assertEquals(1024, MaskImage.defaultStripRows(1920));
        int rows = MaskImage.defaultStripRows(30000);
        assertTrue(rows >= 512 && rows <= 1024, "полоса канваса 30000 px: ~512–1024 строк, получено " + rows);
        assertTrue((long) rows * 30000 <= 16L * 1024 * 1024, "память полосы — в пределах ~64 МБ");
    }

    // ---- набор экспорта: ≥16k / >30k с учётом множителя «сетки» ----

    @Test
    void exportSetListsLargeAndSkipsTooLargeCountingMeshMultiplier(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        // 1 колонка × 70 строк по 128 px = 128×8960 — обычный размер...
        Screen tall = f.model.addScreen("Высокий", f.type.getId(), 70, 1, 0, 0);
        // ...и такой же, но «сетка»: ×2 → 17920 (≥16k), ×4 → 35840 (>30000).
        Screen mesh2 = f.model.addScreen("Сетка2", f.type.getId(), 70, 1, 0, 0);
        f.model.setMaskMesh(mesh2, true);
        Screen mesh4 = f.model.addScreen("Сетка4", f.type.getId(), 70, 1, 0, 0);
        f.model.setMaskMesh(mesh4, true);
        f.model.setMaskHeightMultiplier(mesh4, 4);

        MaskExportSet set = new MaskExportSet();
        for (Screen s : List.of(tall, mesh2, mesh4)) {
            MaskImage img = MaskImage.screen(s, f.type, f.model.getWorkspace(),
                    PixelGridRenderer.GridRenderOptions.defaultForScreen(s));
            set.add(s.getName() + ".png", MaskExportSet.screenLabel(f.scene, s, true), img);
        }

        assertEquals(List.of("Высокий.png", "Сетка2.png"), set.entries().stream().map(MaskExportSet.Entry::filename).toList());
        assertEquals(1, set.large().size());
        assertEquals("Сцена — экран «Сетка2» — 128×17920 px", set.large().get(0).describe());
        assertEquals(1, set.skipped().size());
        assertEquals(35840, set.skipped().get(0).height());

        String text = MaskExportSet.warningText(null, set);
        assertNotNull(text);
        assertTrue(text.contains("128×17920"), text);
        assertTrue(text.contains("НЕ будут экспортированы"), text);
        assertTrue(text.contains("«Сетка4»"), text);
    }

    @Test
    void warningTextIsNullWhenNothingToWarnAbout(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen s = f.model.addScreen("Обычный", f.type.getId(), 2, 2, 0, 0);
        MaskExportSet set = new MaskExportSet();
        set.add("a.png", "a", MaskImage.screen(s, f.type, f.model.getWorkspace(),
                PixelGridRenderer.GridRenderOptions.defaultForScreen(s)));
        assertNull(MaskExportSet.warningText(null, set));
        assertNotNull(MaskExportSet.warningText("канвас «К» (100×100 px):\n    «A» — по ширине на 5 px", set),
                "обрезка канвасом — в том же общем тексте");
    }

    // ---- пресеты Resolume / After Effects ----

    @Test
    void resolumeRangeMaxGrowsWithCanvasAbove16384(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen s = f.model.addScreen("Center", f.type.getId(), 2, 2, 0, 0);
        ContentCanvas big = f.model.addCanvas("Большой", 20000, 1080);
        f.model.addScreenToCanvas(big, s.getId(), 19000, 0);
        String xml = ResolumePresetExporter.buildXml(big, f.scene, f.model);
        assertTrue(xml.contains("width=\"20000\" height=\"1080\""), xml);
        assertTrue(xml.contains("<ValueRange name=\"defaultRange\" min=\"1\" max=\"20000\"/>"), xml);
        assertTrue(xml.contains("<v x=\"19000\" y=\"0\"/>\n<v x=\"19256\" y=\"0\"/>"), "слайс в координатах канваса");

        ContentCanvas small = f.model.addCanvas("Малый", 1920, 1080);
        assertTrue(ResolumePresetExporter.buildXml(small, f.scene, f.model)
                .contains("max=\"16384\""), "для обычных канвасов — как в образце Resolume");
    }

    @Test
    void afterEffectsScriptSkipsScreenMaskAbove30000(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen ok = f.model.addScreen("Обычный", f.type.getId(), 2, 2, 0, 0);
        Screen huge = f.model.addScreen("Огромный", f.type.getId(), 70, 1, 0, 0);
        f.model.setMaskMesh(huge, true);
        f.model.setMaskHeightMultiplier(huge, 4); // 128×35840 — больше предела AE
        ContentCanvas c = f.model.addCanvas("К", 1920, 1080);
        f.model.addScreenToCanvas(c, ok.getId(), 0, 0);
        f.model.addScreenToCanvas(c, huge.getId(), 300, 0);

        String jsx = AfterEffectsJsxWriter.buildJsx(c, f.scene, f.model, "Сцена");
        assertTrue(jsx.contains(AfterEffectsJsxWriter.maskFilename("Сцена", ok, 256, 256)));
        assertFalse(jsx.contains("128, 35840"), "композиции больше 30000 px AE не создаст");
        assertFalse(jsx.contains(AfterEffectsJsxWriter.maskFilename("Сцена", huge, 128, 35840)),
                "скрипт не должен ссылаться на маску, которая не экспортируется");
        assertTrue(jsx.contains("больше " + MaskLimits.MAX_SIDE_PX), "пояснение в комментарии скрипта");
    }
}
