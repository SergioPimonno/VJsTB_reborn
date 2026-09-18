package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NodeSide;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T4.3 (D8) — геометрия ствола пучка и
 *  подпись количества, чистая функция без Swing. */
class EdgeBundlesTest {

    @Test
    void trunkRunsStrictlyAlongTheOutwardNormalOfTheSide() {
        EdgeBundles.Bundle right = EdgeBundles.bundleFor(100, 50, NodeSide.RIGHT, 3);
        assertEquals(100, right.pin()[0], 1e-9);
        assertEquals(50, right.pin()[1], 1e-9);
        assertEquals(100 + EdgeBundles.TRUNK_LENGTH, right.mergePoint()[0], 1e-9);
        assertEquals(50, right.mergePoint()[1], 1e-9, "RIGHT — ствол строго горизонтален, Y не меняется");

        EdgeBundles.Bundle top = EdgeBundles.bundleFor(100, 50, NodeSide.TOP, 3);
        assertEquals(100, top.mergePoint()[0], 1e-9, "TOP — ствол строго вертикален, X не меняется");
        assertEquals(50 - EdgeBundles.TRUNK_LENGTH, top.mergePoint()[1], 1e-9);
    }

    @Test
    void trunkLengthIsFixedRegardlessOfMemberCount() {
        EdgeBundles.Bundle two = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 2);
        EdgeBundles.Bundle ten = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 10);
        double lenTwo = Math.hypot(two.mergePoint()[0] - two.pin()[0], two.mergePoint()[1] - two.pin()[1]);
        double lenTen = Math.hypot(ten.mergePoint()[0] - ten.pin()[0], ten.mergePoint()[1] - ten.pin()[1]);
        assertEquals(lenTwo, lenTen, 1e-9);
        assertEquals(EdgeBundles.TRUNK_LENGTH, lenTwo, 1e-9);
    }

    @Test
    void trunkSegmentGoesFromMergePointToPin() {
        EdgeBundles.Bundle b = EdgeBundles.bundleFor(10, 20, NodeSide.BOTTOM, 4);
        double[][] trunk = b.trunk();
        assertEquals(b.mergePoint()[0], trunk[0][0], 1e-9);
        assertEquals(b.mergePoint()[1], trunk[0][1], 1e-9);
        assertEquals(b.pin()[0], trunk[1][0], 1e-9);
        assertEquals(b.pin()[1], trunk[1][1], 1e-9);
    }

    @Test
    void labelShowsTheMemberCount() {
        assertEquals("×2", EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 2).label());
        assertEquals("×7", EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 7).label());
    }

    @Test
    void bundleOfFewerThanTwoMembersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 1));
        assertThrows(IllegalArgumentException.class, () -> EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 0));
    }

    @Test
    void allFourSidesProduceDistinctOutwardDirections() {
        double[] r = EdgeBundles.bundleFor(0, 0, NodeSide.RIGHT, 2).mergePoint();
        double[] l = EdgeBundles.bundleFor(0, 0, NodeSide.LEFT, 2).mergePoint();
        double[] t = EdgeBundles.bundleFor(0, 0, NodeSide.TOP, 2).mergePoint();
        double[] btm = EdgeBundles.bundleFor(0, 0, NodeSide.BOTTOM, 2).mergePoint();
        assertTrue(r[0] > 0 && Math.abs(r[1]) < 1e-9);
        assertTrue(l[0] < 0 && Math.abs(l[1]) < 1e-9);
        assertTrue(t[1] < 0 && Math.abs(t[0]) < 1e-9);
        assertTrue(btm[1] > 0 && Math.abs(btm[0]) < 1e-9);
    }
}
