package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Цепочка расключения сигнала (данные): упорядоченный список кабинетов
 * (daisy chain), опционально привязана к порту контроллера.
 */
public class SignalChain {

    private String id = UUID.randomUUID().toString();
    private Integer portNumber;
    private boolean backup;
    private List<String> cabinetInstanceIds = new ArrayList<>();

    public SignalChain() {
    }

    public SignalChain(Integer portNumber, boolean backup, List<String> cabinetInstanceIds) {
        this.portNumber = portNumber;
        this.backup = backup;
        this.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    public boolean isBackup() {
        return backup;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public List<String> getCabinetInstanceIds() {
        return cabinetInstanceIds;
    }

    public void setCabinetInstanceIds(List<String> cabinetInstanceIds) {
        this.cabinetInstanceIds = cabinetInstanceIds;
    }

    public SignalChain copy() {
        SignalChain c = new SignalChain();
        c.id = id;
        c.portNumber = portNumber;
        c.backup = backup;
        c.cabinetInstanceIds = new ArrayList<>(cabinetInstanceIds);
        return c;
    }
}
