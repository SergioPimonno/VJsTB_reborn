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
 * сетей в рамках одной сцены. */
public class NetworkManagerPlan {

    private List<Network> networks = new ArrayList<>();

    public NetworkManagerPlan() {
    }

    public List<Network> getNetworks() {
        return networks;
    }

    public void setNetworks(List<Network> networks) {
        this.networks = networks;
    }
}
