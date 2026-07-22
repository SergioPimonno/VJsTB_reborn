package com.vjstb.ledscheme.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Экран составляется из сетки rows x cols однотипных LED cabinets.
 * Характеристики экрана (разрешение, габариты, вес, потребление) вычисляются
 * из типа кабинета и размера сетки, с учётом скрытых (hidden) кабинетов.
 */
@Entity
@Table(name = "screen")
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_type_id", nullable = false)
    private CabinetType cabinetType;

    @Column(nullable = false)
    private int rows;

    @Column(nullable = false)
    private int cols;

    /** Позиция левого верхнего угла экрана в сцене, мм. */
    @Column(nullable = false)
    private double posXMm;

    @Column(nullable = false)
    private double posYMm;

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowIndex ASC, colIndex ASC")
    private List<CabinetInstance> cabinets = new ArrayList<>();

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PowerChain> powerChains = new ArrayList<>();

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SignalChain> signalChains = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public CabinetType getCabinetType() {
        return cabinetType;
    }

    public void setCabinetType(CabinetType cabinetType) {
        this.cabinetType = cabinetType;
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

    // ---- вычисляемые характеристики экрана (не персистятся) ----

    public double getPhysicalWidthMm() {
        return cols * cabinetType.getWidthMm();
    }

    public double getPhysicalHeightMm() {
        return rows * cabinetType.getHeightMm();
    }

    public int getResolutionWidthPx() {
        return cols * cabinetType.getResolutionWidth();
    }

    public int getResolutionHeightPx() {
        return rows * cabinetType.getResolutionHeight();
    }

    private List<CabinetInstance> activeCabinets() {
        return cabinets.stream().filter(c -> !c.isHidden()).toList();
    }

    public double getTotalPowerW() {
        return activeCabinets().size() * cabinetType.getPowerConsumptionW();
    }

    public double getTotalWeightKg() {
        return activeCabinets().size() * cabinetType.getWeightKg();
    }

    public int getActiveCabinetCount() {
        return activeCabinets().size();
    }
}
