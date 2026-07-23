package com.vjstb.ledscheme.model;

import java.util.UUID;

/** Связь между двумя узлами общей схемы площадки (линия питания/сигнала). */
public class SchemaEdge {

    private String id = UUID.randomUUID().toString();
    private SchemaMode mode = SchemaMode.POWER;
    private String fromNodeId;
    private String toNodeId;
    private String label;

    public SchemaEdge() {
    }

    public SchemaEdge(SchemaMode mode, String fromNodeId, String toNodeId, String label) {
        this.mode = mode;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SchemaMode getMode() {
        return mode;
    }

    public void setMode(SchemaMode mode) {
        this.mode = mode;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public SchemaEdge copy() {
        SchemaEdge e = new SchemaEdge();
        e.id = id;
        e.mode = mode;
        e.fromNodeId = fromNodeId;
        e.toNodeId = toNodeId;
        e.label = label;
        return e;
    }
}
