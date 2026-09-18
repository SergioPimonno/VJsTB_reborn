package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T2.3 — {@link SchemaUsage} повторяет
 *  прежнее поведение {@code SchemaCanvasPanel.usedCount}/{@code screenUsedCount}/
 *  {@code edgeOrdinalForPort} без изменений (перенос, не переработка). */
class SchemaUsageTest {

    private static SchemaEdge edge(String fromNode, String fromPort, String toNode, String toPort, Integer wireCount) {
        SchemaEdge e = new SchemaEdge(SchemaMode.SIGNAL, fromNode, toNode, null);
        e.setFromPortId(fromPort);
        e.setToPortId(toPort);
        e.setWireCount(wireCount);
        return e;
    }

    @Test
    void usedCountSumsWireCountDefaultingToOne() {
        SchemaEdge e1 = edge("a", "p1", "b", null, null);
        SchemaEdge e2 = edge("c", null, "d", "p1", 3);
        SchemaEdge unrelated = edge("a", "other", "b", null, 5);
        List<SchemaEdge> edges = List.of(e1, e2, unrelated);

        assertEquals(4, SchemaUsage.usedCount(edges, "p1", null), "1 (без wireCount) + 3");
    }

    @Test
    void usedCountExcludesGivenEdge() {
        SchemaEdge e1 = edge("a", "p1", "b", null, 2);
        SchemaEdge e2 = edge("c", null, "d", "p1", 3);
        List<SchemaEdge> edges = List.of(e1, e2);

        assertEquals(2, SchemaUsage.usedCount(edges, "p1", e2), "e2 исключена — переподключаемая связь себя не давит");
    }

    @Test
    void nodeUsedCountMatchesEitherEnd() {
        SchemaEdge e1 = edge("screen1", "p1", "hub", "p2", 2);
        SchemaEdge e2 = edge("hub", "p3", "screen1", "p4", 1);
        List<SchemaEdge> edges = List.of(e1, e2);

        assertEquals(3, SchemaUsage.nodeUsedCount(edges, "screen1", null));
    }

    @Test
    void edgeOrdinalForPortFollowsListOrder() {
        SchemaEdge e1 = edge("a", "p1", "b", null, null);
        SchemaEdge e2 = edge("c", "p1", "d", null, null);
        SchemaEdge e3 = edge("e", "p1", "f", null, null);
        List<SchemaEdge> edges = List.of(e1, e2, e3);

        assertEquals(0, SchemaUsage.edgeOrdinalForPort(edges, e1, "p1"));
        assertEquals(1, SchemaUsage.edgeOrdinalForPort(edges, e2, "p1"));
        assertEquals(2, SchemaUsage.edgeOrdinalForPort(edges, e3, "p1"));
    }

    @Test
    void edgeOrdinalForPortReturnsCountForNotYetCreatedEdge() {
        SchemaEdge e1 = edge("a", "p1", "b", null, null);
        List<SchemaEdge> edges = List.of(e1);
        SchemaEdge preview = edge("x", "p1", "y", null, null);

        assertEquals(1, SchemaUsage.edgeOrdinalForPort(edges, preview, "p1"),
                "связь ещё не в списке — встанет следующей по счёту");
    }

    @Test
    void isPortUsedReflectsPresenceOfAnyMatchingEdge() {
        SchemaEdge e1 = edge("a", "p1", "b", null, null);
        assertTrue(SchemaUsage.isPortUsed(List.of(e1), "p1"));
        assertFalse(SchemaUsage.isPortUsed(List.of(e1), "p2"));
        assertFalse(SchemaUsage.isPortUsed(List.of(), "p1"));
    }
}
