package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Тип видеоконтроллера в библиотеке (аналог профилей устройств в SmartLCT/Novastar):
 * модель, число портов вывода и предельная нагрузка на порт. Библиотека общая для
 * всех проектов, как и {@link CabinetType}.
 */
public class ControllerType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String vendor = "";
    /** Число портов вывода (Ethernet/оптика) на устройство. */
    private int portCount = 8;
    /** Максимум пикселей на один порт (типовое ограничение видеоконтроллеров). */
    private int maxPixelsPerPort = 650_000;

    public ControllerType() {
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

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public int getPortCount() {
        return portCount;
    }

    public void setPortCount(int portCount) {
        this.portCount = portCount;
    }

    public int getMaxPixelsPerPort() {
        return maxPixelsPerPort;
    }

    public void setMaxPixelsPerPort(int maxPixelsPerPort) {
        this.maxPixelsPerPort = maxPixelsPerPort;
    }

    public ControllerType copy() {
        ControllerType c = new ControllerType();
        c.id = id;
        c.name = name;
        c.vendor = vendor;
        c.portCount = portCount;
        c.maxPixelsPerPort = maxPixelsPerPort;
        return c;
    }
}
