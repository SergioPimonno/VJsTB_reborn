package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Одна группа портов одного типа разъёма на карте (например «2×HDMI 2.1»). Карта
 * может содержать несколько таких групп разного типа/направления — комбинированные
 * карты вроде Barco E2 (2×HDMI2.1 + 2×DP1.2 на одной карте) требуют именно этого,
 * а не единой пары input/output-счётчиков на карту.
 */
public class CardPort {

    private String id = UUID.randomUUID().toString();
    private String connectorType = "";
    private PortDirection direction = PortDirection.IN;
    private int count = 1;

    public CardPort() {
    }

    public CardPort(String connectorType, PortDirection direction, int count) {
        this.connectorType = connectorType;
        this.direction = direction;
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public PortDirection getDirection() {
        return direction;
    }

    public void setDirection(PortDirection direction) {
        this.direction = direction;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public CardPort copy() {
        CardPort p = new CardPort();
        p.id = id;
        p.connectorType = connectorType;
        p.direction = direction;
        p.count = count;
        return p;
    }
}
