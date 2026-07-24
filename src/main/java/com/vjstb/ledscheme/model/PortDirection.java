package com.vjstb.ledscheme.model;

/** Направление группы портов карты — вход или выход. */
public enum PortDirection {
    IN("Вход"),
    OUT("Выход");

    private final String label;

    PortDirection(String label) {
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
