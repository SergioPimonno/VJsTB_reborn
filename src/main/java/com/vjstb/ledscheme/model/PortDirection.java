package com.vjstb.ledscheme.model;

/** Направление группы портов карты — вход, выход, или одновременно оба (гнездо
 *  сквозного прохода/loop-through, например SDI Loop In/Out или Genlock Loop —
 *  физически один и тот же разъём работает и как вход, и как выход). */
public enum PortDirection {
    IN("Вход"),
    OUT("Выход"),
    IN_OUT("Вход/выход");

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
