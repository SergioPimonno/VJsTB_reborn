package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Шрифт схемы…» на холсте (запрос пользователя 2026-09-30, пункт 1, трек C4):
 * размер схемы действительно меняет рисунок блоков и подписей линий, а блоки и линии
 * со СВОИМ размером остаются нарисованными ровно так же — это то, ради чего размер
 * схемы не пишется в узлы. Сравнение — структурное (картинка изменилась / не
 * изменилась), не побайтовое, как у остальных тестов визуальной части.
 */
class SchemaSheetFontSizeRenderTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static int[] pixels(SchemaCanvasPanel canvas) {
        BufferedImage img = canvas.renderImage(700, 300, false);
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    @Test
    void schemeSizeRedrawsDefaultBlocksAndLabelsButNotOnesWithOwnSize(@TempDir Path dir) {
        AppModel model = model(dir);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Блок А", 20, 20, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Блок Б", 400, 20, null);
        SchemaEdge e = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        e.setLabel("кабель");
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);

        int[] before = pixels(canvas);
        model.setSchemaSheetFontSizes(model.currentSchemaSheet(SchemaMode.POWER), 24, 22);
        int[] afterScheme = pixels(canvas);

        assertFalse(Arrays.equals(before, afterScheme), "блоки и подпись линии без своего размера должны перерисоваться");

        // теперь у ВСЕХ блоков и линии свой размер — смена шрифта схемы рисунок не меняет
        model.setSchemaNodesFontSize(List.of(a, b), 12);
        model.setSchemaEdgeFontSize(e, 11);
        int[] custom = pixels(canvas);
        model.setSchemaSheetFontSizes(model.currentSchemaSheet(SchemaMode.POWER), 40, 36);
        int[] customAfter = pixels(canvas);

        assertTrue(Arrays.equals(custom, customAfter), "собственный размер блоков/линий шрифт схемы не затрагивает");
        assertEquals(12, model.effectiveNodeFontSize(a));
    }
}
