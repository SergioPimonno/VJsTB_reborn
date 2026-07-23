package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Узел общей схемы площадки (питания или сигнала): оборудование (источник,
 * распределение, конвертер, медиасервер, контроллер) либо ссылка на реальный
 * экран сцены — тогда имя/статистика в узле берутся из самого экрана (привязка
 * схемы к цепочкам, а не просто рисунок).
 */
public class SchemaNode {

    private String id = UUID.randomUUID().toString();
    private SchemaMode mode = SchemaMode.POWER;
    private SchemaNodeType type = SchemaNodeType.CUSTOM;
    private String label = "";
    private double x;
    private double y;
    /** Для type == SCREEN: id реального экрана сцены, с которым связан узел. */
    private String screenRefId;

    public SchemaNode() {
    }

    public SchemaNode(SchemaMode mode, SchemaNodeType type, String label, double x, double y, String screenRefId) {
        this.mode = mode;
        this.type = type;
        this.label = label;
        this.x = x;
        this.y = y;
        this.screenRefId = screenRefId;
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

    public SchemaNodeType getType() {
        return type;
    }

    public void setType(SchemaNodeType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getScreenRefId() {
        return screenRefId;
    }

    public void setScreenRefId(String screenRefId) {
        this.screenRefId = screenRefId;
    }

    public SchemaNode copy() {
        SchemaNode n = new SchemaNode();
        n.id = id;
        n.mode = mode;
        n.type = type;
        n.label = label;
        n.x = x;
        n.y = y;
        n.screenRefId = screenRefId;
        return n;
    }
}
