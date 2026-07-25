package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок ВСЕХ общих библиотек оборудования разом (кабинеты, контроллеры,
 * пресеты и кабели) — формат файла для кнопки «Экспорт/импорт всего сразу»
 * на этапе «Библиотеки», в дополнение к экспорту/импорту одного типа за раз.
 */
public class LibraryBundle {

    private List<CabinetType> cabinetTypes = new ArrayList<>();
    private List<ControllerType> controllerTypes = new ArrayList<>();
    private List<EquipmentPreset> equipmentPresets = new ArrayList<>();
    private List<CableType> cableTypes = new ArrayList<>();

    public List<CabinetType> getCabinetTypes() {
        return cabinetTypes;
    }

    public void setCabinetTypes(List<CabinetType> cabinetTypes) {
        this.cabinetTypes = cabinetTypes;
    }

    public List<ControllerType> getControllerTypes() {
        return controllerTypes;
    }

    public void setControllerTypes(List<ControllerType> controllerTypes) {
        this.controllerTypes = controllerTypes;
    }

    public List<EquipmentPreset> getEquipmentPresets() {
        return equipmentPresets;
    }

    public void setEquipmentPresets(List<EquipmentPreset> equipmentPresets) {
        this.equipmentPresets = equipmentPresets;
    }

    public List<CableType> getCableTypes() {
        return cableTypes;
    }

    public void setCableTypes(List<CableType> cableTypes) {
        this.cableTypes = cableTypes;
    }
}
