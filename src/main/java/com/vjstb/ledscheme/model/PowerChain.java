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
    private List<String> cabinetInstanceIds = new ArrayList<>();
    /** Нагрузка (Вт), при которой инженер подтвердил перегрузку кнопкой «Я знаю»
     *  (Task #81) — null, если не подтверждалась. Предупреждение считается
     *  подтверждённым, пока текущая нагрузка цепочки не ПРЕВЫСИТ это значение —
     *  так добавление кабинетов в уже подтверждённую перегруженную цепочку снова
     *  требует подтверждения, а простое пересохранение/переоткрытие проекта — нет. */
    private Double acknowledgedOverloadWatts;

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

    public PowerChain copy() {
        PowerChain c = new PowerChain();
        c.id = id;
        c.phase = phase;
        c.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
        c.acknowledgedOverloadWatts = acknowledgedOverloadWatts;
        return c;
    }
}
