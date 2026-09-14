package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import java.util.ArrayList;
import java.util.List;

/**
 * Резолв "кто где" для {@link NetworkManagerPlan} (Round 8: устройства и связи
 * теперь общий список плана, а не собственность {@link Network} — см. её
 * class-javadoc за мотивацией). Все методы ЖИВЬЁМ ищут по спискам плана при
 * каждом вызове, ничего не кэшируют — тот же принцип, что {@code
 * NetworkCanvasPanel.resolveLabel}/{@code resolveColor}: план меняется чаще,
 * чем стоит поддерживать отдельный индекс в актуальном состоянии, а размеры
 * (десятки устройств на сцену) делают линейный поиск дешёвым.
 *
 * <p>Центральное место, куда переехала логика "чья это связь/устройство",
 * раньше неявно гарантированная тем, что {@code Network} сама владела своими
 * списками — теперь членство определяется через {@link
 * NetworkDevicePlacement#getAttachments()}, и это единственное место, которое
 * о них знает: {@code ui.NetworkCanvasPanel}/{@code ui.NetworkManagerPanel} и
 * диалоги обращаются сюда, а не лезут в attachments напрямую. */
public final class NetworkTopology {

    private NetworkTopology() {
    }

    public static NetworkDevicePlacement deviceById(NetworkManagerPlan plan, String id) {
        if (id == null) {
            return null;
        }
        for (NetworkDevicePlacement p : plan.getDevices()) {
            if (id.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    public static Network networkById(NetworkManagerPlan plan, String id) {
        if (id == null) {
            return null;
        }
        for (Network n : plan.getNetworks()) {
            if (id.equals(n.getId())) {
                return n;
            }
        }
        return null;
    }

    /** Устройства, у которых есть подключение ({@link NetworkAttachment}) к
     *  сети {@code networkId} — порядок как в {@code plan.getDevices()}. */
    public static List<NetworkDevicePlacement> devicesInNetwork(NetworkManagerPlan plan, String networkId) {
        List<NetworkDevicePlacement> result = new ArrayList<>();
        for (NetworkDevicePlacement p : plan.getDevices()) {
            if (p.attachmentFor(networkId) != null) {
                result.add(p);
            }
        }
        return result;
    }

    /** Сеть, которой принадлежит порт {@code port1Based} устройства {@code
     *  device} — резолвится по {@link NetworkAttachment#coversPort}
     *  ПЕРВОГО подключения, покрывающего этот порт (при обычной одной сети на
     *  устройство однозначно; при нескольких — пользователь должен явно
     *  разнести номера портов между подключениями, иначе неоднозначность
     *  разрешается в пользу первого по порядку подключения). {@code null} —
     *  устройство ни в одной сети, либо порт ни в одном подключении. */
    public static Network networkOfPort(NetworkManagerPlan plan, NetworkDevicePlacement device, int port1Based) {
        if (device == null) {
            return null;
        }
        for (NetworkAttachment a : device.getAttachments()) {
            if (a.coversPort(port1Based)) {
                return networkById(plan, a.getNetworkId());
            }
        }
        return null;
    }

    /** Сеть связи {@code link} — резолвится по сети порта-ИСТОЧНИКА (см. {@link
     *  #networkOfPort}); порт-приёмник гарантированно резолвится в ТУ ЖЕ сеть,
     *  т.к. связи между устройствами разных сетей запрещены при создании (см.
     *  {@code ui.NetworkCanvasPanel#createLink}) — здесь эта гарантия НЕ
     *  проверяется повторно, просто используется. {@code null}, если
     *  устройство-источник с тех пор удалено или разъединено с сетью. */
    public static Network networkOfLink(NetworkManagerPlan plan, NetworkLink link) {
        NetworkDevicePlacement from = deviceById(plan, link.getFromDeviceId());
        return networkOfPort(plan, from, link.getFromPort());
    }

    /** Связи, чья сеть (см. {@link #networkOfLink}) — {@code networkId}. */
    public static List<NetworkLink> linksInNetwork(NetworkManagerPlan plan, String networkId) {
        List<NetworkLink> result = new ArrayList<>();
        for (NetworkLink link : plan.getLinks()) {
            Network n = networkOfLink(plan, link);
            if (n != null && networkId.equals(n.getId())) {
                result.add(link);
            }
        }
        return result;
    }
}
