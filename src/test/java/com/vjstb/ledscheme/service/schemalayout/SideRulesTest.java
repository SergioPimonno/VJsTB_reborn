package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T1.4, §2.2 — таблица сторон по ролям для
 *  {@link NodeOrientation#RIGHT} и повороты для остальных трёх ориентаций, плюс
 *  правило «ручная сторона не поворачивается» (реплика пользователя 2026-09-16:
 *  «синхро сделай перетаскиваемым, часто этот порт удобно ставить сбоку»). */
class SideRulesTest {

    private static NodeSide side(InterfaceRole role, PortDirection dir, boolean thru, NodeOrientation o) {
        return SideRules.sideFor(role, dir, thru, o, null);
    }

    @Test
    void baselineTableForRightOrientation() {
        for (InterfaceRole role : new InterfaceRole[]{InterfaceRole.VIDEO, InterfaceRole.LED_DATA,
                InterfaceRole.AUDIO, InterfaceRole.OTHER, InterfaceRole.POWER}) {
            assertEquals(NodeSide.LEFT, side(role, PortDirection.IN, false, NodeOrientation.RIGHT), role.name());
            assertEquals(NodeSide.RIGHT, side(role, PortDirection.OUT, false, NodeOrientation.RIGHT), role.name());
            assertEquals(NodeSide.RIGHT, side(role, PortDirection.IN_OUT, false, NodeOrientation.RIGHT), role.name());
        }
        assertEquals(NodeSide.TOP, side(InterfaceRole.SYNC, PortDirection.IN, false, NodeOrientation.RIGHT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.SYNC, PortDirection.OUT, false, NodeOrientation.RIGHT));

        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.NETWORK, PortDirection.IN, false, NodeOrientation.RIGHT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.NETWORK, PortDirection.OUT, false, NodeOrientation.RIGHT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.CONTROL, PortDirection.IN, false, NodeOrientation.RIGHT));
    }

    @Test
    void thruOutputAlwaysGoesToBottomRegardlessOfRole() {
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.VIDEO, PortDirection.OUT, true, NodeOrientation.RIGHT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.POWER, PortDirection.OUT, true, NodeOrientation.RIGHT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.SYNC, PortDirection.OUT, true, NodeOrientation.RIGHT));
    }

    @Test
    void orientationRotatesWholeTableClockwise() {
        // VIDEO IN — на RIGHT это LEFT; после поворотов по часовой должно пройти
        // LEFT -> TOP (DOWN) -> RIGHT (LEFT-ориентация) -> BOTTOM (UP).
        assertEquals(NodeSide.LEFT, side(InterfaceRole.VIDEO, PortDirection.IN, false, NodeOrientation.RIGHT));
        assertEquals(NodeSide.TOP, side(InterfaceRole.VIDEO, PortDirection.IN, false, NodeOrientation.DOWN));
        assertEquals(NodeSide.RIGHT, side(InterfaceRole.VIDEO, PortDirection.IN, false, NodeOrientation.LEFT));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.VIDEO, PortDirection.IN, false, NodeOrientation.UP));

        // Синхро-вход — TOP на RIGHT, тот же цикл со сдвигом на одну позицию.
        assertEquals(NodeSide.TOP, side(InterfaceRole.SYNC, PortDirection.IN, false, NodeOrientation.RIGHT));
        assertEquals(NodeSide.RIGHT, side(InterfaceRole.SYNC, PortDirection.IN, false, NodeOrientation.DOWN));
        assertEquals(NodeSide.BOTTOM, side(InterfaceRole.SYNC, PortDirection.IN, false, NodeOrientation.LEFT));
        assertEquals(NodeSide.LEFT, side(InterfaceRole.SYNC, PortDirection.IN, false, NodeOrientation.UP));
    }

    @Test
    void manualPlacementSideIsNeverRotated() {
        PortPlacement placement = new PortPlacement("port-1");
        placement.setSide(NodeSide.RIGHT); // синхро поставлено сбоку вручную
        for (NodeOrientation o : NodeOrientation.values()) {
            assertEquals(NodeSide.RIGHT,
                    SideRules.sideFor(InterfaceRole.SYNC, PortDirection.IN, false, o, placement),
                    "ручная сторона не должна меняться при повороте блока: " + o);
        }
    }

    @Test
    void nullOrientationBehavesLikeRight() {
        assertEquals(side(InterfaceRole.VIDEO, PortDirection.IN, false, NodeOrientation.RIGHT),
                side(InterfaceRole.VIDEO, PortDirection.IN, false, null));
    }
}
