package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.MaskLimits;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30 (п. 2 плана v2.6, решение D7): «убрать ограничение 16k на
 *  сторону маски/канваса». Раньше 16384 было зашито в спиннеры UI; теперь пределы — одна точка
 *  {@link MaskLimits}: сторона ≥ 16384 — только предупреждение, &gt; 30000 (предел композиции
 *  After Effects) — отказ, и отказывает сама модель ({@code addCanvas/updateCanvas}), чтобы ни
 *  один путь в обход спиннеров не создал канвас, который нельзя экспортировать. */
class MaskLimitsTest {

    private static AppModel newModelWithScene(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType type = new CabinetType();
        type.setName("Base");
        type.setResolutionWidth(128);
        type.setResolutionHeight(128);
        model.addCabinetType(type);
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        return model;
    }

    @Test
    void warnThresholdIsInclusiveAt16384() {
        assertFalse(MaskLimits.isLarge(16383, 16383));
        assertTrue(MaskLimits.isLarge(16384, 100));
        assertTrue(MaskLimits.isLarge(100, 16384));
    }

    @Test
    void maxThresholdAllows30000AndRejects30001() {
        assertFalse(MaskLimits.exceedsMax(30000, 30000));
        assertTrue(MaskLimits.exceedsMax(30001, 10));
        assertTrue(MaskLimits.exceedsMax(10, 30001));
        assertDoesNotThrow(() -> MaskLimits.checkCanvasSize(30000, 30000));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MaskLimits.checkCanvasSize(30001, 1080));
        assertTrue(ex.getMessage().contains("30000"), "русский текст с пределом: " + ex.getMessage());
    }

    @Test
    void canvasSizeHintWarnsFrom16384AndRefusesAbove30000() {
        assertNull(MaskLimits.canvasSizeHint(16383, 1080));
        assertNotNull(MaskLimits.canvasSizeHint(16384, 1080));
        assertTrue(MaskLimits.canvasSizeHint(30001, 1080).contains("нельзя"));
    }

    @Test
    void largeAndTooLargeSplitAnExportSet() {
        List<MaskLimits.Item> items = List.of(
                new MaskLimits.Item("обычный", 1920, 1080),
                new MaskLimits.Item("ровно 16k", 16384, 512),
                new MaskLimits.Item("предел", 30000, 2000),
                new MaskLimits.Item("слишком", 30001, 2000));
        assertEquals(List.of("ровно 16k", "предел"), MaskLimits.large(items).stream().map(MaskLimits.Item::label).toList());
        assertEquals(List.of("слишком"), MaskLimits.tooLarge(items).stream().map(MaskLimits.Item::label).toList());
        assertEquals("слишком — 30001×2000 px", MaskLimits.tooLarge(items).get(0).describe());
    }

    @Test
    void addCanvasAcceptsUpTo30000AndRejects30001(@TempDir Path dir) {
        AppModel model = newModelWithScene(dir);
        ContentCanvas big = model.addCanvas("Большой", 30000, 20000);
        assertEquals(30000, big.getWidthPx());

        int before = model.canvasesForCurrentScene().size();
        assertThrows(IllegalArgumentException.class, () -> model.addCanvas("Слишком", 30001, 1080));
        assertThrows(IllegalArgumentException.class, () -> model.addCanvas("Слишком", 1920, 30001));
        assertEquals(before, model.canvasesForCurrentScene().size(), "отказ не должен добавить канвас");
    }

    @Test
    void updateCanvasRejects30001AndKeepsPreviousSize(@TempDir Path dir) {
        AppModel model = newModelWithScene(dir);
        ContentCanvas c = model.addCanvas("К", 1920, 1080);
        model.updateCanvas(c, "К", 20000, 2000);
        assertEquals(20000, c.getWidthPx());

        assertThrows(IllegalArgumentException.class, () -> model.updateCanvas(c, "Новое имя", 30001, 2000));
        assertEquals(20000, c.getWidthPx(), "размер не должен измениться");
        assertEquals("К", c.getName(), "и имя тоже — правка отклонена целиком");
    }
}
