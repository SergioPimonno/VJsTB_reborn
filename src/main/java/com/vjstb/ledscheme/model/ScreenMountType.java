package com.vjstb.ledscheme.model;

/** Способ монтажа экрана — влияет на то, какой расчёт крепления к нему применим. */
public enum ScreenMountType {
    RIGGED("Подвес"),
    STRUCTURE("На конструктиве"),
    LAYER("На лайере"),
    FLOOR("Напольный");

    private final String label;

    ScreenMountType(String label) {
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
