package com.vjstb.ledscheme.service.graphimport;

import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.port;
import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.power;
import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.graphimport.SchemaGraphImport.Action;
import com.vjstb.ledscheme.service.graphimport.SchemaGraphImport.NodePlan;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Перенос схем Zimmer (yEd) на лист общей схемы: подбор оборудования, выбор экранов, порты связей по семейству
 * разъёма, одна запись отмены на весь импорт.
 */
class SchemaGraphImportTest {

    private AppModel model;

    private AppModel newModel(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        // фрагмент библиотеки пользователя
        var lib = model.getWorkspace().getEquipmentPresets();
        lib.add(signal("CVT4k", port("Fiber", PortDirection.IN, 2), port("Cat6/RJ45", PortDirection.OUT, 16)));
        lib.add(signal("MCTRL4k", port("HDMI 2.0", PortDirection.IN, 1), port("Fiber", PortDirection.OUT, 2),
                port("Cat6/RJ45", PortDirection.OUT, 16)));
        lib.add(signal("H2", port("HDMI 2.0", PortDirection.IN, 1), port("Fiber MMF LC", PortDirection.OUT, 4)));
        lib.add(power("AlpenBox 125A->8xSocapex", port("CEE 125A", PortDirection.IN, 1),
                port("Socapex", PortDirection.OUT, 8)));
        lib.add(power("AlpenBox 125A->4x32A", port("CEE 125A", PortDirection.IN, 1),
                port("CEE 32A", PortDirection.OUT, 4), port("CEE 16A", PortDirection.OUT, 3)));
        lib.add(power("Проходная Socapex", port("Socapex", PortDirection.IN, 1),
                port("Socapex", PortDirection.OUT, 1), port("CEE 16A", PortDirection.OUT, 6)));
        lib.add(power("UPS FOH", port("CEE 16A", PortDirection.IN, 1)));
        return model;
    }

    private static ImportedGraph load(String resource) throws IOException {
        try (InputStream in = SchemaGraphImportTest.class.getResourceAsStream("/graphml/" + resource)) {
            return GraphMlReader.read(in, resource);
        }
    }

    private static NodePlan planOf(List<NodePlan> plans, String label) {
        return plans.stream().filter(p -> p.node().label().equals(label)).findFirst().orElseThrow();
    }

    private SchemaNode nodeLabelled(String label) {
        return model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(n -> label.equals(n.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void proposalPicksKnownEquipmentStubsTheRestAndLeavesScreensToTheUser(@TempDir Path dir) throws Exception {
        newModel(dir);
        List<NodePlan> plans = SchemaGraphImport.propose(load("signal-zimmer.graphml"), SchemaMode.SIGNAL,
                model.getEquipmentPresets());

        assertEquals(15, plans.size(), "группа-папка в план не попадает");
        for (String known : new String[]{"CVT4k", "MCTRL4k", "H2"}) {
            NodePlan p = planOf(plans, known);
            assertEquals(Action.PRESET, p.action(), known);
            assertEquals(known, p.preset().getName());
        }
        assertEquals(Action.STUB, planOf(plans, "Mediabort").action());
        assertEquals(Action.STUB, planOf(plans, "Server").action());
        assertTrue(plans.stream().filter(p -> p.node().kind() == ImportedGraph.Kind.IMAGE)
                .allMatch(p -> p.action() == Action.SKIP), "экран выбирает пользователь");
    }

    @Test
    void applyCreatesASheetNodesAndPortedEdgesInOneUndoStep(@TempDir Path dir) throws Exception {
        newModel(dir);
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        CabinetType type = model.addCabinetType(ct);
        Screen left = model.addScreen("Left", type.getId(), 2, 2, 0, 0);
        Screen right = model.addScreen("Right", type.getId(), 2, 2, 600, 0);

        ImportedGraph graph = load("signal-zimmer.graphml");
        List<NodePlan> plans = SchemaGraphImport.propose(graph, SchemaMode.SIGNAL, model.getEquipmentPresets());
        // пользователь назначил экраны двум картинкам; остальные не переносит
        List<NodePlan> images = plans.stream().filter(p -> p.node().kind() == ImportedGraph.Kind.IMAGE).toList();
        images.get(0).useScreen(left);
        images.get(1).useScreen(right);
        int sheetsBefore = model.schemaSheets(SchemaMode.SIGNAL).size();

        SchemaGraphImport.Result r = SchemaGraphImport.apply(model, SchemaMode.SIGNAL, "Импорт Zimmer", graph, plans);

        assertEquals("Импорт Zimmer", r.sheetName());
        assertEquals(sheetsBefore + 1, model.schemaSheets(SchemaMode.SIGNAL).size());
        assertEquals("Импорт Zimmer", model.currentSchemaSheet(SchemaMode.SIGNAL).getName());
        assertEquals(15 - 3, r.nodes(), "три картинки пропущены");
        assertEquals(2, r.screens());
        assertEquals(r.nodes(), model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());
        assertEquals(SchemaNodeType.SCREEN, nodeLabelled("Left").getType());
        assertEquals(left.getId(), nodeLabelled("Left").getScreenRefId());

        // H2 --fiber 1--> CVT4k: гнёзда семейства fiber с обеих сторон
        SchemaNode h2 = nodeLabelled("H2");
        SchemaEdge fiber1 = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(e -> "fiber 1".equals(e.getLabel())).findFirst().orElseThrow();
        assertEquals(h2.getId(), fiber1.getFromNodeId());
        assertNotNull(fiber1.getFromPortId());
        assertNotNull(fiber1.getToPortId());
        assertEquals("Fiber MMF LC", connectorOf(h2, fiber1.getFromPortId()));
        SchemaNode cvtTo = byId(fiber1.getToNodeId());
        assertEquals("CVT4k", cvtTo.getLabel());
        assertEquals("Fiber", connectorOf(cvtTo, fiber1.getToPortId()));
        // MCTRL4k --fiber5--> CVT4k тоже по волокну
        SchemaEdge fiber5 = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(e -> "fiber5".equals(e.getLabel())).findFirst().orElseThrow();
        assertEquals("Fiber", connectorOf(byId(fiber5.getFromNodeId()), fiber5.getFromPortId()));
        // связь к заглушке без гнёзд (Server → Mediabort «SDI») — между блоками
        SchemaEdge sdi = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(e -> "SDI".equals(e.getLabel())).findFirst().orElseThrow();
        assertNull(sdi.getFromPortId());
        assertNull(sdi.getToPortId());
        // стиль линии: связи создаются с авто-прокладкой под 90°
        assertEquals(com.vjstb.ledscheme.model.EdgeRouteMode.AUTO, fiber1.effectiveRouteMode());

        // всё одним шагом: Ctrl+Z убирает и лист, и узлы, и связи
        model.undo();
        assertEquals(sheetsBefore, model.schemaSheets(SchemaMode.SIGNAL).size());
        assertEquals(0, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());
        assertEquals(0, model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).size());
    }

    private SchemaNode byId(String id) {
        return model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream().filter(n -> n.getId().equals(id))
                .findFirst().orElseThrow();
    }

    private static String connectorOf(SchemaNode n, String portId) {
        for (var c : n.getCards()) {
            for (var p : c.getPorts()) {
                if (p.getId().equals(portId)) {
                    return p.getConnectorType();
                }
            }
        }
        for (var p : n.getPowerConnectors()) {
            if (p.getId().equals(portId)) {
                return p.getConnectorType();
            }
        }
        return null;
    }

    @Test
    void powerSchemeKeepsRedLinesTheAuthorsLabelAndReportsApproximateMatches(@TempDir Path dir) throws Exception {
        newModel(dir);
        ImportedGraph graph = load("power-zimmer.graphml");
        List<NodePlan> plans = SchemaGraphImport.propose(graph, SchemaMode.POWER, model.getEquipmentPresets());

        assertEquals("UPS FOH", planOf(plans, "FOH UPS").preset().getName());
        NodePlan near = planOf(plans, "AlpenBox 125A->4xSocapex");
        assertEquals("AlpenBox 125A->8xSocapex", near.preset().getName());
        assertEquals(Action.STUB, planOf(plans, "Socapex->6x16A").action(), "общее слово не подставляется само");

        SchemaGraphImport.Result r = SchemaGraphImport.apply(model, SchemaMode.POWER, "Импорт питание", graph, plans);
        var nodes = model.schemaNodesForCurrentScene(SchemaMode.POWER);
        assertTrue(nodes.stream().anyMatch(n -> "FOH UPS".equals(n.getLabel())), "равнозначное совпадение — имя автора");
        assertTrue(nodes.stream().anyMatch(n -> "AlpenBox 125A->8xSocapex".equals(n.getLabel())),
                "приблизительное — имя пресета, чтобы подпись не врала про выходы");
        assertTrue(r.notes().stream().anyMatch(s -> s.contains("приблизительное")));
        var edges = model.schemaEdgesForCurrentScene(SchemaMode.POWER);
        assertTrue(edges.stream().anyMatch(e -> e.getColor() != null && e.getColor() == 0xFF0000));
        // картинки (экраны) пропущены — связи к ним не создаются, это видно в отчёте
        long toImages = graph.edges().stream().filter(e -> graph.nodeById(e.sourceId()).kind() == ImportedGraph.Kind.IMAGE
                || graph.nodeById(e.targetId()).kind() == ImportedGraph.Kind.IMAGE).count();
        assertEquals(toImages, r.edgesDropped());
        assertEquals(graph.edges().size() - toImages, r.edges());
        // щит 4xSocapex (по составу 8xSocapex) → «Socapex->6x16A» (заглушка без гнёзд): связь между блоками
        assertTrue(edges.stream().anyMatch(e -> e.getFromPortId() == null && e.getToPortId() == null));
    }

    @Test
    void existingSheetNameGetsANumberAndTheSheetIsLeftOpen(@TempDir Path dir) throws Exception {
        newModel(dir);
        ImportedGraph graph = load("signal-zimmer.graphml");
        List<NodePlan> plans = SchemaGraphImport.propose(graph, SchemaMode.SIGNAL, model.getEquipmentPresets());
        SchemaGraphImport.apply(model, SchemaMode.SIGNAL, "Zimmer", graph, plans);
        SchemaGraphImport.Result again = SchemaGraphImport.apply(model, SchemaMode.SIGNAL, "Zimmer", graph, plans);
        assertEquals("Zimmer 2", again.sheetName());
        assertEquals("Zimmer 2", model.currentSchemaSheet(SchemaMode.SIGNAL).getName());
    }

    @Test
    void importedBlocksNeverOverlapAndPictureBlocksBecomeLabelledStubs(@TempDir Path dir) throws Exception {
        newModel(dir);
        ImportedGraph graph = load("kish-signal.graphml");
        List<NodePlan> plans = SchemaGraphImport.propose(graph, SchemaMode.SIGNAL, model.getEquipmentPresets());
        long pictures = plans.stream().filter(p -> p.node().kind() == ImportedGraph.Kind.IMAGE).count();
        plans.stream().filter(p -> p.node().kind() == ImportedGraph.Kind.IMAGE).forEach(NodePlan::useStub);

        SchemaGraphImport.Result r = SchemaGraphImport.apply(model, SchemaMode.SIGNAL, "KISH", graph, plans);

        assertEquals(10, pictures);
        assertTrue(r.stubs() >= pictures, "картинки — пустые блоки (плюс оборудование, которого нет в этой тестовой библиотеке)");
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Картинки экранов перенесены пустыми блоками")));
        assertEquals(0, r.edgesDropped());
        var nodes = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL);
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                SchemaNode a = nodes.get(i);
                SchemaNode b = nodes.get(j);
                boolean overlap = a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                        && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
                assertTrue(!overlap, a.getLabel() + " / " + b.getLabel());
            }
        }
        assertTrue(nodes.stream().anyMatch(n -> "Center".equals(n.getLabel())));
    }

    @Test
    void stubTypesAreGuessedFromTheLabel(@TempDir Path dir) throws Exception {
        newModel(dir);
        ImportedGraph graph = load("power-zimmer.graphml");
        List<NodePlan> plans = SchemaGraphImport.propose(graph, SchemaMode.POWER, model.getEquipmentPresets());
        SchemaGraphImport.apply(model, SchemaMode.POWER, "P", graph, plans);
        var nodes = model.schemaNodesForCurrentScene(SchemaMode.POWER);
        assertEquals(SchemaNodeType.DISTRO, nodes.stream().filter(n -> "Socapex->6x16A".equals(n.getLabel()))
                .findFirst().orElseThrow().getType());
    }
}
