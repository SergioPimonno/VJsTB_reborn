package com.vjstb.ledscheme.model;

/** Режим прокладки маршрута связи общей схемы (docs/schema-ports-rework/PLAN.md,
 *  задача T1.2/этап 4). {@link #AUTO} — ломаная под 90° считается заново при каждой
 *  отрисовке ортогональным трассировщиком ({@code OrthogonalRouter}), точки излома НЕ
 *  сохраняются в {@link SchemaEdge#getWaypoints()}; {@link #MANUAL} — маршрут идёт через
 *  сохранённые {@link SchemaEdge#getWaypoints()}, как раньше (Task #85/v1.4); {@link
 *  #STRAIGHT} — прямая линия узел-узел без точек излома, тоже как раньше. {@code null}
 *  у {@link SchemaEdge#getRouteMode()} — старая связь без явного режима: см. {@link
 *  SchemaEdge#effectiveRouteMode()} для legacy-резолва (непустые waypoints → {@link
 *  #MANUAL}, иначе {@link #STRAIGHT}) — так открытие старого проекта НИЧЕГО не меняет
 *  в уже нарисованных связях (Золотое решение D6 в PLAN.md), новыми связями управляет
 *  настройка персонализации «Режим прокладки новых связей». */
public enum EdgeRouteMode {
    AUTO, MANUAL, STRAIGHT
}
