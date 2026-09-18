package com.vjstb.ledscheme.model;

/** Сторона рамки узла общей схемы, на которой рисуется гнездо (docs/schema-ports-
 *  rework/PLAN.md, задача T1.2) — по умолчанию вычисляется из роли интерфейса гнезда
 *  и {@link NodeOrientation} узла (см. клиентский {@code SideRules}), но может быть
 *  переопределена вручную для конкретной группы через {@link PortPlacement#getSide()}
 *  (например, пользователь предпочитает ставить синхро-гнездо сбоку, а не сверху). */
public enum NodeSide {
    TOP, RIGHT, BOTTOM, LEFT
}
