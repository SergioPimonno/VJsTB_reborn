package com.vjstb.ledscheme.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Цепочка расключения сигнала (данные): упорядоченный список кабинетов
 * (daisy chain), опционально привязана к порту контроллера.
 */
@Entity
@Table(name = "signal_chain")
public class SignalChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    /** Номер порта контроллера, если цепочка привязана к порту. */
    private Integer portNumber;

    @Column(nullable = false)
    private boolean backup = false;

    @ElementCollection
    @CollectionTable(name = "signal_chain_cabinet", joinColumns = @JoinColumn(name = "signal_chain_id"))
    @OrderColumn(name = "position")
    @Column(name = "cabinet_instance_id", nullable = false)
    private List<Long> cabinetInstanceIds = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Screen getScreen() {
        return screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
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

    public List<Long> getCabinetInstanceIds() {
        return cabinetInstanceIds;
    }

    public void setCabinetInstanceIds(List<Long> cabinetInstanceIds) {
        this.cabinetInstanceIds = cabinetInstanceIds;
    }
}
