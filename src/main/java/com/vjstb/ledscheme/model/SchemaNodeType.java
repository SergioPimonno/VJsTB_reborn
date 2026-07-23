package com.vjstb.ledscheme.model;

/** Тип узла общей схемы площадки (аналог фигур в yEd). */
public enum SchemaNodeType {
    SOURCE("Источник/щит"),
    DISTRO("Распределение"),
    CONVERTER("Конвертер"),
    SERVER("Медиасервер"),
    CONTROLLER("Контроллер"),
    SCREEN("Экран"),
    CUSTOM("Прочее оборудование");

    private final String label;

    SchemaNodeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
