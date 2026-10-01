package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.CanvasFit;
import com.vjstb.ledscheme.service.MaskGeometry;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.AfterEffectsJsxWriter;
import com.vjstb.ledscheme.ui.ResolumePresetExporter;
import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30 к маскам: геометрия экрана-«сетки» ({@link MaskGeometry}),
 *  предупреждение «канвас обрезает маски» ({@link CanvasFit}), размеры слайса Resolume и
 *  прекомпа After Effects с множителем, совместимость старого JSON (новые поля с дефолтами,
 *  сохраняющими прежнее поведение) и откат правок масок. */
class MaskGeometryAndExportTest {

    private static final class Fixture {
        final AppModel model;
        final Scene scene;
        final CabinetType type;

        Fixture(Path dir) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
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
    }

    // ---- MaskGeometry ----

    @Test
    void geometryOfPlainAndMeshScreen(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 2, 3, 0, 0);

        MaskGeometry plain = MaskGeometry.of(scr, f.type, f.model.getWorkspace());
        assertEquals(384, plain.width());
        assertEquals(256, plain.height());
        assertEquals(128, plain.cellH());
        assertEquals("384×256 px", plain.sizeLabel());

        f.model.setMaskMesh(scr, true);
        MaskGeometry mesh = MaskGeometry.of(scr, f.type, f.model.getWorkspace());
        assertEquals(384, mesh.width());
        assertEquals(512, mesh.height());
        assertEquals(256, mesh.cellH(), "ячейки кабинетов тоже ×N");
        assertEquals(128, mesh.cellW());
        assertEquals(256, mesh.resolutionH(), "реальное разрешение экрана множитель не меняет");
        assertEquals("384×512 px (сетка ×2)", mesh.sizeLabel());
    }

    // ---- CanvasFit ----

    @Test
    void canvasFitDetectsOverflowAndAccountsForMeshMultiplier(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 2, 2, 0, 0); // маска 256×256
        ContentCanvas canvas = f.model.addCanvas("C", 256, 256);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);

        assertTrue(CanvasFit.overflows(canvas, f.scene, f.model).isEmpty(), "ровно по размеру -- вмещается");
        assertNull(CanvasFit.report(canvas, f.scene, f.model, false));

        f.model.movePlacement(pl, 10, 0);
        List<CanvasFit.Overflow> over = CanvasFit.overflows(canvas, f.scene, f.model);
        assertEquals(1, over.size());
        assertEquals(10, over.get(0).overflowX());
        assertEquals(0, over.get(0).overflowY());

        f.model.movePlacement(pl, 0, 0);
        f.model.setMaskMesh(scr, true); // маска 256×512 в канвасе высотой 256
        over = CanvasFit.overflows(canvas, f.scene, f.model);
        assertEquals(1, over.size());
        assertEquals(256, over.get(0).overflowY(), "множитель сетки учтён");
        String text = CanvasFit.describe(over);
        assertTrue(text.contains("«A»") && text.contains("по высоте на 256 px"), text);
        String report = CanvasFit.report(canvas, f.scene, f.model, true);
        assertNotNull(report);
        assertTrue(report.startsWith("Сцена — канвас «C» (256×256 px):"), report);
    }

    @Test
    void canvasFitForProspectiveSizeUsedBeforeApplyingResize(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 2, 2, 0, 0);
        ContentCanvas canvas = f.model.addCanvas("C", 512, 512);
        f.model.addScreenToCanvas(canvas, scr.getId(), 100, 100);

        assertTrue(CanvasFit.overflows(canvas, f.scene, f.model).isEmpty());
        assertFalse(CanvasFit.overflows(canvas, 300, 512, f.scene, f.model).isEmpty(), "300 < 100+256");
        assertTrue(CanvasFit.overflows(canvas, 356, 356, f.scene, f.model).isEmpty(), "356 == 100+256");
    }

    // ---- Resolume / After Effects ----

    @Test
    void resolumeSliceAndAfterEffectsPrecompUseMeshMaskSize(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 2, 2, 0, 0); // 256×256, с сеткой 256×512
        f.model.setMaskMesh(scr, true);
        ContentCanvas canvas = f.model.addCanvas("C", 1024, 1024);
        f.model.addScreenToCanvas(canvas, scr.getId(), 100, 50);

        String xml = ResolumePresetExporter.buildXml(canvas, f.scene, f.model);
        // Слайс: x 100..356, y 50..562 (высота 512 = 256×2).
        assertTrue(xml.contains("<v x=\"356\" y=\"562\"/>"), xml);
        assertFalse(xml.contains("y=\"306\""), "без множителя нижний край был бы 306");

        String jsx = AfterEffectsJsxWriter.buildJsx(canvas, f.scene, f.model, "Сцена");
        assertTrue(jsx.contains("addComp(\"A_256x512\", 256, 512, 1.0, DUR, FPS)"), jsx);
        assertTrue(jsx.contains(AfterEffectsJsxWriter.maskFilename("Сцена", scr, 256, 512)));
    }

    // ---- старый JSON ----

    private static ObjectMapper lenientMapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void oldPlacementJsonKeepsNameplateAndResolutionOnAndDropsRasterFromNewFiles() throws Exception {
        CanvasPlacement old = lenientMapper().readValue(
                "{\"id\":\"p1\",\"screenId\":\"s1\",\"x\":5,\"y\":6,\"showRaster\":false,\"showGrid\":true}",
                CanvasPlacement.class);
        assertTrue(old.isShowNameLabel(), "старые проекты рисовали плашку всегда");
        assertTrue(old.isShowResolution());
        assertEquals(5, old.getX());

        JsonNode written = lenientMapper().valueToTree(old);
        assertFalse(written.has("showRaster"), "растр в новые файлы не пишется");
        assertTrue(written.has("showNameLabel"));
        assertTrue(written.has("showResolution"));
    }

    @Test
    void oldScreenJsonGetsDefaultMeshAndColorFields() throws Exception {
        Screen scr = lenientMapper().readValue("{\"id\":\"s1\",\"name\":\"A\",\"background\":\"RED_GRAY\"}", Screen.class);
        assertFalse(scr.isMaskMesh());
        assertEquals(2, scr.getMaskHeightMultiplier());
        assertEquals(1, scr.effectiveMaskHeightMultiplier(), "без сетки множитель 1");
        assertNull(scr.getMaskColorA());
        assertEquals(MaskColorPreset.RED_GRAY.color(0), scr.maskColor(0));

        Screen roundTrip = lenientMapper().readValue(lenientMapper().writeValueAsString(scr), Screen.class);
        assertEquals(MaskColorPreset.RED_GRAY, roundTrip.getBackground());
    }

    @Test
    void screenCopyCarriesMaskFields() {
        Screen scr = new Screen();
        scr.setMaskMesh(true);
        scr.setMaskHeightMultiplier(3);
        scr.setBackground(MaskColorPreset.CUSTOM);
        scr.setMaskColorA(0x010203);
        scr.setMaskColorB(0x040506);
        Screen c = scr.copy();
        assertTrue(c.isMaskMesh());
        assertEquals(3, c.effectiveMaskHeightMultiplier());
        assertEquals(new Color(0x010203), c.maskColor(0));
        assertEquals(new Color(0x040506), c.maskColor(1));
    }

    // ---- модель: одна запись отмены, undo ----

    @Test
    void updatePlacementMaskIsOneUndoStepAndRevertsAllFields(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 1, 1, 0, 0);
        ContentCanvas canvas = f.model.addCanvas("C", 256, 256);
        CanvasPlacement pl = f.model.addScreenToCanvas(canvas, scr.getId(), 0, 0);
        int before = f.model.undoLabels().size();

        f.model.updatePlacementMask(pl, p -> {
            p.setShowNameLabel(false);
            p.setShowResolution(false);
            p.setName("Свой");
        });
        assertEquals(before + 1, f.model.undoLabels().size(), "одна запись отмены на всю правку");
        CanvasPlacement changed = f.model.canvasesForCurrentScene().get(0).getPlacements().get(0);
        assertFalse(changed.isShowNameLabel());

        f.model.undo();
        CanvasPlacement restored = f.model.canvasesForCurrentScene().get(0).getPlacements().get(0);
        assertTrue(restored.isShowNameLabel());
        assertTrue(restored.isShowResolution());
        assertNull(restored.getName());
    }

    @Test
    void setMaskCustomColorsSwitchesToCustomAndUndoRestores(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen scr = f.model.addScreen("A", f.type.getId(), 1, 1, 0, 0);
        Screen other = f.model.addScreen("B", f.type.getId(), 1, 1, 0, 0);
        f.model.selectScreen(other); // правим НЕ текущий экран -- как таблица гридов

        f.model.setMaskCustomColors(scr, new Color(0x102030), new Color(0x405060));
        assertEquals(MaskColorPreset.CUSTOM, scr.getBackground());
        assertEquals(new Color(0x102030), scr.maskColor(0));
        assertEquals(new Color(0x405060), scr.maskColor(1));

        f.model.undo();
        assertEquals(MaskColorPreset.NORMAL, scr.getBackground());
        assertNull(scr.getMaskColorA());
    }

    @Test
    void meshSettingsAreUndoableAndPerScreen(@TempDir Path dir) {
        Fixture f = new Fixture(dir);
        Screen a = f.model.addScreen("A", f.type.getId(), 1, 1, 0, 0);
        Screen b = f.model.addScreen("B", f.type.getId(), 1, 1, 0, 0);

        f.model.setMaskMesh(a, true);
        f.model.setMaskHeightMultiplier(a, 4);
        assertEquals(4, a.effectiveMaskHeightMultiplier());
        assertEquals(1, b.effectiveMaskHeightMultiplier(), "свойство конкретного экрана");

        f.model.setMaskHeightMultiplier(a, 0);
        assertEquals(1, a.getMaskHeightMultiplier(), "минимум 1");

        f.model.undo(); // множитель 0 -> 4
        assertEquals(4, a.getMaskHeightMultiplier());
        f.model.undo(); // 4 -> 2 (после включения сетки)
        assertEquals(2, a.getMaskHeightMultiplier());
        assertTrue(a.isMaskMesh());
        f.model.undo(); // сетка выключена
        assertFalse(a.isMaskMesh());
    }
}
