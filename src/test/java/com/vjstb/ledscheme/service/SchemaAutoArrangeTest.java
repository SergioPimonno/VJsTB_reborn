package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Запрос пользователя 2026-10-02: кнопка авторасстановки блоков схемы колонками слева направо —
 *  сигнал: серверы → другое оборудование → контроллеры → конвертеры → экраны; питание: источники без
 *  входов → источники со входами → распределение → экраны и всё остальное. */
class SchemaAutoArrangeTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        return model;
    }

    private static SchemaNode node(AppModel m, SchemaMode mode, SchemaNodeType type, String label, double x, double y) {
        SchemaNode n = m.addSchemaNode(mode, type, label, x, y, null);
        n.setWidth(200);
        n.setHeight(80);
        return n;
    }

    @Test
    void signalColumnsGoServersOtherControllersConvertersScreens(@TempDir Path dir) {
        AppModel m = model(dir);
        // намеренно перемешанные позиции
        SchemaNode screen = node(m, SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Экран", 0, 0);
        SchemaNode converter = node(m, SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "Конв", 500, 300);
        SchemaNode controller = node(m, SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "Контр", 100, 700);
        SchemaNode other = node(m, SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Прочее", 900, 20);
        SchemaNode server = node(m, SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Сервер", 300, 400);

        assertEquals(5, m.arrangeSchemaNodes(SchemaMode.SIGNAL));

        assertTrue(server.getX() < other.getX());
        assertTrue(other.getX() < controller.getX());
        assertTrue(controller.getX() < converter.getX());
        assertTrue(converter.getX() < screen.getX());
        // колонки не перекрываются: следующая правее правого края предыдущей
        assertTrue(other.getX() >= server.getX() + server.getWidth());
    }

    @Test
    void emptyColumnsAreSkippedAndStackInOneColumnKeepsVerticalOrder(@TempDir Path dir) {
        AppModel m = model(dir);
        SchemaNode lower = node(m, SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Нижний", 50, 400);
        SchemaNode upper = node(m, SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Верхний", 800, 10);
        SchemaNode server = node(m, SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Сервер", 300, 200);

        m.arrangeSchemaNodes(SchemaMode.SIGNAL);

        assertEquals(upper.getX(), lower.getX(), 1e-9, "оба экрана в одной колонке");
        assertTrue(upper.getY() < lower.getY(), "порядок сверху вниз сохраняется по прежней высоте");
        assertEquals(server.getX() + 200 + 140, upper.getX(), 1e-9, "пустые колонки пропущены, зазор между колонками");
        // меньшая колонка центрируется относительно большей
        assertEquals(upper.getY() + (upper.getHeight() + 50 + lower.getHeight()) / 2.0 - server.getHeight() / 2.0,
                server.getY(), 1e-9);
    }

    @Test
    void powerColumnsSourcesWithoutInputsThenWithInputsThenDistroThenScreensAndRest(@TempDir Path dir) {
        AppModel m = model(dir);
        SchemaNode screen = node(m, SchemaMode.POWER, SchemaNodeType.SCREEN, "Экран", 0, 0);
        SchemaNode distro = node(m, SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная", 700, 0);
        SchemaNode fed = node(m, SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит со входом", 20, 500);
        fed.getPowerConnectors().add(new CardPort("CEE 32A", PortDirection.IN, 1));
        SchemaNode gen = node(m, SchemaMode.POWER, SchemaNodeType.SOURCE, "Генератор", 400, 900);
        gen.getPowerConnectors().add(new CardPort("CEE 125A", PortDirection.OUT, 1));
        SchemaNode server = node(m, SchemaMode.POWER, SchemaNodeType.SERVER, "Сервер", 300, 300);

        m.arrangeSchemaNodes(SchemaMode.POWER);

        assertTrue(gen.getX() < fed.getX(), "источник без входных разъёмов левее источника со входным");
        assertTrue(fed.getX() < distro.getX());
        assertTrue(distro.getX() < screen.getX());
        assertEquals(screen.getX(), server.getX(), 1e-9, "экраны и прочее — одна колонка");
        assertTrue(screen.getY() < server.getY(), "в ней экраны выше прочего оборудования");
    }

    @Test
    void arrangementIsOneUndoStepAndResetsManualRoutes(@TempDir Path dir) {
        AppModel m = model(dir);
        SchemaNode a = node(m, SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "A", 700, 10);
        SchemaNode b = node(m, SchemaMode.SIGNAL, SchemaNodeType.SERVER, "B", 10, 300);
        SchemaEdge edge = m.addSchemaEdge(SchemaMode.SIGNAL, b.getId(), a.getId(), null);
        edge.setRouteMode(EdgeRouteMode.MANUAL);
        edge.setWaypoints(List.of(new EdgeWaypoint(100, 100)));
        String aId = a.getId();
        String bId = b.getId();
        String edgeId = edge.getId();

        m.arrangeSchemaNodes(SchemaMode.SIGNAL);

        SchemaEdge after = m.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(e -> e.getId().equals(edgeId)).findFirst().orElseThrow();
        assertEquals(EdgeRouteMode.AUTO, after.effectiveRouteMode());
        assertTrue(after.getWaypoints().isEmpty());

        m.undo();

        SchemaNode a2 = m.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(n -> n.getId().equals(aId)).findFirst().orElseThrow();
        SchemaNode b2 = m.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(n -> n.getId().equals(bId)).findFirst().orElseThrow();
        assertEquals(700, a2.getX(), 1e-9);
        assertEquals(10, b2.getX(), 1e-9);
        SchemaEdge restored = m.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(e -> e.getId().equals(edgeId)).findFirst().orElseThrow();
        assertEquals(EdgeRouteMode.MANUAL, restored.effectiveRouteMode());
        assertEquals(1, restored.getWaypoints().size());
    }

    @Test
    void emptySchemeDoesNothing(@TempDir Path dir) {
        assertEquals(0, model(dir).arrangeSchemaNodes(SchemaMode.SIGNAL));
    }
}
