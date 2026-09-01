package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Одна компьютерная сеть внутри {@link NetworkManagerPlan} — персистентная
 * форма {@code ui.NetworkManagerPanel.NetworkSection}. В отличие от {@link
 * VehicleLoadSection} (та идентифицируется только ссылкой на тип машины,
 * "машины" не именуются пользователем) — сеть ЕСТЬ пользовательская сущность
 * со своим именем, поэтому несёт {@link #id} (для добавления/переименования/
 * удаления без опоры на индекс списка) и {@link #name}. */
public class Network {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private List<NetworkDevicePlacement> devices = new ArrayList<>();
    /** Кабельные связи между устройствами ЭТОЙ сети (порт-в-порт) — см. {@link
     *  NetworkLink}. */
    private List<NetworkLink> links = new ArrayList<>();
    /** Цвет линий связи ЭТОЙ сети (RGB, см. {@code Color#getRGB()}) — {@code
     *  null} означает "цвет по умолчанию" (см. {@code ui.NetworkCanvasPanel
     *  #DEFAULT_LINK_COLOR}). Баг-репорт: "цвет линии должен зависеть от цвета
     *  выбранного для сети" — раньше все связи любой сети рисовались одним
     *  фиксированным цветом, что не позволяло на глаз отличить, каким сетям
     *  принадлежат линии, если устройства нескольких сетей физически рядом на
     *  канвасе. {@code ui.NetworkManagerPanel#addNetwork} подставляет новой сети
     *  цвет по умолчанию (по золотому углу от индекса — соседние по порядку
     *  сети получают заметно разные оттенки), пользователь может изменить
     *  кнопкой «Цвет…». */
    private Integer color;

    public Network() {
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

    public List<NetworkDevicePlacement> getDevices() {
        return devices;
    }

    public void setDevices(List<NetworkDevicePlacement> devices) {
        this.devices = devices;
    }

    public List<NetworkLink> getLinks() {
        return links;
    }

    public void setLinks(List<NetworkLink> links) {
        this.links = links;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }
}
