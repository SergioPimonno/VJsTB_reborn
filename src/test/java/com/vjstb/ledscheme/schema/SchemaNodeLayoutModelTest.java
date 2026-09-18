package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Клиентская модель раскладки гнёзд/маршрутов общей схемы (docs/schema-ports-rework/
 *  PLAN.md, задача T1.2): круговое JSON-сохранение новых полей {@link SchemaNode}/
 *  {@link SchemaEdge}, обратная совместимость со старыми узлами/связями без этих полей,
 *  ремап {@link PortPlacement#getPortId()} при вставке (Ctrl+V) копии узла и legacy-
 *  резолв {@link SchemaEdge#effectiveRouteMode()}. */
class SchemaNodeLayoutModelTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void roundTripsOrientationAndPortPlacementsThroughJson(@TempDir Path dir) {
        File file = new File(dir.toFile(), "workspace.json");
        AppModel model = new AppModel(new WorkspaceStore(file));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Q8", 0, 0, null);
        SchemaCard card = model.addCardToNode(node, "Вход", List.of(new CardPort("HDMI", PortDirection.IN, 4)));
        String portId = card.getPorts().get(0).getId();

        node.setOrientation(NodeOrientation.DOWN);
        node.setOnlyUsedPorts(true);
        node.setNetworkDeviceTypeId("nettype-1");
        PortPlacement pp = new PortPlacement(portId);
        pp.setSide(NodeSide.LEFT);
        pp.setOrder(2.5);
        pp.setCollapsed(Boolean.TRUE);
        pp.setRoleOverride(InterfaceRole.SYNC);
        pp.setThruOverride(Boolean.FALSE);
        node.getPortPlacements().add(pp);
        // мутация полей выше идёт напрямую по ссылке на живой узел сцены (мутаторы этих
        // конкретных полей появятся в T3.3) — changed() нужен явно, чтобы сохранить на диск.
        model.updateSchemaNode(node, node.getLabel(), node.getType(), node.getScreenRefId());

        AppModel reloaded = new AppModel(new WorkspaceStore(file));
        Workspace ws = reloaded.getWorkspace();
        SchemaNode rn = ws.getProjects().get(0).getScenes().get(0).getSchemaNodes().get(0);

        assertEquals(NodeOrientation.DOWN, rn.getOrientation());
        assertTrue(rn.isOnlyUsedPorts());
        assertEquals("nettype-1", rn.getNetworkDeviceTypeId());
        PortPlacement rpp = rn.findPortPlacement(portId);
        assertEquals(NodeSide.LEFT, rpp.getSide());
        assertEquals(2.5, rpp.getOrder());
        assertEquals(Boolean.TRUE, rpp.getCollapsed());
        assertEquals(InterfaceRole.SYNC, rpp.getRoleOverride());
        assertEquals(Boolean.FALSE, rpp.getThruOverride());
    }

    @Test
    void nodeWithoutNewFieldsReadsWithNullDefaults(@TempDir Path dir) {
        // Имитация СТАРОГО проекта — узел никогда не трогал новые поля (типичный случай
        // для любого уже сохранённого до этой переработки workspace.json).
        File file = new File(dir.toFile(), "workspace.json");
        AppModel model = new AppModel(new WorkspaceStore(file));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 0, null);

        AppModel reloaded = new AppModel(new WorkspaceStore(file));
        SchemaNode rn = reloaded.getWorkspace().getProjects().get(0).getScenes().get(0).getSchemaNodes().get(0);
        assertNull(rn.getOrientation(), "без явной ориентации — берётся умолчание профиля, не RIGHT жёстко");
        assertTrue(rn.getPortPlacements().isEmpty());
        assertTrue(!rn.isOnlyUsedPorts());
        assertNull(rn.getNetworkDeviceTypeId());
    }

    @Test
    void pasteRemapsPortPlacementToNewPortId(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Q8", 0, 0, null);
        SchemaCard card = model.addCardToNode(node, "Вход", List.of(new CardPort("HDMI", PortDirection.IN, 4)));
        String originalPortId = card.getPorts().get(0).getId();
        PortPlacement pp = new PortPlacement(originalPortId);
        pp.setSide(NodeSide.TOP);
        node.getPortPlacements().add(pp);

        List<SchemaNode> pasted = model.pasteSchemaNodes(SchemaMode.SIGNAL, List.of(node.copy()), List.of(), 50, 50);
        assertEquals(1, pasted.size());
        SchemaNode copy = pasted.get(0);
        String newPortId = copy.getCards().get(0).getPorts().get(0).getId();

        assertTrue(!newPortId.equals(originalPortId), "у вставленной копии гнёзда получают новые id");
        PortPlacement copiedPlacement = copy.findPortPlacement(newPortId);
        assertEquals(NodeSide.TOP, copiedPlacement.getSide(),
                "раскладка группы должна следовать за гнездом на его НОВЫЙ id, а не потеряться/остаться на старом");
        assertEquals(null, copy.findPortPlacement(originalPortId), "записи со старым id быть не должно");
    }

    @Test
    void edgeRouteModeLegacyResolution() {
        SchemaEdge straight = new SchemaEdge(SchemaMode.SIGNAL, "a", "b", null);
        assertEquals(EdgeRouteMode.STRAIGHT, straight.effectiveRouteMode(), "без waypoints и без явного режима — прямая, как раньше");

        SchemaEdge manual = new SchemaEdge(SchemaMode.SIGNAL, "a", "b", null);
        manual.setWaypoints(List.of(new EdgeWaypoint(10, 10)));
        assertEquals(EdgeRouteMode.MANUAL, manual.effectiveRouteMode(),
                "непустые waypoints без явного режима — существующая ломаная, не авто-трассировка");

        SchemaEdge explicitAuto = new SchemaEdge(SchemaMode.SIGNAL, "a", "b", null);
        explicitAuto.setWaypoints(List.of(new EdgeWaypoint(10, 10)));
        explicitAuto.setRouteMode(EdgeRouteMode.AUTO);
        assertEquals(EdgeRouteMode.AUTO, explicitAuto.effectiveRouteMode(), "явный режим всегда побеждает legacy-резолв");
    }
}
