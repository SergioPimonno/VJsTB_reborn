package com.vjstb.ledscheme.model;

/** Физическая форма кабинета (для спецификации и будущей геометрии экрана). */
public enum CabinetShape {
    RECTANGLE("Прямоугольная"),
    TRIANGLE("Треугольная"),
    CORNER("Угловая"),
    ROUND("Круговая");

    private final String label;

    CabinetShape(String label) {
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
