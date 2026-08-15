package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.AfterEffectsJsxWriter;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тот же образец канваса, что {@code ResolumePresetExporterTest} (3584×1280, три экрана) —
 *  проверяет, что .jsx создаёт композицию нужного размера и по слою на каждый экран, с
 *  позицией/anchor point, соответствующими границам экрана в координатах канваса, и что
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

        assertTrue(jsx.contains("app.project.items.addComp(\"Test\", 3584, 1280,"),
                "композиция должна быть размером с канвас");

        // Left_Portal: 768×1280 в (0,0) -> anchor (384,640), position (384,640).
        String leftFile = AfterEffectsJsxWriter.maskFilename("Сцена", left, 768, 1280);
        assertTrue(jsx.contains("importMask(\"" + leftFile + "\")"));
        assertTrue(jsx.contains("layer.name = \"Left_Portal\";"));
        assertTrue(jsx.contains("[384.0, 640.0]"), "anchor/position левого портала");

        // Center: 2048×1024 в (768,0) -> anchor (1024,512), position (768+1024, 0+512) = (1792,512).
        String centerFile = AfterEffectsJsxWriter.maskFilename("Сцена", center, 2048, 1024);
        assertTrue(jsx.contains("importMask(\"" + centerFile + "\")"));
        assertTrue(jsx.contains("layer.name = \"Center\";"));
        assertTrue(jsx.contains("[1792.0, 512.0]"), "position центрального экрана");

        // Right_Portal: 768×1280 в (2816,0) -> anchor (384,640), position (2816+384, 640) = (3200,640).
        String rightFile = AfterEffectsJsxWriter.maskFilename("Сцена", right, 768, 1280);
        assertTrue(jsx.contains("importMask(\"" + rightFile + "\")"));
        assertTrue(jsx.contains("layer.name = \"Right_Portal\";"));
        assertTrue(jsx.contains("[3200.0, 640.0]"), "position правого портала");
    }

    @Test
    void maskFilenameMatchesExportMasksConvention() {
        Screen scr = new Screen();
        scr.setName("Center");
        String filename = AfterEffectsJsxWriter.maskFilename("Сцена", scr, 2048, 1024);
        assertTrue(filename.equals("Сцена_Center_Маска_2048x1024.png"), filename);
    }
}
