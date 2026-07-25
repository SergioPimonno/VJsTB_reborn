package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.ResolumePresetExporter;
import java.io.File;
import org.junit.jupiter.api.Test;

/** Проверка по образцу реального рабочего файла Resolume, присланного пользователем
 *  (канвас 3584×1280 с тремя размещёнными экранами Left_Portal/Center/Right_Portal). */
class ResolumePresetExporterTest {

    private static AppModel newModel() {
        File f = new File(System.getProperty("java.io.tmpdir"), "vjstb_resolume_test_" + System.nanoTime() + ".json");
        f.deleteOnExit();
        return new AppModel(new WorkspaceStore(f));
    }

    @Test
    void buildsScreenSetupXmlMatchingCanvasLayout() {
        AppModel model = newModel();
        CabinetType base = new CabinetType();
        base.setName("Base");
        base.setResolutionWidth(128);
        base.setResolutionHeight(128);
        model.addCabinetType(base);

        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);

        // addScreen(name, cabinetTypeId, rows, cols, posX, posY) — обратите внимание
        // на порядок rows/cols: Center 16 колонок × 8 строк = 2048×1024, Left/Right
        // Portal 6 колонок × 10 строк = 768×1280, как в образце пользователя.
        Screen center = model.addScreen("Center", base.getId(), 8, 16, 0, 0);
        Screen left = model.addScreen("Left_Portal", base.getId(), 10, 6, 0, 0);
        Screen right = model.addScreen("Right_Portal", base.getId(), 10, 6, 0, 0);

        ContentCanvas canvas = model.addCanvas("Test", 3584, 1280);
        model.addScreenToCanvas(canvas, left.getId(), 0, 0);
        model.addScreenToCanvas(canvas, center.getId(), 768, 0);
        model.addScreenToCanvas(canvas, right.getId(), 2816, 0);

        String xml = ResolumePresetExporter.buildXml(canvas, scene, model);

        assertTrue(xml.contains("<XmlState name=\"Test\">"));
        assertTrue(xml.contains("width=\"3584\" height=\"1280\""), "виртуальный экран должен быть размером с канвас");
        assertTrue(xml.contains("value=\"Left_Portal\""));
        assertTrue(xml.contains("value=\"Center\""));
        assertTrue(xml.contains("value=\"Right_Portal\""));

        // Center: InputRect x=768..2816 (2048 px), y=0..1024 (1024 px) — ровно как в образце.
        assertTrue(xml.contains("<v x=\"768\" y=\"0\"/>\n<v x=\"2816\" y=\"0\"/>\n<v x=\"2816\" y=\"1024\"/>\n<v x=\"768\" y=\"1024\"/>"));
        // Left_Portal: x=0..768, y=0..1280.
        assertTrue(xml.contains("<v x=\"0\" y=\"0\"/>\n<v x=\"768\" y=\"0\"/>\n<v x=\"768\" y=\"1280\"/>\n<v x=\"0\" y=\"1280\"/>"));
        // Right_Portal: x=2816..3584, y=0..1280.
        assertTrue(xml.contains("<v x=\"2816\" y=\"0\"/>\n<v x=\"3584\" y=\"0\"/>\n<v x=\"3584\" y=\"1280\"/>\n<v x=\"2816\" y=\"1280\"/>"));

        assertTrue(xml.contains("PM_LINEAR"));
        assertTrue(xml.contains("controlWidth=\"4\" controlHeight=\"4\""));
    }
}
