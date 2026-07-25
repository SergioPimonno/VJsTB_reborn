package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
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
    private double width = 175;
    private double height = 56;
    /** Для type == SCREEN: id реального экрана сцены, с которым связан узел. */
    private String screenRefId;
    /** Комплектация карт ввода/вывода (медиасерверы/видеопроцессоры вроде Barco E2,
     *  PixelHue Q8): у type == SERVER/CONTROLLER задаёт реальное число видео I/O. */
    private List<SchemaCard> cards = new ArrayList<>();
    /** Разъёмы питания узла (щиты/дистрибьюторы и т.п. в схеме ПИТАНИЯ): в отличие
     *  от {@link #cards} тут нет группировки по картам — просто список «тип разъёма
     *  (PowerCon/CEE/Schuko…) + направление + количество», т.к. силовое оборудование
     *  не имеет сменных видеокарт. */
    private List<CardPort> powerConnectors = new ArrayList<>();
    /** Запас (%, 100 = без запаса) для проверки суммарной нагрузки этого силового
     *  узла (Task #86/#87) — переопределяет {@link com.vjstb.ledscheme.service.PowerCalc#DEFAULT_DERATING_PERCENT}
     *  для узлов с нетиповым режимом эксплуатации (например, проходной блок-разветвитель
     *  без собственного запаса, в отличие от вводного щита). null — берётся значение
     *  по умолчанию. */
    private Double loadDeratingPercent;

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

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getScreenRefId() {
        return screenRefId;
    }

    public void setScreenRefId(String screenRefId) {
        this.screenRefId = screenRefId;
    }

    public List<SchemaCard> getCards() {
        return cards;
    }

    public void setCards(List<SchemaCard> cards) {
        this.cards = cards;
    }

    public List<CardPort> getPowerConnectors() {
        return powerConnectors;
    }

    public void setPowerConnectors(List<CardPort> powerConnectors) {
        this.powerConnectors = powerConnectors;
    }

    public Double getLoadDeratingPercent() {
        return loadDeratingPercent;
    }

    public void setLoadDeratingPercent(Double loadDeratingPercent) {
        this.loadDeratingPercent = loadDeratingPercent;
    }

    public SchemaNode copy() {
        SchemaNode n = new SchemaNode();
        n.id = id;
        n.mode = mode;
        n.type = type;
        n.label = label;
        n.x = x;
        n.y = y;
        n.width = width;
        n.height = height;
        n.screenRefId = screenRefId;
        n.cards = new ArrayList<>();
        for (SchemaCard c : cards) {
            n.cards.add(c.copy());
        }
        n.powerConnectors = new ArrayList<>();
        for (CardPort p : powerConnectors) {
            n.powerConnectors.add(p.copy());
        }
        n.loadDeratingPercent = loadDeratingPercent;
        return n;
    }
}
