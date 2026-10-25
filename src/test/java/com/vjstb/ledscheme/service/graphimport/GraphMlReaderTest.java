package com.vjstb.ledscheme.service.graphimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphEdge;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphNode;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.Kind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Чтение yEd {@code .graphml} на реальных схемах пользователя (Zimmer, ЦСКА; вшитые картинки вырезаны):
 * сигнал — 16 блоков (5 картинок экранов, группа «FOH» с вложенными блоками) и 16 связей, питание — 20 блоков и
 * 23 связи, красные линии питания.
 */
class GraphMlReaderTest {

    private static ImportedGraph load(String resource) throws IOException {
        try (InputStream in = GraphMlReaderTest.class.getResourceAsStream("/graphml/" + resource)) {
            assertNotNull(in, resource);
            return GraphMlReader.read(in, resource);
        }
    }

    @Test
    void signalSchemeBlocksGroupsAndEdges() throws Exception {
        ImportedGraph g = load("signal-zimmer.graphml");
        assertEquals(16, g.nodes().size());
        assertEquals(16, g.edges().size());
        assertEquals(5, g.nodes().stream().filter(n -> n.kind() == Kind.IMAGE).count());
        assertEquals(1, g.nodes().stream().filter(n -> n.kind() == Kind.GROUP).count());

        GraphNode h2 = g.nodeById("n10::n0");
        assertEquals("H2", h2.label());
        assertEquals("n10", h2.parentId());
        assertEquals(Kind.EQUIPMENT, h2.kind());
        GraphNode group = g.nodeById("n10");
        assertEquals(Kind.GROUP, group.kind());
        // координаты вложенного блока абсолютные: он лежит внутри рамки группы без сдвига
        assertTrue(h2.x() >= group.x() && h2.x() + h2.width() <= group.x() + group.width());
        assertTrue(h2.y() >= group.y() && h2.y() + h2.height() <= group.y() + group.height());
        assertEquals(0xFFCC00, h2.fillRgb());
    }

    @Test
    void edgeLabelsJoinLinesAndArrowsAreRead() throws Exception {
        ImportedGraph g = load("signal-zimmer.graphml");
        GraphEdge first = g.edges().get(0);
        assertEquals("1-4 main / 5-8 backup", first.label());
        assertTrue(first.arrowAtTarget());
        assertFalse(first.arrowAtSource());
        assertTrue(g.edges().stream().anyMatch(e -> "fiber 1".equals(e.label())));
        assertTrue(g.edges().stream().anyMatch(e -> "SDI".equals(e.label())));
    }

    @Test
    void powerSchemeHasRedFeedLines() throws Exception {
        ImportedGraph g = load("power-zimmer.graphml");
        assertEquals(20, g.nodes().size());
        assertEquals(23, g.edges().size());
        assertEquals(11, g.edges().stream().filter(e -> e.colorRgb() != null && e.colorRgb() == 0xFF0000).count());
        GraphNode ups = g.nodes().stream().filter(n -> n.label().equals("FOH UPS")).findFirst().orElseThrow();
        assertEquals(Kind.EQUIPMENT, ups.kind());
        // подпись блока в две строки — одной строкой через пробел
        assertTrue(g.nodes().stream().anyMatch(n -> n.label().equals("AlpenBox 125A->4xSocapex")));
    }

    @Test
    void brokenOrHostileXmlIsRejectedWithAReadableError() {
        String bomb = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"aaaa\">]><graphml/>";
        assertThrows(IOException.class,
                () -> GraphMlReader.read(new ByteArrayInputStream(bomb.getBytes(StandardCharsets.UTF_8)), "x"));
        assertThrows(IOException.class,
                () -> GraphMlReader.read(new ByteArrayInputStream("not xml".getBytes(StandardCharsets.UTF_8)), "x"));
    }
}
