package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.schemalayout.SchemaFontSizes;
import com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Размер шрифта ВСЕЙ схемы — кнопка «Шрифт схемы…» (запрос пользователя 2026-09-30,
 * docs/masks-and-schema-sheets/PLAN.md, пункт 1, трек C4: «для всех блоков с
 * дефолтным размером; блоки с кастомным размером не трогать»). Проверяется модельный
 * слой: разрешение «свой → умолчание схемы → стандарт» для блоков и линий, что размер
 * схемы НЕ записывается в узлы/связи (иначе они стали бы «кастомными»), рост блока
 * при увеличении шрифта, независимость схем друг от друга, отмена и сохранение.
 */
class SchemaSheetFontSizeTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaSheet power(AppModel model) {
        return model.currentSchemaSheet(SchemaMode.POWER);
    }

    @Test
    void nodeWithoutOwnSizeTakesSchemeSizeAndOwnSizeWins(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode plain = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Plain", 0, 0, null);
        SchemaNode custom = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Custom", 200, 0, null);
        model.setSchemaNodesFontSize(List.of(custom), 18);

        model.setSchemaSheetFontSizes(power(model), 14, null);

        assertEquals(14, model.effectiveNodeFontSize(plain));
        assertEquals(Integer.valueOf(14), model.schemaNodeFontSizeOverride(plain));
        assertEquals(18, model.effectiveNodeFontSize(custom), "собственный размер блока побеждает размер схемы");
    }

    @Test
    void withoutSchemeSizeNodeFallsBackToStandard(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode n = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "N", 0, 0, null);

        assertEquals(SchemaLayoutMetrics.LABEL_FONT_SIZE, model.effectiveNodeFontSize(n));
        assertNull(model.schemaNodeFontSizeOverride(n), "нигде не задан — холст не подменяет шрифт");
    }

    @Test
    void edgeWithoutOwnSizeTakesSchemeEdgeSizeAndOwnSizeWins(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 300, 0, null);
        SchemaEdge plain = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        SchemaEdge custom = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        model.setSchemaEdgeFontSize(custom, 16);

        assertEquals(SchemaFontSizes.EDGE_FONT_SIZE, model.effectiveEdgeFontSize(plain));

        model.setSchemaSheetFontSizes(power(model), null, 13);

        assertEquals(13, model.effectiveEdgeFontSize(plain));
        assertEquals(16, model.effectiveEdgeFontSize(custom));
        assertEquals(SchemaLayoutMetrics.LABEL_FONT_SIZE, model.effectiveNodeFontSize(a),
                "размер линий схемы на блоки не влияет");
    }

    @Test
    void schemeSizeIsNeverWrittenIntoNodesOrEdges(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 300, 0, null);
        SchemaEdge e = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);

        model.setSchemaSheetFontSizes(power(model), 20, 18);

        assertNull(a.getFontSize(), "иначе блок стал бы «кастомным» и повторная смена схемы его бы не затронула");
        assertNull(b.getFontSize());
        assertNull(e.getFontSize());

        model.setSchemaSheetFontSizes(power(model), 11, 12);
        assertEquals(11, model.effectiveNodeFontSize(a));
        assertEquals(12, model.effectiveEdgeFontSize(e));
    }

    @Test
    void zeroResetsSchemeSizeToStandard(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        model.setSchemaSheetFontSizes(power(model), 20, 20);

        model.setSchemaSheetFontSizes(power(model), 0, null);

        assertNull(power(model).getDefaultFontSize());
        assertNull(power(model).getDefaultEdgeFontSize());
        assertEquals(SchemaLayoutMetrics.LABEL_FONT_SIZE, model.effectiveNodeFontSize(a));
    }

    @Test
    void resettingNodeOwnSizeReturnsItToSchemeSize(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        model.setSchemaSheetFontSizes(power(model), 15, null);
        model.setSchemaNodesFontSize(List.of(a), 22);
        assertEquals(22, model.effectiveNodeFontSize(a));

        model.setSchemaNodesFontSize(List.of(a), null); // «0 — как у схемы»

        assertEquals(15, model.effectiveNodeFontSize(a));
    }

    @Test
    void schemeSizeAppliesOnlyToItsOwnSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode onFirst = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Первая", 0, 0, null);
        SchemaSheet first = power(model);
        model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaNode onSecond = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Вторая", 0, 0, null);

        model.setSchemaSheetFontSizes(first, 20, null);

        assertEquals(20, model.effectiveNodeFontSize(onFirst));
        assertEquals(SchemaLayoutMetrics.LABEL_FONT_SIZE, model.effectiveNodeFontSize(onSecond),
                "размер одной схемы не должен менять другую схему сцены");
        // и узел первой схемы разрешается по СВОЕЙ схеме, даже когда открыта вторая
        assertEquals(20, model.effectiveNodeFontSize(onFirst));
    }

    @Test
    void powerAndSignalSchemesAreIndependent(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode sig = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "S", 0, 0, null);

        model.setSchemaSheetFontSizes(power(model), 20, 20);

        assertEquals(SchemaLayoutMetrics.LABEL_FONT_SIZE, model.effectiveNodeFontSize(sig));
    }

    @Test
    void biggerSchemeFontGrowsBlockMinimumSizeAndNeverShrinksIt(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode n = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 0, null);
        for (int i = 0; i < 4; i++) {
            model.addPowerConnectorToNode(n, "CEE 32A", PortDirection.OUT, 1);
        }
        double w0 = n.getWidth();
        double h0 = n.getHeight();

        model.setSchemaSheetFontSizes(power(model), 28, null);

        assertTrue(n.getWidth() > w0 || n.getHeight() > h0,
                "блок должен подогнаться под крупный шрифт: " + w0 + "x" + h0 + " -> " + n.getWidth() + "x"
                        + n.getHeight());
        assertTrue(n.getWidth() >= w0 && n.getHeight() >= h0);
        double w1 = n.getWidth();
        double h1 = n.getHeight();

        model.setSchemaSheetFontSizes(power(model), null, null);

        assertEquals(w1, n.getWidth(), "блок только растёт, ручной размер пользователя не отбирается");
        assertEquals(h1, n.getHeight());
    }

    @Test
    void blockWithOwnSizeIsNotResizedBySchemeChange(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode n = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 0, null);
        for (int i = 0; i < 4; i++) {
            model.addPowerConnectorToNode(n, "CEE 32A", PortDirection.OUT, 1);
        }
        model.setSchemaNodesFontSize(List.of(n), 10);
        double w = n.getWidth();
        double h = n.getHeight();

        model.setSchemaSheetFontSizes(power(model), 28, null);

        assertEquals(w, n.getWidth(), "свой размер блока 10 — шрифт схемы его раскладку не меняет");
        assertEquals(h, n.getHeight());
    }

    @Test
    void oneUndoRevertsSchemeSizeAndBlockGrowth(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode n = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 0, null);
        for (int i = 0; i < 4; i++) {
            model.addPowerConnectorToNode(n, "CEE 32A", PortDirection.OUT, 1);
        }
        double w0 = n.getWidth();
        double h0 = n.getHeight();
        model.setSchemaSheetFontSizes(power(model), 28, 20);

        model.undo();

        assertNull(power(model).getDefaultFontSize());
        assertNull(power(model).getDefaultEdgeFontSize());
        SchemaNode after = model.schemaNodesForCurrentScene(SchemaMode.POWER).get(0);
        assertEquals(w0, after.getWidth());
        assertEquals(h0, after.getHeight());
    }

    @Test
    void unchangedSizesDoNotPushUndoEntry(@TempDir Path dir) {
        AppModel model = model(dir);
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        model.setSchemaSheetFontSizes(power(model), 14, null);

        model.setSchemaSheetFontSizes(power(model), 14, null); // то же самое
        model.undo(); // откатывает ПЕРВУЮ установку, а не «пустую» вторую

        assertNull(power(model).getDefaultFontSize());
    }

    @Test
    void schemeSizesSurviveSaveAndReload(@TempDir Path dir) {
        AppModel model = model(dir);
        model.setSchemaSheetFontSizes(power(model), 17, 12);

        AppModel reloaded = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        reloaded.selectProject(reloaded.getProjects().get(0));
        reloaded.selectScene(reloaded.getCurrentProject().getScenes().get(0));

        assertEquals(Integer.valueOf(17), reloaded.currentSchemaSheet(SchemaMode.POWER).getDefaultFontSize());
        assertEquals(Integer.valueOf(12), reloaded.currentSchemaSheet(SchemaMode.POWER).getDefaultEdgeFontSize());
    }

    @Test
    void duplicatedSheetKeepsSchemeSizes(@TempDir Path dir) {
        AppModel model = model(dir);
        model.setSchemaSheetFontSizes(power(model), 17, 12);

        SchemaSheet copy = model.duplicateSchemaSheet(power(model));

        assertEquals(Integer.valueOf(17), copy.getDefaultFontSize());
        assertEquals(Integer.valueOf(12), copy.getDefaultEdgeFontSize());
    }

    @Test
    void normalizeTreatsNonPositiveAsStandardAndClampsTo72() {
        assertNull(SchemaFontSizes.normalize(null));
        assertNull(SchemaFontSizes.normalize(0));
        assertNull(SchemaFontSizes.normalize(-3));
        assertEquals(Integer.valueOf(9), SchemaFontSizes.normalize(9));
        assertEquals(Integer.valueOf(72), SchemaFontSizes.normalize(500));
    }
}
