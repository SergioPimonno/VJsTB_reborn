package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт пользователя 2026-09-18: "не работает групповое выделение линий,
 *  только по одной" — Shift/Ctrl+клик по связи (см. {@link
 *  SchemaCanvasPanel#toggleEdgeSelectionForTest}) должен накапливать многовыделение,
 *  как уже работало для узлов, а не всегда сужать до одной связи. Клики через
 *  реальный {@code MouseEvent} здесь не эмулируются — тестируется сама логика
 *  выделения ({@code toggleEdgeSelection}/{@code selectSingleEdge}), которую дёргает
 *  обработчик мыши; сама доставка события — стандартный Swing-механизм, не
 *  специфичный для этой фичи. */
class SchemaEdgeMultiSelectTest {

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

    /** Три узла-источника, каждый с одним HDMI-выходом, все включены в одного
     *  общего приёмника — три независимые связи для многовыделения. */
    private static SchemaEdge[] threeEdgesIntoOneSink(AppModel model) {
        SchemaNode sink = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "Sink", 500, 0, null);
        CardPort sinkIn = model.addCardToNode(sink, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 3))).getPorts().get(0);
        SchemaEdge[] result = new SchemaEdge[3];
        for (int i = 0; i < 3; i++) {
            SchemaNode src = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "Src" + i, 0, i * 150, null);
            CardPort out = model.addCardToNode(src, "Видео", List.of(
                    new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
            result[i] = model.addSchemaEdge(SchemaMode.SIGNAL, src.getId(), out.getId(), sink.getId(), sinkIn.getId(), null);
        }
        return result;
    }

    @Test
    void shiftClickTogglesEdgesIntoAndOutOfTheSelectionSet(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge[] e = threeEdgesIntoOneSink(model);
        SchemaCanvasPanel canvas = canvas(model, dir);

        canvas.selectSingleEdgeForTest(e[0]);
        assertEquals(Set.of(e[0]), canvas.getSelectedEdges());

        canvas.toggleEdgeSelectionForTest(e[1]);
        assertEquals(Set.of(e[0], e[1]), canvas.getSelectedEdges(), "Shift+клик по второй связи должен ДОБАВИТЬ её, не заменить выделение");
        assertEquals(e[1], canvas.getSelectedEdge(), "последняя добавленная связь становится \"главной\"");

        canvas.toggleEdgeSelectionForTest(e[2]);
        assertEquals(Set.of(e[0], e[1], e[2]), canvas.getSelectedEdges());

        canvas.toggleEdgeSelectionForTest(e[1]);
        assertEquals(Set.of(e[0], e[2]), canvas.getSelectedEdges(), "повторный Shift+клик по уже выделенной связи должен УБРАТЬ её");
        assertTrue(canvas.getSelectedEdge() == e[0] || canvas.getSelectedEdge() == e[2],
                "\"главная\" связь переезжает на оставшуюся в наборе, если убрали именно её");
    }

    @Test
    void plainClickNarrowsSelectionToOneEdge(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge[] e = threeEdgesIntoOneSink(model);
        SchemaCanvasPanel canvas = canvas(model, dir);

        canvas.toggleEdgeSelectionForTest(e[0]);
        canvas.toggleEdgeSelectionForTest(e[1]);
        assertEquals(2, canvas.getSelectedEdges().size());

        canvas.selectSingleEdgeForTest(e[2]);
        assertEquals(Set.of(e[2]), canvas.getSelectedEdges(), "обычный клик (без Shift/Ctrl) должен сузить выделение до одной связи");
    }

    @Test
    void toggleEdgeSelectionOffEmptiesSelectedEdgeWhenSetBecomesEmpty(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge[] e = threeEdgesIntoOneSink(model);
        SchemaCanvasPanel canvas = canvas(model, dir);

        canvas.toggleEdgeSelectionForTest(e[0]);
        canvas.toggleEdgeSelectionForTest(e[0]);

        assertTrue(canvas.getSelectedEdges().isEmpty());
        assertNull(canvas.getSelectedEdge());
    }

    @Test
    void rerouteSelectedRetracesEveryEdgeInTheMultiSelection(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge[] e = threeEdgesIntoOneSink(model);
        for (SchemaEdge edge : e) {
            model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(50, 50)));
        }
        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.toggleEdgeSelectionForTest(e[0]);
        canvas.toggleEdgeSelectionForTest(e[2]);

        canvas.rerouteSelected();

        assertEquals(EdgeRouteMode.AUTO, e[0].effectiveRouteMode(), "выделенная связь 0 должна перейти в AUTO");
        assertEquals(EdgeRouteMode.AUTO, e[2].effectiveRouteMode(), "выделенная связь 2 должна перейти в AUTO");
        assertFalse(e[1].effectiveRouteMode() == EdgeRouteMode.AUTO,
                "НЕвыделенная связь 1 не должна была тронута — иначе это 'перетрассировать все', а не 'выделенные'");
    }

    @Test
    void deleteSelectedRemovesTheWholeMultiSelectionAsOneUndoStep(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaEdge[] e = threeEdgesIntoOneSink(model);
        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.toggleEdgeSelectionForTest(e[0]);
        canvas.toggleEdgeSelectionForTest(e[1]);

        canvas.deleteSelected();

        List<SchemaEdge> remaining = model.getCurrentScene().getSchemaEdges();
        assertEquals(1, remaining.size(), "должна остаться ровно НЕвыделенная связь 2");
        assertTrue(remaining.contains(e[2]));

        model.undo();
        assertEquals(3, model.getCurrentScene().getSchemaEdges().size(), "один Ctrl+Z должен вернуть ОБЕ удалённые связи разом");
    }
}
