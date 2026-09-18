package com.vjstb.ledscheme.settings;

/** Где рисовать стрелку направления связи общей схемы (docs/schema-ports-rework/
 *  PLAN.md, задача T1.5, D15). {@link #TARGET} — одна стрелка у конца, входящего в
 *  приёмник, как на референсных схемах пользователя из yEd — новое умолчание.
 *  {@link #SEGMENTS} — прежнее поведение: стрелка на КАЖДОМ прямом отрезке ломаной
 *  (включая промежуточные — читается как «поток идёт по всей линии», а не только
 *  «откуда куда»), оставлено как явно выбираемый вариант. */
public enum ArrowPlacement {
    TARGET("У приёмника"),
    SEGMENTS("На каждом отрезке");

    private final String label;

    ArrowPlacement(String label) {
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
