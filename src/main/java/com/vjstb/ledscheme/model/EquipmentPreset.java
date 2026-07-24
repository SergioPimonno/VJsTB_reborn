package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Пресет оборудования библиотеки (медиасервер/видеопроцессор/конвертер и т.п.) —
 * переиспользуемый шаблон узла общей схемы: название, категория (тип узла) и
 * заранее заданная комплектация карт ввода/вывода. Выбор пресета при добавлении
 * узла в схему копирует название и карты в новый узел вместо повторного ручного
 * ввода одной и той же модели оборудования (например, Barco E2, PixelHue Q8).
 */
public class EquipmentPreset {

    private String id = UUID.randomUUID().toString();
    /** Питание и сигнал — разные библиотеки оборудования: один и тот же тип узла
     *  (например SOURCE) означает разное железо в зависимости от схемы (силовой
     *  щит vs видеоисточник), пресеты между ними не должны путаться. */
    private SchemaMode mode = SchemaMode.POWER;
    private SchemaNodeType category = SchemaNodeType.CUSTOM;
    private String name = "";
    private String description = "";
    private List<SchemaCard> cards = new ArrayList<>();
    /** Разъёмы питания пресета (только для mode == POWER): тип+направление+количество
     *  без группировки по картам, см. {@link SchemaNode#getPowerConnectors()}. */
    private List<CardPort> powerConnectors = new ArrayList<>();

    public EquipmentPreset() {
    }

    public EquipmentPreset(SchemaMode mode, SchemaNodeType category, String name, String description) {
        this.mode = mode;
        this.category = category;
        this.name = name;
        this.description = description;
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

    public SchemaNodeType getCategory() {
        return category;
    }

    public void setCategory(SchemaNodeType category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public EquipmentPreset copy() {
        EquipmentPreset p = new EquipmentPreset();
        p.id = id;
        p.mode = mode;
        p.category = category;
        p.name = name;
        p.description = description;
        p.cards = new ArrayList<>();
        for (SchemaCard c : cards) {
            p.cards.add(c.copy());
        }
        p.powerConnectors = new ArrayList<>();
        for (CardPort cp : powerConnectors) {
            p.powerConnectors.add(cp.copy());
        }
        return p;
    }
}
