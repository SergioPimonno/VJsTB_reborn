package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Одно устройство на поле Сетевого менеджера (см. {@code
 * ui.NetworkCanvasPanel}) — персистентная форма {@code
 * ui.NetworkCanvasPanel.PlacedDevice}. Ссылается на источник имени/типа ПО
 * ID, не embed'ит объект — тот же приём, что и у {@code
 * VehicleLoadPlacement#caseTypeId}/{@code Screen#cabinetTypeId}, чтобы не
 * плодить устаревающие копии внутри сохранённого проекта.
 *
 * <p><b>Живёт на уровне {@code NetworkManagerPlan}, НЕ внутри одной {@link
 * Network}</b> (Round 8, запрос пользователя: "один блок может добавляться в
 * поле 1 раз, но может принадлежать разным сеткам при условии, что у него
 * больше 1 порта и соответственно адреса") — раньше размещение физически
 * лежало в {@code Network.devices}, поэтому одно физическое устройство,
 * входящее в две сети, приходилось заводить на поле ДВАЖДЫ, разными блоками
 * с разными id (нельзя было ни соединить их как один узел, ни увидеть, что
 * это один и тот же ящик). Теперь блок один, а членство в сетях — список
 * {@link #attachments} ({@link NetworkAttachment}, там же живут адрес/маска/
 * шлюз — РАЗНЫЕ для разных сетей одного устройства).
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
 * <p>{@link #ethernetPortCount}/{@link #opticalPortCount} — число портов ИМЕННО
 * этого размещения, используются ТОЛЬКО для СВЯЗАННЫХ устройств ({@link
 * #linkedSchemaNodeId} задан) — у тех источника для этого числа нет вовсе
 * (SchemaNode не моделирует сетевые порты управления, а видео-порты
 * контроллера — другое оборудование, см. их javadoc). У КАТАЛОЖНЫХ
 * устройств ({@link #deviceTypeId} задан) число портов резолвится ЖИВЬЁМ из
 * {@code NetworkDeviceType} — эти поля для них не используются вовсе. */
public class NetworkDevicePlacement {

    private String id = UUID.randomUUID().toString();
    private String linkedSchemaNodeId;
    private String deviceTypeId;
    /** Число СЕТЕВЫХ (RJ45, управление/мониторинг) портов — для КАТАЛОЖНЫХ
     *  устройств ({@link #deviceTypeId} задан) не используется вовсе,
     *  резолвится ЖИВЬЁМ из {@code NetworkDeviceType#getEthernetPortCount()};
     *  используется только для СВЯЗАННЫХ ({@link #linkedSchemaNodeId}), у
     *  которых каталожного источника нет. <b>НЕ порты вывода видео на экран
     *  контроллера</b> (явное уточнение пользователя: "в контроллерах
     *  ethernet порты для экранов и сетевой порт — это разные порты") — для
     *  контроллера, добавленного из схемы, это число портов управления
     *  (обычно 1, редко 2 для резервирования), никак не связанное с числом
     *  портов расключения LED-кабинетов. Имя JSON-ключа сохранено ("portCount")
     *  для обратной совместимости — Java-имя переименовано только для
     *  симметрии с {@link #opticalPortCount} и {@code NetworkDeviceType}. */
    @com.fasterxml.jackson.annotation.JsonProperty("portCount")
    private int ethernetPortCount = 4;
    /** Число ОПТИЧЕСКИХ портов — та же логика, что {@link #ethernetPortCount},
     *  только для fiber-аплинков; 0 по умолчанию. Новое поле — у устройств,
     *  сохранённых раньше, остаётся 0. */
    private int opticalPortCount;
    private String customLabel = "";
    /** {@code true} — этот блок представляет ТЕКУЩЕЕ устройство (машину, на
     *  которой запущено приложение), запрос пользователя: "в поле должен
     *  автоматически формироваться блок Admin Laptop, представляющий текущее
     *  устройство и с которого и будет осуществляться пинг" (пинг и так
     *  всегда идёт с этой машины — {@code service.NetworkPingService}
     *  запускает системный {@code ping} локально — блок нужен для
     *  ВИЗУАЛЬНОГО представления этого факта в топологии, не как отдельный
     *  канал запуска). Создаётся один раз кнопкой «+ Мой компьютер» в
     *  {@code ui.NetworkManagerPanel}, IP синхронизируется живьём (см.
     *  {@code ui.NetworkCanvasPanel#refreshAdminLaptopAddresses}, вызывается
     *  на каждое обновление панели) — единственное отличие от обычного
     *  устройства, во всём остальном (перетаскивание, подключение к
     *  дополнительным сетям, удаление) ведёт себя как любой другой блок. */
    private boolean adminLaptop;
    /** Подключения этого блока к сетям — см. {@link NetworkAttachment}. Адрес/
     *  маска/шлюз живут ТАМ, а не здесь: одно физическое устройство может
     *  сидеть в нескольких сетях с разными адресами (запрос пользователя, см.
     *  javadoc {@link NetworkAttachment}). Пустой список — блок на поле есть, но
     *  ни в одну сеть пока не включён (валидное промежуточное состояние:
     *  например, устройство найдено сканом и ещё не распределено). */
    private List<NetworkAttachment> attachments = new ArrayList<>();
    /** Размер блока на канвасе — растягивается мышью за угол, как узлы общей
     *  схемы ({@code SchemaNode#getWidth()}/{@code #getHeight()}, запрос
     *  пользователя: «блоки должны мочь растягиваться (как в общей схеме)»).
     *  Значения по умолчанию совпадают с прежними константами канваса
     *  ({@code NetworkCanvasPanel.DEVICE_W}/{@code DEVICE_H}) — у проектов,
     *  сохранённых до появления этих полей, раскладка не меняется. */
    private double width = 130;
    private double height = 56;
    /** @deprecated Легаси-поле проектов, сохранённых до введения {@link
     *  NetworkAttachment} — читается только миграцией ({@code
     *  store.WorkspaceStore#migrateLegacyNetworkPlans}), которая переносит
     *  значение в подключение к сети и очищает это поле. Новый код адресует
     *  {@code attachment.getIpAddress()}. */
    private String ipAddress = "";
    /** @deprecated См. {@link #ipAddress}. */
    private String subnetMask = "";
    /** @deprecated См. {@link #ipAddress}. */
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

    public int getEthernetPortCount() {
        return ethernetPortCount;
    }

    public void setEthernetPortCount(int ethernetPortCount) {
        this.ethernetPortCount = ethernetPortCount;
    }

    public int getOpticalPortCount() {
        return opticalPortCount;
    }

    public void setOpticalPortCount(int opticalPortCount) {
        this.opticalPortCount = opticalPortCount;
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

    public boolean isAdminLaptop() {
        return adminLaptop;
    }

    public void setAdminLaptop(boolean adminLaptop) {
        this.adminLaptop = adminLaptop;
    }

    /** @deprecated Только для миграции ({@code
     *  store.WorkspaceStore#migrateLegacyNetworkPlans}) — новый код читает
     *  {@code NetworkAttachment#getIpAddress()}. {@code @JsonProperty}
     *  сохраняет старое имя JSON-поля ("ipAddress"), несмотря на переименование
     *  метода — иначе Jackson перестал бы находить это поле в файлах,
     *  сохранённых до введения {@link NetworkAttachment}, и миграции было бы
     *  нечего переносить. */
    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("ipAddress")
    public String getLegacyIpAddress() {
        return ipAddress;
    }

    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("ipAddress")
    public void setLegacyIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /** @deprecated См. {@link #getLegacyIpAddress()}. */
    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("subnetMask")
    public String getLegacySubnetMask() {
        return subnetMask;
    }

    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("subnetMask")
    public void setLegacySubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    /** @deprecated См. {@link #getLegacyIpAddress()}. */
    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("gateway")
    public String getLegacyGateway() {
        return gateway;
    }

    @Deprecated
    @com.fasterxml.jackson.annotation.JsonProperty("gateway")
    public void setLegacyGateway(String gateway) {
        this.gateway = gateway;
    }

    public List<NetworkAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<NetworkAttachment> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    /** Подключение этого устройства к конкретной сети — {@code null}, если
     *  устройство в эту сеть не входит. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public NetworkAttachment attachmentFor(String networkId) {
        if (networkId == null) {
            return null;
        }
        for (NetworkAttachment a : attachments) {
            if (networkId.equals(a.getNetworkId())) {
                return a;
            }
        }
        return null;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
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
