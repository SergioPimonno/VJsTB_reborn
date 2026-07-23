package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Экземпляр контроллера, назначенный экрану. Экран может обслуживаться несколькими
 * контроллерами — их суммарное число портов даёт общее число портов расключения сигнала.
 */
public class ControllerInstance {

    private String id = UUID.randomUUID().toString();
    private String controllerTypeId;
    private String label = "";

    public ControllerInstance() {
    }

    public ControllerInstance(String controllerTypeId, String label) {
        this.controllerTypeId = controllerTypeId;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getControllerTypeId() {
        return controllerTypeId;
    }

    public void setControllerTypeId(String controllerTypeId) {
        this.controllerTypeId = controllerTypeId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public ControllerInstance copy() {
        ControllerInstance c = new ControllerInstance();
        c.id = id;
        c.controllerTypeId = controllerTypeId;
        c.label = label;
        return c;
    }
}
