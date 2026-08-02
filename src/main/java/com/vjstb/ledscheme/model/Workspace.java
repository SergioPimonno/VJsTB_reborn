package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Корневой контейнер данных приложения: библиотека кабинетов + проекты.
 * Сериализуется целиком в локальный JSON-файл.
 */
public class Workspace {

    private List<CabinetType> cabinetTypes = new ArrayList<>();
    /** Библиотека типов контроллеров (аналог SmartLCT: модель, число портов, лимит на порт). */
    private List<ControllerType> controllerTypes = new ArrayList<>();
    /** Библиотека пресетов оборудования (медиасерверы/видеопроцессоры/конвертеры и т.д.)
     *  для быстрой вставки узлов общей схемы. */
    private List<EquipmentPreset> equipmentPresets = new ArrayList<>();
    /** Библиотека пользовательских кабелей/переходников (например «CEE 16A →
     *  TrueCON») — дополняет встроенные пресеты в WireLabelDialog/PowerConnectorsConfigDialog. */
    private List<CableType> cableTypes = new ArrayList<>();
    /** Справочник видов интерфейса (HDMI/DisplayPort/SDI/...) и их версий — общий
     *  для карт оборудования, кабелей и подписи связи схемы (см. InterfaceType). */
    private List<InterfaceType> interfaceTypes = new ArrayList<>();
    /** Админ-редактируемые подкатегории оборудования под "Прочее оборудование" —
     *  см. EquipmentPreset.customCategoryLabel/AdminDialog. */
    private List<String> customEquipmentCategories = new ArrayList<>();
    /** Переопределение ПОДПИСИ встроенной категории оборудования (см. SchemaNodeType),
     *  ключ — имя константы enum (SOURCE, DISTRO, ...), значение — новый текст,
     *  который видит пользователь (см. AppModel.categoryLabel). Сам список категорий
     *  фиксирован — сюда попадают только переименованные, остальные просто
     *  отсутствуют в карте и используют SchemaNodeType.getLabel() как есть. */
    private Map<String, String> equipmentCategoryLabelOverrides = new LinkedHashMap<>();
    private List<Project> projects = new ArrayList<>();

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

    public List<InterfaceType> getInterfaceTypes() {
        return interfaceTypes;
    }

    public void setInterfaceTypes(List<InterfaceType> interfaceTypes) {
        this.interfaceTypes = interfaceTypes;
    }

    public List<String> getCustomEquipmentCategories() {
        return customEquipmentCategories;
    }

    public void setCustomEquipmentCategories(List<String> customEquipmentCategories) {
        this.customEquipmentCategories = customEquipmentCategories;
    }

    public Map<String, String> getEquipmentCategoryLabelOverrides() {
        return equipmentCategoryLabelOverrides;
    }

    public void setEquipmentCategoryLabelOverrides(Map<String, String> equipmentCategoryLabelOverrides) {
        this.equipmentCategoryLabelOverrides = equipmentCategoryLabelOverrides;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public CabinetType cabinetTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (CabinetType ct : cabinetTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        return null;
    }

    public ControllerType controllerTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (ControllerType ct : controllerTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        return null;
    }
}
