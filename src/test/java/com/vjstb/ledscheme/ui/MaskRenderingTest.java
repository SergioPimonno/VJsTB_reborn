package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30 к этапу «Генерация масок»: растр (линии через 16 px) убран
 *  совсем; плашка с именем и строка разрешения включаются отдельными галочками; экран-«сетка»
 *  даёт маску выше в N раз (ячейки и маска пустот — тоже); собственная пара цветов попадает в
 *  пиксели маски. Тесты смотрят реальные пиксели {@link PixelGridRenderer}, а не только
 *  модель, — регрессия «галочка есть, а на маске ничего не меняется» поймается здесь. */
class MaskRenderingTest {

    private static final int CAB_PX = 128;

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
            type.setResolutionWidth(CAB_PX);
            type.setResolutionHeight(CAB_PX);
            model.addCabinetType(type);
            Project project = model.addProject("Проект");
            model.selectProject(project);
            scene = model.addScene("Сцена");
            model.selectScene(scene);
        }

        Screen screen(String name, int rows, int cols) {
            return model.addScreen(name, type.getId(), rows, cols, 0, 0);
        }

        PixelGridRenderer.GridRenderOptions opts(Screen scr, ContentCanvas canvas, CanvasPlacement pl) {
            return PixelGridRenderer.GridRenderOptions.of(scr, pl, canvas, settings);
        }
    }

    @Test
    void maskHasNoRasterLinesInsideACabinet(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.screen("A", 2, 2);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 512);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);

        BufferedImage img = PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(),
                f.opts(scr, canvas, pl));

        // x=32 и x=48 кратны 16 -- раньше здесь шла вертикальная линия растра (alpha 40) поверх
        // заливки ячейки; теперь пиксель внутри кабинета без чужих элементов -- чистый цвет пресета.
        int expected = MaskColorPreset.NORMAL.color(0).getRGB() & 0xFFFFFF;
        assertEquals(expected, img.getRGB(32, 100) & 0xFFFFFF, "линий растра быть не должно");
        assertEquals(expected, img.getRGB(48, 100) & 0xFFFFFF, "линий растра быть не должно");
        assertEquals(expected, img.getRGB(40, 96) & 0xFFFFFF, "горизонтальных линий растра быть не должно");
    }

    /** Высота (в px) вертикальной протяжки, где пиксели центральной колонки маски отличаются
     *  от её фона, — прямо высота плашки (0 — плашки нет). */
    private static int plateExtent(BufferedImage img, Color background) {
        int x = img.getWidth() / 2;
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) != (background.getRGB() & 0xFFFFFF)) {
                min = Math.min(min, y);
                max = y;
            }
        }
        return max < 0 ? 0 : max - min + 1;
    }

    @Test
    void nameplateAndResolutionLinesAreIndependentAndPlateShrinksToOneLine(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        // Один большой кабинет -- вся маска однотонная, плашка в центре единственная "помеха".
        CabinetType big = new CabinetType();
        big.setName("Big");
        big.setResolutionWidth(512);
        big.setResolutionHeight(512);
        f.model.addCabinetType(big);
        Screen scr = f.model.addScreen("Экран", big.getId(), 1, 1, 0, 0);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 512);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);
        f.model.updatePlacementMask(pl, p -> {
            p.setShowGrid(false);
            p.setShowIds(false);
        });
        Color bg = MaskColorPreset.NORMAL.color(0);

        BufferedImage both = PixelGridRenderer.renderMask(scr, big, f.model.getWorkspace(), f.opts(scr, canvas, pl));
        f.model.updatePlacementMask(pl, p -> p.setShowResolution(false));
        BufferedImage nameOnly = PixelGridRenderer.renderMask(scr, big, f.model.getWorkspace(), f.opts(scr, canvas, pl));
        f.model.updatePlacementMask(pl, p -> {
            p.setShowResolution(true);
            p.setShowNameLabel(false);
        });
        BufferedImage resOnly = PixelGridRenderer.renderMask(scr, big, f.model.getWorkspace(), f.opts(scr, canvas, pl));
        f.model.updatePlacementMask(pl, p -> p.setShowResolution(false));
        BufferedImage none = PixelGridRenderer.renderMask(scr, big, f.model.getWorkspace(), f.opts(scr, canvas, pl));

        assertEquals(0, plateExtent(none, bg), "обе строки выключены -- плашки нет вовсе");
        int two = plateExtent(both, bg);
        int name = plateExtent(nameOnly, bg);
        int res = plateExtent(resOnly, bg);
        assertTrue(name > 0 && res > 0, "одна строка -- плашка всё равно рисуется");
        assertTrue(name < two, "плашка только с именем ниже двухстрочной: " + name + " vs " + two);
        assertTrue(res < two, "плашка только с разрешением ниже двухстрочной: " + res + " vs " + two);
    }

    @Test
    void meshScreenMaskIsMultiplierTimesTaller(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.screen("Mesh", 2, 2);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 1024);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);
        f.model.updatePlacementMask(pl, p -> {
            p.setShowIds(false);
            p.setShowNameLabel(false);
            p.setShowResolution(false);
        });

        BufferedImage normal = PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(),
                f.opts(scr, canvas, pl));
        assertEquals(256, normal.getWidth());
        assertEquals(256, normal.getHeight());

        f.model.setMaskMesh(scr, true);
        BufferedImage mesh = PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(),
                f.opts(scr, canvas, pl));
        assertEquals(256, mesh.getWidth());
        assertEquals(512, mesh.getHeight(), "множитель по умолчанию 2 -- маска вдвое выше");
        // Ячейка кабинета (строка 1) начинается на y=256 (= 128*2), а не на y=128.
        int row0 = MaskColorPreset.NORMAL.color(0).getRGB() & 0xFFFFFF;
        int row1 = MaskColorPreset.NORMAL.color(1).getRGB() & 0xFFFFFF;
        assertEquals(row0, mesh.getRGB(20, 200) & 0xFFFFFF, "ячейка строки 0 теперь тянется до y=256");
        assertEquals(row1, mesh.getRGB(20, 300) & 0xFFFFFF, "строка 1 начинается с y=256");

        f.model.setMaskHeightMultiplier(scr, 3);
        assertEquals(768, PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(),
                f.opts(scr, canvas, pl)).getHeight());
    }

    @Test
    void gapMaskAndCanvasMaskFollowTheMeshMultiplier(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.screen("Mesh", 2, 2);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 1024);
        f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);

        BufferedImage gapBefore = PixelGridRenderer.renderCanvasGapMask(canvas, f.scene, f.model);
        assertEquals(0xFF000000, gapBefore.getRGB(100, 400), "без сетки экран кончается на y=256 -- ниже «пустота»");

        f.model.setMaskMesh(scr, true);
        BufferedImage gap = PixelGridRenderer.renderCanvasGapMask(canvas, f.scene, f.model);
        assertEquals(0, gap.getRGB(100, 400) >>> 24, "экран-сетка занимает 512 px по высоте -- здесь уже не «пустота»");
        assertEquals(0xFF000000, gap.getRGB(100, 600), "ниже маски экрана -- по-прежнему «пустота»");

        BufferedImage canvasMask = PixelGridRenderer.renderCanvasMask(canvas, f.scene, f.model, f.settings);
        assertTrue((canvasMask.getRGB(10, 400) & 0xFFFFFF) != 0, "маска экрана-сетки вклеена на всю свою высоту");
    }

    @Test
    void customMaskColorPairReachesTheMaskPixels(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.screen("A", 2, 2);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 512);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);
        f.model.updatePlacementMask(pl, p -> {
            p.setShowIds(false);
            p.setShowNameLabel(false);
            p.setShowResolution(false);
        });
        f.model.setMaskCustomColors(scr, new Color(0x112233), new Color(0xAABBCC));

        BufferedImage img = PixelGridRenderer.renderMask(scr, f.type, f.model.getWorkspace(), f.opts(scr, canvas, pl));

        // (0,0) и (1,1) -- чётность 0; (0,1) и (1,0) -- чётность 1.
        assertEquals(0x112233, img.getRGB(40, 40) & 0xFFFFFF);
        assertEquals(0xAABBCC, img.getRGB(200, 40) & 0xFFFFFF);
        assertEquals(0xAABBCC, img.getRGB(40, 200) & 0xFFFFFF);
        assertEquals(0x112233, img.getRGB(200, 200) & 0xFFFFFF);
    }

    @Test
    void customPresetWithoutPairFallsBackToNormalColors() {
        Screen scr = new Screen();
        scr.setBackground(MaskColorPreset.CUSTOM);
        assertEquals(MaskColorPreset.NORMAL.color(0), scr.maskColor(0));
        assertEquals(MaskColorPreset.NORMAL.color(1), scr.maskColor(1));
    }
}
