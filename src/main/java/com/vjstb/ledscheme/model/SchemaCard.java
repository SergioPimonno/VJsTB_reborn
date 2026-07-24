package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Карта ввода/вывода в шасси узла схемы (медиасервер/видеопроцессор наподобие
 * Barco E2, PixelHue Q8 или карты видеовхода Novastar H-серии): у таких устройств
 * комплектация карт определяет реальное число видеовходов/выходов. Одна карта
 * может сочетать несколько групп разъёмов разного типа/направления (например
 * 2×HDMI2.1 + 2×DP1.2 на одной карте Barco E2) — отсюда список {@link CardPort},
 * а не единая пара input/output-счётчиков на карту.
 */
public class SchemaCard {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private List<CardPort> ports = new ArrayList<>();

    public SchemaCard() {
    }

    public SchemaCard(String name, List<CardPort> ports) {
        this.name = name;
        if (ports != null) {
            this.ports = ports;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CardPort> getPorts() {
        return ports;
    }

    public void setPorts(List<CardPort> ports) {
        this.ports = ports;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int totalInputs() {
        int sum = 0;
        for (CardPort p : ports) {
            if (p.getDirection() == PortDirection.IN) {
                sum += p.getCount();
            }
        }
        return sum;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int totalOutputs() {
        int sum = 0;
        for (CardPort p : ports) {
            if (p.getDirection() == PortDirection.OUT) {
                sum += p.getCount();
            }
        }
        return sum;
    }

    /** Краткое описание вида «2×HDMI2.1 IN, 2×DP1.2 IN, 4×SDI OUT». */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String portsSummary() {
        StringBuilder sb = new StringBuilder();
        for (CardPort p : ports) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getCount()).append('×').append(p.getConnectorType())
                    .append(' ').append(p.getDirection() == PortDirection.IN ? "IN" : "OUT");
        }
        return sb.toString();
    }

    public SchemaCard copy() {
        SchemaCard c = new SchemaCard();
        c.id = id;
        c.name = name;
        c.ports = new ArrayList<>();
        for (CardPort p : ports) {
            c.ports.add(p.copy());
        }
        return c;
    }
}
