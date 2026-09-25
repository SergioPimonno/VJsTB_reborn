package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.AfterEffectsJsxWriter;
import com.vjstb.ledscheme.ui.PixelGridRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тот же образец канваса, что {@code ResolumePresetExporterTest} (3584×1280, три экрана) —
 *  проверяет, что .jsx создаёт композицию нужного размера и по прекомпу на каждый экран,
 *  положенному anchor [0,0] в позицию экрана на канвасе, guide-слои пустот/разметки, и что
 *  имя PNG-файла, на который ссылается скрипт, совпадает с {@code
 *  AfterEffectsJsxWriter#maskFilename}. */
class AfterEffectsJsxWriterTest {

    private static AppModel newModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void buildsJsxMatchingCanvasLayout(@TempDir Path dir) {
        AppModel model = newModel(dir);
        CabinetType base = new CabinetType();
        base.setName("Base");
        base.setResolutionWidth(128);
        base.setResolutionHeight(128);
        model.addCabinetType(base);

        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);

        Screen center = model.addScreen("Center", base.getId(), 8, 16, 0, 0);
        Screen left = model.addScreen("Left_Portal", base.getId(), 10, 6, 0, 0);
        Screen right = model.addScreen("Right_Portal", base.getId(), 10, 6, 0, 0);

        ContentCanvas canvas = model.addCanvas("Test", 3584, 1280);
        model.addScreenToCanvas(canvas, left.getId(), 0, 0);
        model.addScreenToCanvas(canvas, center.getId(), 768, 0);
        model.addScreenToCanvas(canvas, right.getId(), 2816, 0);

        String jsx = AfterEffectsJsxWriter.buildJsx(canvas, scene, model, "Сцена");

        assertTrue(jsx.contains("app.project.items.addComp(\"Test_3584x1280\", 3584, 1280,"),
                "имя композиции несёт разрешение канваса, как и имя файла его маски");

        // Каждый экран -- прекомп размером с экран, слой прекомпа с anchor [0,0] в позиции экрана.
        assertScreen(jsx, AfterEffectsJsxWriter.maskFilename("Сцена", left, 768, 1280),
                "Left_Portal_768x1280", 768, 1280, 0, 0);
        assertScreen(jsx, AfterEffectsJsxWriter.maskFilename("Сцена", center, 2048, 1024),
                "Center_2048x1024", 2048, 1024, 768, 0);
        assertScreen(jsx, AfterEffectsJsxWriter.maskFilename("Сцена", right, 768, 1280),
                "Right_Portal_768x1280", 768, 1280, 2816, 0);

        // Маска пустот и разметка -- guide-слои (не попадают в рендер), слои не блокируются.
        assertTrue(jsx.contains("importPng(\"" + AfterEffectsJsxWriter.gapMaskFilename("Сцена", canvas) + "\")"));
        assertTrue(jsx.contains("importPng(\"" + AfterEffectsJsxWriter.overlayFilename("Сцена", canvas) + "\")"));
        assertTrue(jsx.contains("gapLayer.guideLayer = true;"));
        assertTrue(jsx.contains("overlayLayer.guideLayer = true;"));
        assertFalse(jsx.contains(".locked"), "слои не блокируются -- решение пользователя");
    }

    private static void assertScreen(String jsx, String file, String name, int w, int h, int x, int y) {
        assertTrue(jsx.contains("addComp(\"" + name + "\", " + w + ", " + h + ", 1.0, DUR, FPS)"), name);
        assertTrue(jsx.contains("importPng(\"" + file + "\")"), file);
        int at = jsx.indexOf("addComp(\"" + name + "\"");
        String tail = jsx.substring(at);
        int pos = tail.indexOf("setValue([" + x + ", " + y + "])");
        int nextScreen = tail.indexOf("addComp(", 1);
        assertTrue(pos > 0 && (nextScreen < 0 || pos < nextScreen), "позиция " + name);
    }

    @Test
    void canvasHelperPngsHaveCanvasSizeAndCutOutScreens(@TempDir Path dir) {
        AppModel model = newModel(dir);
        CabinetType base = new CabinetType();
        base.setName("Base");
        base.setResolutionWidth(128);
        base.setResolutionHeight(128);
        model.addCabinetType(base);
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        Screen scr = model.addScreen("A", base.getId(), 2, 2, 0, 0);
        ContentCanvas canvas = model.addCanvas("C", 512, 512);
        model.addScreenToCanvas(canvas, scr.getId(), 0, 0);

        BufferedImage gap = PixelGridRenderer.renderCanvasGapMask(canvas, scene, model);
        assertEquals(512, gap.getWidth());
        assertEquals(512, gap.getHeight());
        assertEquals(0, gap.getRGB(100, 100) >>> 24, "над экраном -- прозрачно");
        assertEquals(0xFF000000, gap.getRGB(400, 400), "вне экранов -- непрозрачный чёрный");

        BufferedImage overlay = PixelGridRenderer.renderCanvasOverlay(canvas, scene, model);
        assertEquals(512, overlay.getWidth());
        assertEquals(0, overlay.getRGB(400, 400) >>> 24, "разметка в основном прозрачна");
    }

    @Test
    void maskFilenameMatchesExportMasksConvention() {
        Screen scr = new Screen();
        scr.setName("Center");
        String filename = AfterEffectsJsxWriter.maskFilename("Сцена", scr, 2048, 1024);
        assertTrue(filename.equals("Сцена_Center_Маска_2048x1024.png"), filename);
    }
}
