package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Цепочка расключения питания: упорядоченный список кабинетов (daisy chain)
 * на одной фазе (1/2/3).
 */
public class PowerChain {

    private String id = UUID.randomUUID().toString();
    private int phase = 1;
    /** Id ЗВЕНЬЕВ цепочки по порядку. Имя поля историческое: до модульных экранов звено всегда было кабинетом
     *  ({@link CabinetInstance#getId()}); теперь это id кабинета ИЛИ id блока питания
     *  ({@link PowerSupplyInstance#getId()}) модульного экрана (модули в питании звеньями не бывают — их питает
     *  блок). UUID не пересекаются, формат файла не менялся. Разворачивать звенья — только через
     *  {@code service.ChainUnits}; неизвестные id отбрасываются при чтении ({@code ChainUnits#pruneUnknown}). */
    private List<String> cabinetInstanceIds = new ArrayList<>();
    /** Нагрузка (Вт), при которой инженер подтвердил перегрузку кнопкой «Я знаю»
     *  (Task #81) — null, если не подтверждалась. Предупреждение считается
     *  подтверждённым, пока текущая нагрузка цепочки не ПРЕВЫСИТ это значение —
     *  так добавление кабинетов в уже подтверждённую перегруженную цепочку снова
     *  требует подтверждения, а простое пересохранение/переоткрытие проекта — нет. */
    private Double acknowledgedOverloadWatts;
    /** Свой цвет цепочки (RGB), null — цвет по фазе как раньше (см. Palette.phaseColor).
     *  По аналогии с {@link SchemaEdge#getColor()} в общей схеме (Task #4) — там же
     *  образец UI (контекстное меню «Цвет линии…»/«Сбросить цвет»). */
    private Integer color;

    public PowerChain() {
    }

    public PowerChain(int phase, List<String> cabinetInstanceIds) {
        this.phase = phase;
        this.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public List<String> getCabinetInstanceIds() {
        return cabinetInstanceIds;
    }

    public void setCabinetInstanceIds(List<String> cabinetInstanceIds) {
        this.cabinetInstanceIds = cabinetInstanceIds;
    }

    public Double getAcknowledgedOverloadWatts() {
        return acknowledgedOverloadWatts;
    }

    public void setAcknowledgedOverloadWatts(Double acknowledgedOverloadWatts) {
        this.acknowledgedOverloadWatts = acknowledgedOverloadWatts;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    public PowerChain copy() {
        PowerChain c = new PowerChain();
        c.id = id;
        c.phase = phase;
        c.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
        c.acknowledgedOverloadWatts = acknowledgedOverloadWatts;
        c.color = color;
        return c;
    }
}
