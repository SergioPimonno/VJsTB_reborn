package com.vjstb.ledscheme.model;

/**
 * Раскладка ОДНОЙ группы разъёмов (см. {@link CardPort#getId()}) на рамке конкретного
 * узла общей схемы (docs/schema-ports-rework/PLAN.md, задача T1.2) — переопределяет
 * то, что иначе вычислялось бы автоматически из роли интерфейса и {@link
 * SchemaNode#getOrientation()} (см. клиентский {@code SideRules}) или из библиотеки
 * (см. {@code PortRoleResolver}). Хранится ТОЛЬКО в клиенте, в списке {@link
 * SchemaNode#getPortPlacements()} — общая библиотека ({@code ledscheme-model}) про
 * конкретные проекты ничего не знает.
 *
 * <p>Каждое поле независимо и по умолчанию {@code null} — «не переопределено, взять
 * из правил/библиотеки»: у только что добавленного узла список раскладок пуст, а
 * поведение в точности совпадает с автоматическим (важно для обратной совместимости —
 * открытие старого проекта без единой записи {@code PortPlacement} не меняет вид схемы).
 * Команда «Вернуть раскладку по умолчанию» (см. PLAN.md, задача T3.3) удаляет запись
 * целиком, а не обнуляет поля по одному.
 */
public class PortPlacement {

    /** id группы разъёмов ({@link CardPort#getId()}), к которой относится эта запись —
     *  НЕ id самого узла (у узла таких записей много, по одной на переопределённую
     *  группу) и не id отдельного гнезда внутри развёрнутой группы (развёрнутые гнёзда
     *  одной группы всегда лежат вместе, подряд, раскладка у них общая). */
    private String portId;
    /** Сторона рамки, куда явно перенесена группа (например, «синхро сбоку» — реплика
     *  пользователя 2026-09-16) — {@code null} значит «по таблице ролей» (PLAN.md §2.2).
     *  В отличие от стороны, вычисленной по роли, эта НЕ поворачивается при смене
     *  {@link SchemaNode#getOrientation()} — пользователь поставил группу туда руками. */
    private NodeSide side;
    /** Порядок группы среди других групп на той же стороне — меньшее значение раньше;
     *  {@code null} значит «естественный порядок» (как группы идут в картах узла).
     *  Не обязано быть целым или последовательным — только для сравнения между собой. */
    private Double order;
    /** {@code true} — группа принудительно свёрнута (одно гнездо "N×Тип"), {@code false} —
     *  принудительно развёрнута (N отдельных гнёзд с номерами), {@code null} — авто:
     *  свёрнута, если на группе нет ни одной связи (см. PLAN.md §2.4). */
    private Boolean collapsed;
    /** Переопределение роли ИМЕННО В ЭТОМ ПРОЕКТЕ — старшинство выше библиотечной
     *  ({@link CardPort#getRole()}/{@link InterfaceType#getDefaultRole()}), см. порядок
     *  разрешения в PLAN.md §2.3. {@code null} — роль берётся из библиотеки/эвристики. */
    private InterfaceRole roleOverride;
    /** Переопределение транзита ИМЕННО В ЭТОМ ПРОЕКТЕ — старшинство выше {@link
     *  CardPort#getThru()} и авто-угадывания (PLAN.md §2.3). {@code null} — берётся из
     *  библиотеки/угадывания. */
    private Boolean thruOverride;

    public PortPlacement() {
    }

    public PortPlacement(String portId) {
        this.portId = portId;
    }

    public String getPortId() {
        return portId;
    }

    public void setPortId(String portId) {
        this.portId = portId;
    }

    public NodeSide getSide() {
        return side;
    }

    public void setSide(NodeSide side) {
        this.side = side;
    }

    public Double getOrder() {
        return order;
    }

    public void setOrder(Double order) {
        this.order = order;
    }

    public Boolean getCollapsed() {
        return collapsed;
    }

    public void setCollapsed(Boolean collapsed) {
        this.collapsed = collapsed;
    }

    public InterfaceRole getRoleOverride() {
        return roleOverride;
    }

    public void setRoleOverride(InterfaceRole roleOverride) {
        this.roleOverride = roleOverride;
    }

    public Boolean getThruOverride() {
        return thruOverride;
    }

    public void setThruOverride(Boolean thruOverride) {
        this.thruOverride = thruOverride;
    }

    /** true, если у записи не осталось ни одного переопределения — такую запись можно
     *  удалить из {@link SchemaNode#getPortPlacements()} вместо хранения впустую (см.
     *  {@code AppModel.setPortPlacement} и другие точечные мутаторы этапа 3). */
    public boolean isEmpty() {
        return side == null && order == null && collapsed == null && roleOverride == null && thruOverride == null;
    }

    public PortPlacement copy() {
        PortPlacement p = new PortPlacement();
        p.portId = portId;
        p.side = side;
        p.order = order;
        p.collapsed = collapsed;
        p.roleOverride = roleOverride;
        p.thruOverride = thruOverride;
        return p;
    }
}
