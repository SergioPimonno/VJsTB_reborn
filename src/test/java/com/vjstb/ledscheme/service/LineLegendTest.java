package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Авто-блок «Легенда линий» (docs/schema-ports-rework/PLAN.md, задача T5.4) —
 *  {@link AppModel#lineLegendRoles}/{@link AppModel#lineLegendPowerNominals} (данные
 *  для содержимого, цвета резолвит {@code SchemaCanvasPanel.drawLineLegendContent}) и
 *  {@link AppModel#addLineLegendNode}. */
class LineLegendTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    @Test
    void signalRolesListOnlyRolesActuallyUsedByExistingEdgesInFirstSeenOrder(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene scene = model.getCurrentScene();

        SchemaNode server = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Server", 0, 0, null);
        model.addCardToNode(server, "Card", List.of(
                new CardPort("HDMI 2.0", PortDirection.OUT, 1),      // эвристика -> VIDEO
                new CardPort("Ethernet Cat6", PortDirection.OUT, 1), // не LED-узел -> NETWORK
                new CardPort("XLR", PortDirection.OUT, 1)));         // не подключено — не должно попасть в легенду
        SchemaNode sink = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Sink", 300, 0, null);
        model.addCardToNode(sink, "Card", List.of(
                new CardPort("HDMI 2.0", PortDirection.IN, 1),
                new CardPort("Ethernet", PortDirection.IN, 1)));

        CardPort hdmiOut = server.getCards().get(0).getPorts().get(0);
        CardPort ethOut = server.getCards().get(0).getPorts().get(1);
        CardPort hdmiIn = sink.getCards().get(0).getPorts().get(0);
        CardPort ethIn = sink.getCards().get(0).getPorts().get(1);
        model.addSchemaEdge(SchemaMode.SIGNAL, server.getId(), hdmiOut.getId(), sink.getId(), hdmiIn.getId(), null);
        model.addSchemaEdge(SchemaMode.SIGNAL, server.getId(), ethOut.getId(), sink.getId(), ethIn.getId(), null);

        assertEquals(List.of(InterfaceRole.VIDEO, InterfaceRole.NETWORK), model.lineLegendRoles(scene));
    }

    @Test
    void signalRolesEmptyWhenSceneHasNoSignalEdges(@TempDir Path dir) {
        AppModel model = model(dir);
        assertTrue(model.lineLegendRoles(model.getCurrentScene()).isEmpty());
    }

    @Test
    void powerNominalsListDistinctConnectorTypesInFirstSeenOrder(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene scene = model.getCurrentScene();

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Фишка", 0, 0, null);
        CardPort out32 = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 3, null);
        CardPort out16 = model.addPowerConnectorToNode(source, "CEE 16A", PortDirection.OUT, 2, 1, null);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 300, 0, null);
        CardPort aIn32 = model.addPowerConnectorToNode(a, "CEE 32A", PortDirection.IN, 1, 3, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 300, 100, null);
        CardPort bIn32 = model.addPowerConnectorToNode(b, "CEE 32A", PortDirection.IN, 1, 3, null);
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "C", 300, 200, null);
        CardPort cIn16 = model.addPowerConnectorToNode(c, "CEE 16A", PortDirection.IN, 1, 1, null);

        // Два разных потребителя одного номинала 32A — в легенде он должен встретиться
        // ровно один раз, а не дважды.
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), out32.getId(), a.getId(), aIn32.getId(), null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), out32.getId(), b.getId(), bIn32.getId(), null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), out16.getId(), c.getId(), cIn16.getId(), null);

        assertEquals(List.of("CEE 32A", "CEE 16A"), model.lineLegendPowerNominals(scene));
    }

    @Test
    void addLineLegendNodeCreatesAutoLegendCustomNodeInGivenMode(@TempDir Path dir) {
        AppModel model = model(dir);

        SchemaNode signalLegend = model.addLineLegendNode(SchemaMode.SIGNAL, 10, 20);
        assertEquals(SchemaMode.SIGNAL, signalLegend.getMode());
        assertEquals(SchemaNodeType.CUSTOM, signalLegend.getType());
        assertTrue(signalLegend.isAutoLineLegend());
        assertFalse(signalLegend.isAutoPortLegend(), "не должен подменять собой отдельный флаг легенды портов");

        SchemaNode powerLegend = model.addLineLegendNode(SchemaMode.POWER, 10, 20);
        assertEquals(SchemaMode.POWER, powerLegend.getMode());
        assertTrue(powerLegend.isAutoLineLegend());
    }

    @Test
    void copyPreservesAutoLineLegendFlag(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode legend = model.addLineLegendNode(SchemaMode.SIGNAL, 0, 0);

        SchemaNode copy = legend.copy();

        assertTrue(copy.isAutoLineLegend());
    }
}
