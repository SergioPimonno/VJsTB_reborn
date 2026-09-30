package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-09-30: «Экспорт масок (экраны + канвасы)…» (VisualizationStagePanel,
 *  вкладка «Генерация масок») рисовал ПУСТЫМИ (сплошной чёрный прямоугольник) канвасы
 *  всех сцен проекта, КРОМЕ той, что была выбрана в приложении в момент нажатия —
 *  {@code renderCanvasMask} искал экраны своих размещений в {@code
 *  model.getCurrentScene()}, а не в сцене, которой РЕАЛЬНО принадлежит канвас; экрана с
 *  таким id в чужой текущей сцене не находилось, и вклеивать было нечего. */
class PixelGridRendererCanvasMaskSceneTest {

    private static AppModel newModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private static SettingsManager newSettings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void rendersCanvasOfANonCurrentScene(@TempDir Path dir) {
        AppModel model = newModel(dir);
        SettingsManager settings = newSettings(dir);
        CabinetType base = new CabinetType();
        base.setName("Base");
        base.setResolutionWidth(128);
        base.setResolutionHeight(128);
        model.addCabinetType(base);

        Project project = model.addProject("Проект");
        model.selectProject(project);

        Scene sceneA = model.addScene("Сцена A");
        model.selectScene(sceneA);
        Screen screenA = model.addScreen("Front", base.getId(), 2, 2, 0, 0);

        Scene sceneB = model.addScene("Сцена B");
        model.selectScene(sceneB);
        Screen screenB = model.addScreen("Side", base.getId(), 2, 2, 0, 0);
        ContentCanvas canvasB = model.addCanvas("B", 256, 256);
        model.addScreenToCanvas(canvasB, screenB.getId(), 0, 0);

        // Как в VisualizationStagePanel#exportMasks -- цикл по ВСЕМ сценам проекта, пока
        // в приложении реально выбрана только ОДНА из них (здесь — Сцена A, не Сцена B,
        // которой принадлежит canvasB).
        model.selectScene(sceneA);
        assertNotEquals(sceneB, model.getCurrentScene());

        BufferedImage img = PixelGridRenderer.renderCanvasMask(canvasB, sceneB, model, settings);

        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());
        assertNotEquals(0xFF000000, img.getRGB(30, 30) & 0xFFFFFFFF,
                "маска экрана B должна быть вклеена в канвас, а не оставлять сплошной чёрный фон");
    }
}
