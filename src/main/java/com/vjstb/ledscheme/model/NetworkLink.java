package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Кабельная связь между двумя устройствами ОДНОЙ {@link Network} (см. {@code
 * ui.NetworkCanvasPanel} — клик по порту одного устройства, затем по порту
 * другого, тот же приём, что рисование связей на общей схеме
 * питания/сигнала, {@code SchemaEdge}, только без промежуточных точек
 * излома — прямая линия порт-в-порт). Нужна пользователю для подробных схем
 * и сложной маршрутизации, не просто списка устройств с адресами.
 *
 * <p>{@link #fromDeviceId}/{@link #toDeviceId} — id КОНКРЕТНОГО {@link
 * NetworkDevicePlacement#getId()} в той же сети (не {@code
 * linkedSchemaNodeId}/{@code deviceTypeId} — два размещения в одной сети
 * теоретически могут ссылаться на один и тот же тип/узел, поэтому нужен
 * якорь именно на размещение). {@link #fromPort}/{@link #toPort} — номер
 * порта, 1-based, в пределах {@code NetworkDevicePlacement#getPortCount()}
 * устройства-источника/приёмника на момент создания связи (не проверяется
 * повторно при каждой отрисовке — если пользователь потом уменьшит
 * portCount ниже номера уже подключённого порта, связь остаётся, просто
 * рисуется за пределами видимых портов; сознательно не отслеживаем это
 * автоматически, как и `SchemaEdge` не удаляет себя при смене типа карты). */
public class NetworkLink {

    private String id = UUID.randomUUID().toString();
    private String fromDeviceId;
    private int fromPort;
    private String toDeviceId;
    private int toPort;
    private String label = "";
    /** Точки излома маршрута связи (см. {@link NetworkLinkWaypoint}) — пусто (по
     *  умолчанию) означает прямую линию порт-в-порт, как раньше; непустой список
     *  рисует связь ломаной линией через эти точки (то же самое, что {@code
     *  SchemaEdge#getWaypoints()} у общей схемы площадки, добавляются двойным
     *  кликом по линии в {@code ui.NetworkCanvasPanel}). */
    private List<NetworkLinkWaypoint> waypoints = new ArrayList<>();

    public NetworkLink() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromDeviceId() {
        return fromDeviceId;
    }

    public void setFromDeviceId(String fromDeviceId) {
        this.fromDeviceId = fromDeviceId;
    }

    public int getFromPort() {
        return fromPort;
    }

    public void setFromPort(int fromPort) {
        this.fromPort = fromPort;
    }

    public String getToDeviceId() {
        return toDeviceId;
    }

    public void setToDeviceId(String toDeviceId) {
        this.toDeviceId = toDeviceId;
    }

    public int getToPort() {
        return toPort;
    }

    public void setToPort(int toPort) {
        this.toPort = toPort;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<NetworkLinkWaypoint> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<NetworkLinkWaypoint> waypoints) {
        this.waypoints = waypoints != null ? waypoints : new ArrayList<>();
    }
}
