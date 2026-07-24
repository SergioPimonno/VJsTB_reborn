package com.vjstb.ledscheme.model;

/** Тип разъёма ввода питания САМОГО КАБИНЕТА (не схемы в целом) — у LED-кабинетов
 *  это почти всегда PowerCon или TRUEcon. Остальные типы силовой коммутации (CEE,
 *  Schuko и т.д.) используются выше по цепи (щит→дистрибьютор и т.д.) и остаются
 *  в общем списке кабелей схемы (WireLabelDialog), а не привязаны к типу кабинета. */
public enum PowerConnectorType {
    POWERCON("PowerCon"),
    TRUECON("TRUEcon"),
    OTHER("Другой/не указан");

    private final String label;

    PowerConnectorType(String label) {
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
