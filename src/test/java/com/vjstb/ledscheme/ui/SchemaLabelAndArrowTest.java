package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.ArrowPlacement;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T4.5 (D14/D15) — пустой чип-приглашение
 *  «+ подпись» виден только при наведении/выделении и никогда в экспорте; стрелка
 *  направления по умолчанию одна, у приёмника, вместо старого «на каждом отрезке». */
class SchemaLabelAndArrowTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        return new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
    }

    private static SchemaEdge edgeWithoutLabel(AppModel model) {
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        return model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
    }

    @Test
    void emptyLabelChipHiddenByDefault(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge edge = edgeWithoutLabel(model);
        SchemaCanvasPanel canvas = canvas(model, dir);

        assertFalse(canvas.shouldShowEmptyLabelChipForTest(edge, false),
                "не выделена, не под курсором — приглашение не нужно");
    }

    @Test
    void emptyLabelChipShownWhenEdgeIsSelected(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge edge = edgeWithoutLabel(model);
        SchemaCanvasPanel canvas = canvas(model, dir);

        assertTrue(canvas.shouldShowEmptyLabelChipForTest(edge, true));
    }

    @Test
    void emptyLabelChipShownWhenEdgeIsHovered(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge edge = edgeWithoutLabel(model);
        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.setHoveredEdgeForTest(edge);

        assertTrue(canvas.shouldShowEmptyLabelChipForTest(edge, false));
    }

    @Test
    void emptyLabelChipNeverShownDuringExportEvenIfSelectedOrHovered(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge edge = edgeWithoutLabel(model);
        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.setHoveredEdgeForTest(edge);
        canvas.setExportingForTest(true);

        assertFalse(canvas.shouldShowEmptyLabelChipForTest(edge, true),
                "экспорт статичный — приглашение некликабельно, только шум (D14)");
    }

    @Test
    void segmentsPlacementDrawsArrowOnEveryNonArcSegment() {
        boolean[] onArc = {false, true, false};
        List<Integer> indices = SchemaCanvasPanel.arrowSegmentIndicesForTest(4, onArc, ArrowPlacement.SEGMENTS);
        assertEquals(List.of(0, 2), indices);
    }

    @Test
    void targetPlacementDrawsExactlyOneArrowOnTheLastNonArcSegment() {
        boolean[] onArc = {false, false, false};
        List<Integer> indices = SchemaCanvasPanel.arrowSegmentIndicesForTest(4, onArc, ArrowPlacement.TARGET);
        assertEquals(List.of(2), indices);
    }

    @Test
    void targetPlacementSkipsTrailingArcSegmentAndUsesThePreviousOne() {
        // Последний сегмент лежит под дугой мостика — стрелка встаёт на предыдущий,
        // а не пропадает вовсе (иначе связь с мостиком у самого приёмника осталась
        // бы совсем без стрелки).
        boolean[] onArc = {false, false, true};
        List<Integer> indices = SchemaCanvasPanel.arrowSegmentIndicesForTest(4, onArc, ArrowPlacement.TARGET);
        assertEquals(List.of(1), indices);
    }

    @Test
    void targetPlacementGivesNoArrowWhenEverySegmentIsUnderAnArc() {
        boolean[] onArc = {true, true};
        List<Integer> indices = SchemaCanvasPanel.arrowSegmentIndicesForTest(3, onArc, ArrowPlacement.TARGET);
        assertTrue(indices.isEmpty());
    }

    @Test
    void arrowPlacementDefaultsToTarget(@TempDir Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        assertEquals(ArrowPlacement.TARGET, settings.activeProfile().getSchemaArrowPlacement());
    }
}
