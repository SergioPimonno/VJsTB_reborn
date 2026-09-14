package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Подключение ОДНОГО {@link NetworkDevicePlacement} к ОДНОЙ {@link Network} —
 * «сетевой интерфейс» устройства: адрес, маска, шлюз и, опционально, номера
 * портов, которыми устройство сидит именно в этой сети.
 *
 * <p><b>Зачем отдельная сущность</b> (запрос пользователя: «один блок может
 * добавляться в поле 1 раз, но может принадлежать разным сеткам при условии,
 * что у него больше 1 порта и соответственно адреса»): до этого адрес был
 * полем самого {@link NetworkDevicePlacement}, а само размещение физически
 * лежало ВНУТРИ одной {@code Network} — значит одно физическое устройство,
 * входящее в две сети, приходилось заводить на поле ДВАЖДЫ, двумя разными
 * блоками с разными id. Это неверно и по смыслу (на площадке это один ящик), и
 * технически (связи ссылаются на id размещения, так что два «клона» одного
 * медиасервера нельзя было соединить кабелем как один узел). Теперь блок на
 * поле ровно один, а принадлежность сетям — список этих подключений: у
 * большинства устройств оно одно, у медиасервера с двумя NIC — два, с разными
 * адресами.
 *
 * <p>{@link #ports} — номера портов (1-based, в нумерации портов устройства),
 * отданные ИМЕННО этой сети. ПУСТОЙ список означает «все порты устройства» —
 * нормальный случай для свитча целиком в одной сети, и он же обеспечивает
 * корректную миграцию старых проектов (там понятия «порт в сети» не было
 * вовсе, см. {@code store.WorkspaceStore#migrateLegacyNetworkPlans}). Явный
 * список нужен ровно для многосетевых устройств: первый NIC в «Admin», второй
 * в «Content».
 */
public class NetworkAttachment {

    private String id = UUID.randomUUID().toString();
    /** {@link Network#getId()} сети, к которой подключено устройство. */
    private String networkId;
    private String ipAddress = "";
    private String subnetMask = "";
    private String gateway = "";
    /** Пусто = все порты устройства принадлежат этой сети, см. class-javadoc. */
    private List<Integer> ports = new ArrayList<>();

    public NetworkAttachment() {
    }

    public NetworkAttachment(String networkId) {
        this.networkId = networkId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress != null ? ipAddress : "";
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask != null ? subnetMask : "";
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway != null ? gateway : "";
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public void setPorts(List<Integer> ports) {
        this.ports = ports != null ? ports : new ArrayList<>();
    }

    /** {@code true}, если порт {@code port1Based} принадлежит этой сети — с
     *  учётом соглашения «пустой список = все порты» (см. class-javadoc). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean coversPort(int port1Based) {
        return ports.isEmpty() || ports.contains(port1Based);
    }
}
