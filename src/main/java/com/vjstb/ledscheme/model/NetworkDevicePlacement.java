package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Одно устройство, размещённое в {@link Network} (см. {@code
 * ui.NetworkCanvasPanel}) — персистентная форма {@code
 * ui.NetworkCanvasPanel.PlacedDevice}. Ссылается на источник имени/типа ПО
 * ID, не embed'ит объект — тот же приём, что и у {@code
 * VehicleLoadPlacement#caseTypeId}/{@code Screen#cabinetTypeId}, чтобы не
 * плодить устаревающие копии внутри сохранённого проекта.
 *
 * <p>Ровно ОДНО из {@link #linkedSchemaNodeId}/{@link #deviceTypeId} обычно
 * заполнено (может быть и ни одного — тогда отображается {@link
 * #customLabel}): {@code linkedSchemaNodeId} — устройство добавлено ИЗ
 * общей схемы сигнала (id существующего {@code SchemaNode}, резолвится
 * ЖИВЬЁМ при каждой отрисовке — переименование узла в схеме сразу видно
 * здесь; если узел с тех пор удалён из схемы, ссылка молча игнорируется, тот
 * же приём, что у {@code VehicleLoadVisualizerDialog#loadOrInitSections});
 * {@code deviceTypeId} — устройство добавлено как НОВЫЙ блок из каталога
 * {@code NetworkDeviceType} (общая библиотека, вид {@code
 * LibraryItemKind.NETWORK_DEVICE}). Связь с общей схемой ОДНОНАПРАВЛЕННАЯ —
 * Сетевой менеджер только читает {@code SchemaNode}, никогда не пишет в
 * схему.
 *
 * <p>{@link #xMm}/{@link #yMm} — СОБСТВЕННАЯ позиция на канвасе Сетевого
 * менеджера, независимая от {@code SchemaNode#getX()}/{@code #getY()} —
 * топология сети не обязана совпадать с раскладкой физической схемы.
 *
 * <p>{@link #id} — стабильный id ЭТОГО размещения (не источника!), нужен как
 * якорь для {@link NetworkLink#getFromDeviceId()}/{@code getToDeviceId()} —
 * связи ссылаются на конкретное размещение в конкретной сети, не на
 * {@code linkedSchemaNodeId}/{@code deviceTypeId} напрямую (два разных
 * размещения могут указывать на один и тот же тип/узел).
 *
 * <p>{@link #portCount} — количество сетевых портов ИМЕННО этого размещения
 * (не библиотечного типа) — редактируется в {@code ui.NetworkDeviceParamsDialog}.
 * Не берётся из {@code NetworkDeviceType} автоматически: у связанных
 * ({@code linkedSchemaNodeId}) устройств источника для этого числа нет вовсе
 * (SchemaNode не моделирует сетевые порты), а у каталожных реальная
 * комплектация в поле иногда отличается от паспортной — то же решение, что
 * `carriesCabinets`/`cabinetsPerCase` у CaseType остаются per-instance, а не
 * калькулируются заново на каждый показ. */
public class NetworkDevicePlacement {

    private String id = UUID.randomUUID().toString();
    private String linkedSchemaNodeId;
    private String deviceTypeId;
    private int portCount = 4;
    private String customLabel = "";
    private String ipAddress = "";
    private String subnetMask = "";
    private String gateway = "";
    /** Пусто — нет веб-интерфейса/не задан. */
    private String webInterfaceUrl = "";
    /** Явный флаг "у этого устройства есть веб-интерфейс, им можно управлять" —
     *  ОТДЕЛЬНО от {@link #webInterfaceUrl}, по запросу пользователя. Причина
     *  разделения: {@code ui.NetworkDeviceParamsDialog} автоматически
     *  подставляет {@code http://<ip>} в поле URL при заполнении IP, даже для
     *  устройств, у которых веб-интерфейса физически нет (простой конвертер/
     *  сплиттер) — непустой URL сам по себе не означает "устройством реально
     *  можно управлять через браузер". ПКМ-пункт «Открыть веб-интерфейс»
     *  (см. {@code ui.NetworkCanvasPanel#showDeviceMenu}) активен только когда
     *  ОБА условия верны: этот флаг {@code true} И URL не пуст. */
    private boolean hasWebInterface;
    private double xMm;
    private double yMm;
    private String note = "";

    public NetworkDevicePlacement() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPortCount() {
        return portCount;
    }

    public void setPortCount(int portCount) {
        this.portCount = portCount;
    }

    public String getLinkedSchemaNodeId() {
        return linkedSchemaNodeId;
    }

    public void setLinkedSchemaNodeId(String linkedSchemaNodeId) {
        this.linkedSchemaNodeId = linkedSchemaNodeId;
    }

    public String getDeviceTypeId() {
        return deviceTypeId;
    }

    public void setDeviceTypeId(String deviceTypeId) {
        this.deviceTypeId = deviceTypeId;
    }

    public String getCustomLabel() {
        return customLabel;
    }

    public void setCustomLabel(String customLabel) {
        this.customLabel = customLabel;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getWebInterfaceUrl() {
        return webInterfaceUrl;
    }

    public void setWebInterfaceUrl(String webInterfaceUrl) {
        this.webInterfaceUrl = webInterfaceUrl;
    }

    public boolean isHasWebInterface() {
        return hasWebInterface;
    }

    public void setHasWebInterface(boolean hasWebInterface) {
        this.hasWebInterface = hasWebInterface;
    }

    public double getXMm() {
        return xMm;
    }

    public void setXMm(double xMm) {
        this.xMm = xMm;
    }

    public double getYMm() {
        return yMm;
    }

    public void setYMm(double yMm) {
        this.yMm = yMm;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
