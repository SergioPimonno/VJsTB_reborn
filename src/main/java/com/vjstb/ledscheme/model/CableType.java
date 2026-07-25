package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Пользовательский тип кабеля/переходника библиотеки — например, комбинированный
 * переходник «CEE 16A → TrueCON», который не входит во встроенный список пресетов
 * (см. WireLabelDialog/PowerConnectorsConfigDialog), но нужен часто на конкретной
 * площадке/у конкретного инженера. Библиотека общая для всех проектов, как
 * {@link CabinetType}/{@link ControllerType}/{@link EquipmentPreset} — сохранённый
 * кабель сразу доступен в списке подписи связи и в списке типов разъёма распределения.
 */
public class CableType {

    private String id = UUID.randomUUID().toString();
    /** Питание и сигнал — разные списки кабелей (те же типы названий могут значить
     *  разное: например, "многожильный" не имеет смысла в сигнальной коммутации). */
    private SchemaMode mode = SchemaMode.POWER;
    private String label = "";

    public CableType() {
    }

    public CableType(SchemaMode mode, String label) {
        this.mode = mode;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SchemaMode getMode() {
        return mode;
    }

    public void setMode(SchemaMode mode) {
        this.mode = mode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CableType copy() {
        CableType c = new CableType();
        c.id = id;
        c.mode = mode;
        c.label = label;
        return c;
    }
}
