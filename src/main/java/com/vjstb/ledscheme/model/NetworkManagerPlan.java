package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Сетевой менеджер сцены (см. {@code ui.NetworkManagerPanel}, вкладка
 * раздела «Сигнал») — персистится как обычные данные сцены (см. {@link
 * Scene#getNetworkManagerPlan()}), той же генерической Jackson-сериализацией,
 * что и {@link VehicleLoadPlan}/{@link ContentCanvas}/{@link SchemaNode}, без
 * отдельного кода (де)сериализации. В отличие от {@code VehicleLoadPlan}
 * ("ОДИН план на сцену, осознанное упрощение v1") — здесь список {@link
 * Network} и есть сама суть фичи: пользователь заводит НЕСКОЛЬКО отдельных
 * сетей в рамках одной сцены.
 *
 * <p><b>Round 8</b> — {@link #devices}/{@link #links} переехали СЮДА, на
 * уровень плана (раньше жили внутри каждой {@link Network} — см. её
 * class-javadoc за мотивацией). Каждое устройство поля — РОВНО один элемент
 * {@link #devices}, независимо от того, в скольких сетях оно состоит
 * ({@code NetworkDevicePlacement.getAttachments()}); связи ({@link
 * NetworkLink}) — тоже общий список плана, привязка к конкретной сети
 * определяется ЖИВЬЁМ через сеть, покрывающую порт устройства-источника
 * (см. {@code service.NetworkTopology}), а не хранится на самой связи. */
public class NetworkManagerPlan {

    private List<Network> networks = new ArrayList<>();
    private List<NetworkDevicePlacement> devices = new ArrayList<>();
    private List<NetworkLink> links = new ArrayList<>();

    public NetworkManagerPlan() {
    }

    public List<Network> getNetworks() {
        return networks;
    }

    public void setNetworks(List<Network> networks) {
        this.networks = networks != null ? networks : new ArrayList<>();
    }

    public List<NetworkDevicePlacement> getDevices() {
        return devices;
    }

    public void setDevices(List<NetworkDevicePlacement> devices) {
        this.devices = devices != null ? devices : new ArrayList<>();
    }

    public List<NetworkLink> getLinks() {
        return links;
    }

    public void setLinks(List<NetworkLink> links) {
        this.links = links != null ? links : new ArrayList<>();
    }
}
