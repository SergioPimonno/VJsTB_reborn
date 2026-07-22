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

    public PowerChain copy() {
        PowerChain c = new PowerChain();
        c.id = id;
        c.phase = phase;
        c.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
        return c;
    }
}
