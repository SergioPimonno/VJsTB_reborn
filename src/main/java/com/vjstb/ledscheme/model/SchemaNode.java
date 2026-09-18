package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Узел общей схемы площадки (питания или сигнала): оборудование (источник,
 * распределение, конвертер, медиасервер, контроллер) либо ссылка на реальный
 * экран сцены — тогда имя/статистика в узле берутся из самого экрана (привязка
 * схемы к цепочкам, а не просто рисунок).
 */
public class SchemaNode {

    private String id = UUID.randomUUID().toString();
    private SchemaMode mode = SchemaMode.POWER;
    private SchemaNodeType type = SchemaNodeType.CUSTOM;
    private String label = "";
    private double x;
    private double y;
    private double width = 175;
    private double height = 56;
    /** Для type == SCREEN: id реального экрана сцены, с которым связан узел. */
    private String screenRefId;
    /** Для type == CONTROLLER, только когда узел заведён автозаполнением (см.
     *  AppModel#autoPopulateSchema): id реального ControllerInstance сцены, чью
     *  комплектацию карт зеркалит этот узел — по нему автозаполнение находит узел
     *  повторно (не создаёт дублей при повторном переходе на схему) и определяет,
     *  к какой группе портов автоматически подвести связь от гнезда кабинета.
     *  null — узел добавлен вручную, с реальным контроллером не связан. */
    private String controllerInstanceRefId;
    /** Комплектация карт ввода/вывода (медиасерверы/видеопроцессоры вроде Barco E2,
     *  PixelHue Q8): у type == SERVER/CONTROLLER задаёт реальное число видео I/O. */
    private List<SchemaCard> cards = new ArrayList<>();
    /** Разъёмы питания узла (щиты/дистрибьюторы и т.п. в схеме ПИТАНИЯ): в отличие
     *  от {@link #cards} тут нет группировки по картам — просто список «тип разъёма
     *  (PowerCon/CEE/Schuko…) + направление + количество», т.к. силовое оборудование
     *  не имеет сменных видеокарт. */
    private List<CardPort> powerConnectors = new ArrayList<>();
    /** Запас (%, 100 = без запаса) для проверки суммарной нагрузки этого силового
     *  узла (Task #86/#87) — переопределяет {@link com.vjstb.ledscheme.service.PowerCalc#DEFAULT_DERATING_PERCENT}
     *  для узлов с нетиповым режимом эксплуатации (например, проходной блок-разветвитель
     *  без собственного запаса, в отличие от вводного щита). null — берётся значение
     *  по умолчанию. */
    private Double loadDeratingPercent;
    /** true — узел не оборудование, а АВТО-блок легенды сигнальных портов (см.
     *  AppModel#signalPortLegendLines): вместо обычного содержимого рисуется таблица
     *  "экран — основной контроллер/порты / резервный", пересчитываемая на каждой
     *  отрисовке по факту текущих сигнальных цепочек сцены, а не хранимая в узле.
     *  Только для type == CUSTOM — отдельный SchemaNodeType под это заводить не стали,
     *  это чисто клиентская фишка холста общей схемы, незачем тянуть правку в
     *  ledscheme-model (SchemaNode, в отличие от SchemaNodeType, туда не вынесен). */
    private boolean autoPortLegend = false;
    /** true — узел не оборудование, а АВТО-блок легенды линий (docs/schema-ports-
     *  rework/PLAN.md, задача T5.4) — таблица "роль → цвет" (сигнал) или "номинал →
     *  цвет" (питание) по факту цветов, реально используемых связями текущей схемы
     *  (см. {@code AppModel#lineLegendRoles}/{@code lineLegendPowerNominals}),
     *  пересчитываемая на каждой отрисовке, а не хранимая в узле — как и {@link
     *  #autoPortLegend}, только для type == CUSTOM, тоже чисто клиентская фишка
     *  холста (не вынесена в ledscheme-model). */
    private boolean autoLineLegend = false;
    /** Ориентация потока узла (docs/schema-ports-rework/PLAN.md, задача T1.2) —
     *  {@code null} значит «взять умолчание профиля по режиму схемы» (см. настройки
     *  персонализации {@code signal/powerDefaultOrientation}), а не жёстко {@link
     *  NodeOrientation#RIGHT} — так смена умолчания профиля продолжает менять вид
     *  уже существующих узлов, у которых ориентацию не задавали вручную (как раньше
     *  вела себя галочка «Гнёзда у верхнего/нижнего края блока»). Задаётся вручную
     *  через меню узла «Ориентация»/Ctrl+R (см. PLAN.md, задача T3.3). */
    private NodeOrientation orientation;
    /** Ручные переопределения раскладки отдельных групп разъёмов этого узла (сторона,
     *  порядок, свёрнута ли, роль/транзит в этом проекте) — см. {@link PortPlacement}.
     *  Пусто по умолчанию: раскладка целиком автоматическая (роль → сторона по
     *  таблице, свёртка по занятости). Порядок элементов списка не имеет значения —
     *  ключ каждой записи это {@link PortPlacement#getPortId()}. */
    private List<PortPlacement> portPlacements = new ArrayList<>();
    /** true — на блоке показываются только гнёзда/карты, к которым подведена хотя бы
     *  одна связь (PLAN.md §2.4, «только задействованные»); незадействованные группы/
     *  карты сворачиваются в одну итоговую строку "ещё N карт". Ёмкость и проверки
     *  по-прежнему учитывают ВСЕ гнёзда, скрытые — только визуально. */
    private boolean onlyUsedPorts = false;
    /** Для узла, добавленного из библиотеки сетевого оборудования (см.
     *  {@code NetworkDeviceType}, PLAN.md D11/задача T3.4) — id этого типа: гнёзда
     *  узла ("Ethernet"/"Fiber", роль {@link InterfaceRole#NETWORK}, двусторонние)
     *  заводятся и обновляются по факту {@code ethernetPortCount}/{@code
     *  opticalPortCount} этого типа. {@code null} — обычный узел, не связан с
     *  библиотекой сетевого оборудования (в т.ч. ЛЮБОЙ узел из старого проекта). */
    private String networkDeviceTypeId;

    public SchemaNode() {
    }

    public SchemaNode(SchemaMode mode, SchemaNodeType type, String label, double x, double y, String screenRefId) {
        this.mode = mode;
        this.type = type;
        this.label = label;
        this.x = x;
        this.y = y;
        this.screenRefId = screenRefId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SchemaMode getMode() {
        return mode;
    }

    public void setMode(SchemaMode mode) {
        this.mode = mode;
    }

    public SchemaNodeType getType() {
        return type;
    }

    public void setType(SchemaNodeType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
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

    public String getScreenRefId() {
        return screenRefId;
    }

    public void setScreenRefId(String screenRefId) {
        this.screenRefId = screenRefId;
    }

    public String getControllerInstanceRefId() {
        return controllerInstanceRefId;
    }

    public void setControllerInstanceRefId(String controllerInstanceRefId) {
        this.controllerInstanceRefId = controllerInstanceRefId;
    }

    public List<SchemaCard> getCards() {
        return cards;
    }

    public void setCards(List<SchemaCard> cards) {
        this.cards = cards;
    }

    public List<CardPort> getPowerConnectors() {
        return powerConnectors;
    }

    public void setPowerConnectors(List<CardPort> powerConnectors) {
        this.powerConnectors = powerConnectors;
    }

    public Double getLoadDeratingPercent() {
        return loadDeratingPercent;
    }

    public void setLoadDeratingPercent(Double loadDeratingPercent) {
        this.loadDeratingPercent = loadDeratingPercent;
    }

    public boolean isAutoPortLegend() {
        return autoPortLegend;
    }

    public void setAutoPortLegend(boolean autoPortLegend) {
        this.autoPortLegend = autoPortLegend;
    }

    public boolean isAutoLineLegend() {
        return autoLineLegend;
    }

    public void setAutoLineLegend(boolean autoLineLegend) {
        this.autoLineLegend = autoLineLegend;
    }

    public NodeOrientation getOrientation() {
        return orientation;
    }

    public void setOrientation(NodeOrientation orientation) {
        this.orientation = orientation;
    }

    public List<PortPlacement> getPortPlacements() {
        return portPlacements;
    }

    public void setPortPlacements(List<PortPlacement> portPlacements) {
        this.portPlacements = portPlacements != null ? portPlacements : new ArrayList<>();
    }

    /** Раскладка конкретной группы разъёмов ({@link CardPort#getId()}) этого узла —
     *  {@code null}, если группа не переопределена (раскладка полностью автоматическая). */
    public PortPlacement findPortPlacement(String portId) {
        for (PortPlacement p : portPlacements) {
            if (p.getPortId().equals(portId)) {
                return p;
            }
        }
        return null;
    }

    public boolean isOnlyUsedPorts() {
        return onlyUsedPorts;
    }

    public void setOnlyUsedPorts(boolean onlyUsedPorts) {
        this.onlyUsedPorts = onlyUsedPorts;
    }

    public String getNetworkDeviceTypeId() {
        return networkDeviceTypeId;
    }

    public void setNetworkDeviceTypeId(String networkDeviceTypeId) {
        this.networkDeviceTypeId = networkDeviceTypeId;
    }

    public SchemaNode copy() {
        SchemaNode n = new SchemaNode();
        n.id = id;
        n.mode = mode;
        n.type = type;
        n.label = label;
        n.x = x;
        n.y = y;
        n.width = width;
        n.height = height;
        n.screenRefId = screenRefId;
        n.controllerInstanceRefId = controllerInstanceRefId;
        n.cards = new ArrayList<>();
        for (SchemaCard c : cards) {
            n.cards.add(c.copy());
        }
        n.powerConnectors = new ArrayList<>();
        for (CardPort p : powerConnectors) {
            n.powerConnectors.add(p.copy());
        }
        n.loadDeratingPercent = loadDeratingPercent;
        n.autoPortLegend = autoPortLegend;
        n.autoLineLegend = autoLineLegend;
        n.orientation = orientation;
        n.portPlacements = new ArrayList<>();
        for (PortPlacement p : portPlacements) {
            n.portPlacements.add(p.copy());
        }
        n.onlyUsedPorts = onlyUsedPorts;
        n.networkDeviceTypeId = networkDeviceTypeId;
        return n;
    }
}
