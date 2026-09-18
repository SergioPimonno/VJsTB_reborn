package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T3.3 — перетаскивание группы гнёзд:
 *  "расчёт стороны и позиции вставки по точке отпускания — чистая функция в
 *  schemalayout, с тестом". */
class GroupDropTargetTest {

    @Test
    void sideForPicksNearestOfFourSidesByAngleFromCenter() {
        // Узел 100×40 в (0,0) — центр (50,20).
        assertEquals(NodeSide.LEFT, GroupDropTarget.sideFor(0, 0, 100, 40, 2, 20));
        assertEquals(NodeSide.RIGHT, GroupDropTarget.sideFor(0, 0, 100, 40, 98, 20));
        assertEquals(NodeSide.TOP, GroupDropTarget.sideFor(0, 0, 100, 40, 50, 2));
        assertEquals(NodeSide.BOTTOM, GroupDropTarget.sideFor(0, 0, 100, 40, 50, 38));
    }

    @Test
    void sideForWorksSlightlyOutsideTheNodeToo() {
        // Курсор за левым краем узла на полпути по высоте — всё равно LEFT.
        assertEquals(NodeSide.LEFT, GroupDropTarget.sideFor(0, 0, 100, 40, -20, 20));
        assertEquals(NodeSide.BOTTOM, GroupDropTarget.sideFor(0, 0, 100, 40, 50, 60));
    }

    @Test
    void sideForRespectsNonSquareAspectRatio() {
        // Узел высокий и узкий (40×200) — угол у координаты (30,60) ближе к TOP,
        // если бы узел был квадратным, но при таком аспекте это LEFT (диагональ
        // прямоугольника, не квадрата, определяет секторы).
        assertEquals(NodeSide.LEFT, GroupDropTarget.sideFor(0, 0, 40, 200, 5, 60));
    }

    private static CardPort port(String type) {
        return new CardPort(type, PortDirection.OUT, 1);
    }

    private static NodePortLayout.Pin pinAt(CardPort port, NodeSide side, double along) {
        double x = side == NodeSide.LEFT ? 0 : side == NodeSide.RIGHT ? 100 : along;
        double y = side == NodeSide.TOP ? 0 : side == NodeSide.BOTTOM ? 100 : along;
        return new NodePortLayout.Pin(port, "c1", 0, 1, PortDirection.OUT, InterfaceRole.OTHER, false, side, x, y);
    }

    @Test
    void orderForReturnsNullWhenNoOtherGroupOnThatSide() {
        assertNull(GroupDropTarget.orderFor(NodeSide.LEFT, 0, 0, List.of(), 0, 20));
    }

    @Test
    void orderForInsertsBeforeAllWhenDroppedAboveFirstGroup() {
        CardPort a = port("HDMI");
        CardPort b = port("SDI");
        List<NodePortLayout.Pin> others = List.of(pinAt(a, NodeSide.LEFT, 20), pinAt(b, NodeSide.LEFT, 40));
        Double order = GroupDropTarget.orderFor(NodeSide.LEFT, 0, 0, others, 0, 5);
        assertTrue(order < 0, "перед первой группой (along=20) — отрицательный ключ");
    }

    @Test
    void orderForInsertsAfterAllWhenDroppedBelowLastGroup() {
        CardPort a = port("HDMI");
        CardPort b = port("SDI");
        List<NodePortLayout.Pin> others = List.of(pinAt(a, NodeSide.LEFT, 20), pinAt(b, NodeSide.LEFT, 40));
        Double order = GroupDropTarget.orderFor(NodeSide.LEFT, 0, 0, others, 0, 100);
        assertTrue(order > 1, "после второй группы (natural index 1) — ключ больше 1");
    }

    @Test
    void orderForInsertsBetweenTwoNeighboringGroups() {
        CardPort a = port("HDMI");
        CardPort b = port("SDI");
        CardPort c = port("DisplayPort");
        List<NodePortLayout.Pin> others = List.of(
                pinAt(a, NodeSide.LEFT, 10), pinAt(b, NodeSide.LEFT, 30), pinAt(c, NodeSide.LEFT, 50));
        Double order = GroupDropTarget.orderFor(NodeSide.LEFT, 0, 0, others, 0, 31);
        assertEquals(1.5, order, "между 2-й (natural index 1) и 3-й (index 2) группой");
    }

    @Test
    void orderForUsesHorizontalProjectionForTopAndBottom() {
        CardPort a = port("Ethernet");
        List<NodePortLayout.Pin> others = List.of(pinAt(a, NodeSide.TOP, 50));
        // Точка левее единственной группы по X (along для TOP/BOTTOM — это X).
        Double order = GroupDropTarget.orderFor(NodeSide.TOP, 0, 0, others, 10, 999);
        assertTrue(order < 0);
    }
}
