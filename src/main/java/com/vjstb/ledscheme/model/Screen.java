package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран составляется из сетки rows x cols однотипных LED cabinets.
 * Ссылается на тип кабинета из библиотеки по cabinetTypeId; характеристики
 * (разрешение, габариты, вес, потребление) вычисляются из типа и размера сетки.
 */
public class Screen {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String cabinetTypeId;
    private int rows;
    private int cols;
    /** Позиция левого верхнего угла экрана в сцене, мм. */
    private double posXMm;
    private double posYMm;

    private List<CabinetInstance> cabinets = new ArrayList<>();
    private List<PowerChain> powerChains = new ArrayList<>();
    private List<SignalChain> signalChains = new ArrayList<>();

    public Screen() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCabinetTypeId() {
        return cabinetTypeId;
    }

    public void setCabinetTypeId(String cabinetTypeId) {
        this.cabinetTypeId = cabinetTypeId;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public double getPosXMm() {
        return posXMm;
    }

    public void setPosXMm(double posXMm) {
        this.posXMm = posXMm;
    }

    public double getPosYMm() {
        return posYMm;
    }

    public void setPosYMm(double posYMm) {
        this.posYMm = posYMm;
    }

    public List<CabinetInstance> getCabinets() {
        return cabinets;
    }

    public void setCabinets(List<CabinetInstance> cabinets) {
        this.cabinets = cabinets;
    }

    public List<PowerChain> getPowerChains() {
        return powerChains;
    }

    public void setPowerChains(List<PowerChain> powerChains) {
        this.powerChains = powerChains;
    }

    public List<SignalChain> getSignalChains() {
        return signalChains;
    }

    public void setSignalChains(List<SignalChain> signalChains) {
        this.signalChains = signalChains;
    }

    // ---- вспомогательное ----

    @JsonIgnore
    public CabinetInstance cabinetById(String cabId) {
        for (CabinetInstance c : cabinets) {
            if (c.getId().equals(cabId)) {
                return c;
            }
        }
        return null;
    }

    @JsonIgnore
    public CabinetInstance cabinetAt(int row, int col) {
        for (CabinetInstance c : cabinets) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    public Screen copy() {
        Screen s = new Screen();
        s.id = id;
        s.name = name;
        s.cabinetTypeId = cabinetTypeId;
        s.rows = rows;
        s.cols = cols;
        s.posXMm = posXMm;
        s.posYMm = posYMm;
        s.cabinets = new ArrayList<>();
        for (CabinetInstance c : cabinets) {
            s.cabinets.add(c.copy());
        }
        s.powerChains = new ArrayList<>();
        for (PowerChain c : powerChains) {
            s.powerChains.add(c.copy());
        }
        s.signalChains = new ArrayList<>();
        for (SignalChain c : signalChains) {
            s.signalChains.add(c.copy());
        }
        return s;
    }
}
