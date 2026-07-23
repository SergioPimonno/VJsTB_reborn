package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Корневой контейнер данных приложения: библиотека кабинетов + проекты.
 * Сериализуется целиком в локальный JSON-файл.
 */
public class Workspace {

    private List<CabinetType> cabinetTypes = new ArrayList<>();
    /** Библиотека типов контроллеров (аналог SmartLCT: модель, число портов, лимит на порт). */
    private List<ControllerType> controllerTypes = new ArrayList<>();
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
