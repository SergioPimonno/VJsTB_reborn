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
 * Цепочка расключения питания: упорядоченный список кабинетов (daisy chain)
 * на одной фазе (1/2/3).
 */
@Entity
@Table(name = "power_chain")
public class PowerChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private int phase;

    @ElementCollection
    @CollectionTable(name = "power_chain_cabinet", joinColumns = @JoinColumn(name = "power_chain_id"))
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

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public List<Long> getCabinetInstanceIds() {
        return cabinetInstanceIds;
    }

    public void setCabinetInstanceIds(List<Long> cabinetInstanceIds) {
        this.cabinetInstanceIds = cabinetInstanceIds;
    }
}
