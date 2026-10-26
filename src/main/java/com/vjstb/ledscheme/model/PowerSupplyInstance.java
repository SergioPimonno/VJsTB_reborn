package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Блок питания на экране (docs/receiver-cards/PLAN.md, 4.2): питает набор МОДУЛЕЙ (не карты — решение D17), «место»
 * блока — модуль, от которого считается длина кабелей. Приёмную карту, чьё место попало в область блока, блок питает
 * вычисляемо ({@code service.PowerSupplyLayout}, D19), в данных этого нет.
 *
 * <p>{@link #id} — внутренний идентификатор: на него ссылаются цепочки фаз ({@code PowerChain.cabinetInstanceIds}
 * хранит id звеньев), имена БП1…БПn вычисляются и в данных не лежат. Модуль состоит максимум в одном блоке.
 */
public class PowerSupplyInstance {

    private String id = UUID.randomUUID().toString();
    /** Тип из библиотеки ({@link PowerSupplyType}); пропавший тип — проверки лимитов молча пропускаются. */
    private String powerSupplyTypeId;
    /** Id питаемых модулей ({@link CabinetInstance#getId()}), в порядке чтения. */
    private List<String> moduleIds = new ArrayList<>();
    /** Модуль-«место» блока (D20); {@code null} — авто: ближайший к центру области. */
    private String hostModuleId;

    public PowerSupplyInstance() {
    }

    public PowerSupplyInstance(String powerSupplyTypeId, List<String> moduleIds) {
        this.powerSupplyTypeId = powerSupplyTypeId;
        this.moduleIds = new ArrayList<>(moduleIds);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPowerSupplyTypeId() {
        return powerSupplyTypeId;
    }

    public void setPowerSupplyTypeId(String powerSupplyTypeId) {
        this.powerSupplyTypeId = powerSupplyTypeId;
    }

    public List<String> getModuleIds() {
        return moduleIds;
    }

    public void setModuleIds(List<String> moduleIds) {
        this.moduleIds = moduleIds != null ? moduleIds : new ArrayList<>();
    }

    public String getHostModuleId() {
        return hostModuleId;
    }

    public void setHostModuleId(String hostModuleId) {
        this.hostModuleId = hostModuleId;
    }

    public PowerSupplyInstance copy() {
        PowerSupplyInstance c = new PowerSupplyInstance();
        c.id = id;
        c.powerSupplyTypeId = powerSupplyTypeId;
        c.moduleIds = new ArrayList<>(moduleIds);
        c.hostModuleId = hostModuleId;
        return c;
    }
}
