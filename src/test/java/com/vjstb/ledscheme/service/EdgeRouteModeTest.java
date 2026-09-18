package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T4.4 (§2.6) — {@code EdgeRouteMode} на
 *  {@link AppModel}: явный начальный режим при создании связи, переключатель режима,
 *  и «Перетрассировать» (переводит в AUTO и стирает изломы одной записью отмены). */
class EdgeRouteModeTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaNode[] twoNodes(AppModel model) {
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        return new SchemaNode[]{a, b};
    }

    @Test
    void oldStyleAddSchemaEdgeLeavesRouteModeNullSoOldProjectsResolveToLegacy(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode[] nodes = twoNodes(model);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, nodes[0].getId(), nodes[1].getId(), null);
        assertNull(edge.getRouteMode(), "8/более-старые перегрузки не должны навязывать режим новым/тестовым вызовам");
        assertEquals(EdgeRouteMode.STRAIGHT, edge.effectiveRouteMode());
    }

    @Test
    void explicitInitialRouteModeIsAppliedOnCreation(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode[] nodes = twoNodes(model);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, nodes[0].getId(), null, null,
                nodes[1].getId(), null, null, null, EdgeRouteMode.AUTO);
        assertEquals(EdgeRouteMode.AUTO, edge.getRouteMode());
        assertEquals(EdgeRouteMode.AUTO, edge.effectiveRouteMode());
    }

    @Test
    void creatingEdgeWithInitialRouteModeUndoesAsOneStep(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode[] nodes = twoNodes(model);
        int before = model.getCurrentScene().getSchemaEdges().size();

        model.addSchemaEdge(SchemaMode.SIGNAL, nodes[0].getId(), null, null,
                nodes[1].getId(), null, null, null, EdgeRouteMode.AUTO);
        assertEquals(before + 1, model.getCurrentScene().getSchemaEdges().size());

        model.undo();
        assertEquals(before, model.getCurrentScene().getSchemaEdges().size(),
                "один Ctrl+Z должен убрать связь целиком, включая заданный вместе с ней routeMode");
    }

    @Test
    void setEdgeRouteModeChangesModeAndUndoes(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode[] nodes = twoNodes(model);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, nodes[0].getId(), nodes[1].getId(), null);

        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);
        assertEquals(EdgeRouteMode.MANUAL, edge.getRouteMode());

        // undo() восстанавливает СНИМОК (копии объектов), а не мутирует edge на
        // месте — после отмены нужно заново достать связь из сцены по id, старая
        // ссылка на объект остаётся указывать на уже отсоединённую от сцены копию.
        model.undo();
        assertNull(edgeById(model, edge.getId()).getRouteMode());
    }

    private static SchemaEdge edgeById(AppModel model, String id) {
        for (SchemaEdge e : model.getCurrentScene().getSchemaEdges()) {
            if (e.getId().equals(id)) {
                return e;
            }
        }
        throw new IllegalStateException("Связь не найдена: " + id);
    }

    @Test
    void rerouteEdgesSetsAutoAndClearsWaypointsInOneUndoStep(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode[] nodes = twoNodes(model);
        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.SIGNAL, nodes[0].getId(), nodes[1].getId(), null);
        model.setSchemaEdgeWaypoints(e1, List.of(new EdgeWaypoint(150, 50)));
        model.setEdgeRouteMode(e1, EdgeRouteMode.MANUAL);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.SIGNAL, nodes[1].getId(), nodes[0].getId(), null);

        model.rerouteEdges(List.of(e1, e2));

        assertEquals(EdgeRouteMode.AUTO, e1.getRouteMode());
        assertTrue(e1.getWaypoints().isEmpty(), "AUTO не использует сохранённые изломы — их незачем хранить дальше");
        assertEquals(EdgeRouteMode.AUTO, e2.getRouteMode());

        model.undo();
        SchemaEdge e1After = edgeById(model, e1.getId());
        SchemaEdge e2After = edgeById(model, e2.getId());
        assertEquals(EdgeRouteMode.MANUAL, e1After.getRouteMode(), "один Ctrl+Z должен откатить ОБЕ связи разом");
        assertEquals(1, e1After.getWaypoints().size());
        assertNull(e2After.getRouteMode());
    }

    @Test
    void rerouteEdgesWithEmptyCollectionDoesNothing(@TempDir Path dir) {
        AppModel model = model(dir);
        model.rerouteEdges(List.of());
        // Не должно бросать и не должно создавать запись отмены — просто не падает.
    }

    @Test
    void directionAwarePowerConnectorConstructorStillUsableAfterOverloadAdded(@TempDir Path dir) {
        // Регресс-проверка: добавление 9-аргового addSchemaEdge не должно ломать
        // существующий 4-аргов вызов (перегрузки не должны конфликтовать).
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort out = model.addPowerConnectorToNode(a, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 300, 0, null);
        CardPort in = model.addPowerConnectorToNode(b, "CEE 32A", PortDirection.IN, 1);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, a.getId(), out.getId(), b.getId(), in.getId(), null);
        assertNull(edge.getRouteMode());
    }
}
