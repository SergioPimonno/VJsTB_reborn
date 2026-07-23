package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Scene {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private int orderIndex;
    private List<Screen> screens = new ArrayList<>();
    /** Узлы/связи общей схемы площадки (питание и сигнал вместе, различаются полем mode). */
    private List<SchemaNode> schemaNodes = new ArrayList<>();
    private List<SchemaEdge> schemaEdges = new ArrayList<>();

    public Scene() {
    }

    public Scene(String name) {
        this.name = name;
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<Screen> getScreens() {
        return screens;
    }

    public void setScreens(List<Screen> screens) {
        this.screens = screens;
    }

    public List<SchemaNode> getSchemaNodes() {
        return schemaNodes;
    }

    public void setSchemaNodes(List<SchemaNode> schemaNodes) {
        this.schemaNodes = schemaNodes;
    }

    public List<SchemaEdge> getSchemaEdges() {
        return schemaEdges;
    }

    public void setSchemaEdges(List<SchemaEdge> schemaEdges) {
        this.schemaEdges = schemaEdges;
    }
}
