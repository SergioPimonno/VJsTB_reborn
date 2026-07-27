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
    /** Комплектация по умолчанию — id карт-шаблонов из {@link #cards} В ПОРЯДКЕ
     *  размещения (шаблон может повторяться, если в устройстве несколько
     *  экземпляров одной карты) — при добавлении узла из этого пресета
     *  диалог сборки карт (см. AssembleCardsDialog) стартует с этого набора
     *  вместо пустого, чтобы не собирать одну и ту же типовую комплектацию
     *  заново на каждый узел. Пусто — старое поведение (пустой старт). */
    private List<String> defaultCardTemplateIds = new ArrayList<>();
    /** Название пользовательской подкатегории (только когда category == CUSTOM) —
     *  см. AppModel.customEquipmentCategories/AdminDialog: категории — фиксированный
     *  Java-enum (используется в логике схемы, менять список произвольно небезопасно),
     *  а эта подкатегория — админ-редактируемый список ИМЁН под общим "Прочее
     *  оборудование", реальный способ добавлять новые категории без правки кода.
     *  Пусто — обычное "Прочее оборудование" без уточнения. */
    private String customCategoryLabel = "";

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

    public List<String> getDefaultCardTemplateIds() {
        return defaultCardTemplateIds;
    }

    public void setDefaultCardTemplateIds(List<String> defaultCardTemplateIds) {
        this.defaultCardTemplateIds = defaultCardTemplateIds;
    }

    public String getCustomCategoryLabel() {
        return customCategoryLabel;
    }

    public void setCustomCategoryLabel(String customCategoryLabel) {
        this.customCategoryLabel = customCategoryLabel == null ? "" : customCategoryLabel;
    }

    /** Подпись категории для отображения — подкатегория (если задана и category ==
     *  CUSTOM), иначе обычная подпись типа узла. Единая точка, которой должны
     *  пользоваться и дерево библиотеки, и подбор пресетов в общей схеме, вместо
     *  прямого category.getLabel() — иначе подкатегория нигде бы не была видна. */
    public String categoryDisplayLabel() {
        if (category == SchemaNodeType.CUSTOM && !customCategoryLabel.isEmpty()) {
            return customCategoryLabel;
        }
        return category.getLabel();
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
        p.defaultCardTemplateIds = new ArrayList<>(defaultCardTemplateIds);
        p.customCategoryLabel = customCategoryLabel;
        return p;
    }
}
