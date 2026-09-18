package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;

/**
 * Сторона рамки блока для группы разъёмов с уже известными ролью/направлением/
 * транзитом (docs/schema-ports-rework/PLAN.md, задача T1.4/§2.2) — геометрия без
 * Swing и без модели узла целиком: роль берётся из {@link PortRoleResolver}, транзит —
 * из {@link ThruResolver}, эта функция только раскладывает их по сторонам.
 *
 * <p><b>Базовая таблица</b> (для {@link NodeOrientation#RIGHT} — поток слева направо):
 * <pre>
 * Роль                          | IN    | OUT/IN_OUT | OUT с транзитом
 * VIDEO, LED_DATA, AUDIO, OTHER | LEFT  | RIGHT      | BOTTOM
 * SYNC                          | TOP   | BOTTOM     | BOTTOM
 * NETWORK, CONTROL              | BOTTOM| BOTTOM     | BOTTOM
 * POWER                         | LEFT  | RIGHT      | BOTTOM
 * </pre>
 * Транзитный выход перебивает роль ЛЮБОЙ группы — сквозной проход силового щита/петля
 * генлока рисуется снизу независимо от того, что именно через него проходит.
 *
 * <p><b>Поворот.</b> Для остальных {@link NodeOrientation} таблица выше поворачивается
 * ЦЕЛИКОМ по часовой стрелке: {@link NodeOrientation#DOWN} — на 90°, {@link
 * NodeOrientation#LEFT} — на 180°, {@link NodeOrientation#UP} — на 270°
 * (=90° против часовой) — см. порядок констант {@link NodeOrientation} (ordinal —
 * число поворотов на 90°). {@link PortPlacement#getSide()} (ручной перенос группы,
 * например синхро сбоку — реплика пользователя 2026-09-16) НЕ поворачивается — раз
 * пользователь поставил гнездо туда сам, оно остаётся там при любой ориентации, пока
 * он явно не сбросит раскладку («Вернуть раскладку по умолчанию», PLAN.md, T3.3).
 */
public final class SideRules {

    private SideRules() {
    }

    public static NodeSide sideFor(InterfaceRole role, PortDirection direction, boolean thru,
                                    NodeOrientation orientation, PortPlacement placement) {
        if (placement != null && placement.getSide() != null) {
            return placement.getSide();
        }
        return rotateClockwise(baselineSide(role, direction, thru), orientation);
    }

    private static NodeSide baselineSide(InterfaceRole role, PortDirection direction, boolean thru) {
        if (thru) {
            return NodeSide.BOTTOM;
        }
        boolean in = direction == PortDirection.IN;
        return switch (role) {
            case SYNC -> in ? NodeSide.TOP : NodeSide.BOTTOM;
            case NETWORK, CONTROL -> NodeSide.BOTTOM;
            case POWER, VIDEO, LED_DATA, AUDIO, OTHER -> in ? NodeSide.LEFT : NodeSide.RIGHT;
        };
    }

    /** {@code orientation == null} — как {@link NodeOrientation#RIGHT} (см. {@link
     *  com.vjstb.ledscheme.model.SchemaNode#getOrientation()}: {@code null} значит
     *  «умолчание профиля», геометрию по умолчанию задаёт вызывающий {@code
     *  NodePortLayout}, сюда попадает уже разрешённое значение — но на случай прямого
     *  вызова с {@code null} не падаем, а ведём себя как RIGHT). */
    private static NodeSide rotateClockwise(NodeSide baseline, NodeOrientation orientation) {
        int steps = orientation == null ? 0 : orientation.ordinal();
        NodeSide s = baseline;
        for (int i = 0; i < steps; i++) {
            s = switch (s) {
                case TOP -> NodeSide.RIGHT;
                case RIGHT -> NodeSide.BOTTOM;
                case BOTTOM -> NodeSide.LEFT;
                case LEFT -> NodeSide.TOP;
            };
        }
        return s;
    }
}
