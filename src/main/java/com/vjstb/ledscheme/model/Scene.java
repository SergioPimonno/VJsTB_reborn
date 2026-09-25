package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Scene {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private int orderIndex;
    private List<Screen> screens = new ArrayList<>();
    /** Узлы/связи общей схемы площадки (питание и сигнал вместе, различаются полем mode). */
    private List<SchemaNode> schemaNodes = new ArrayList<>();
    private List<SchemaEdge> schemaEdges = new ArrayList<>();
    /** Канвасы компоновки контента (выходные кадры сигнала) для этой сцены. */
    private List<ContentCanvas> canvases = new ArrayList<>();
    /** Цепочки питания/сигнала — хранятся на уровне СЦЕНЫ, а не отдельного экрана
     *  (см. независимый менеджер цепочек, Task #78): физически цепочка может
     *  затрагивать кабинеты НЕСКОЛЬКИХ экранов одной сцены (общий силовой ввод/
     *  сигнальный даунлинк на смежный экран) — привязка к одному конкретному
     *  экрану раньше была источником повторяющихся багов (чья это цепочка, где
     *  она "живёт", почему на другом экране её не видно/нельзя удалить). Старые
     *  поля powerChains/signalChains на Screen оставлены только для чтения данных
     *  из ранее сохранённых файлов проектов — миграция переносит их сюда один раз
     *  при загрузке (см. WorkspaceStore). */
    private List<PowerChain> powerChains = new ArrayList<>();
    private List<SignalChain> signalChains = new ArrayList<>();
    /** Проекторы сцены — независимый список, как canvases/powerChains, БЕЗ привязки
     *  к графу общей схемы (UI-калькулятор для их создания выпилен, см.
     *  {@code AppModel#addProjector}/{@code OutputStagePanel#addProjectorSheet} —
     *  модель и её отображение в спецификации оставлены). */
    private List<ProjectorInstance> projectors = new ArrayList<>();
    /** Раскладка загрузки машин(ы) кофрами для этой сцены (см.
     *  {@code ui.VehicleLoadVisualizerDialog}) — {@code null}, пока визуализатор
     *  ни разу не открывали для этой сцены. Персистится и синхронизируется с
     *  облаком так же, как остальные данные сцены (см. VEHICLE_CALC_NOTES.md). */
    private VehicleLoadPlan vehicleLoadPlan;
    /** Строки калькулятора транспорта (тип кофра → количество) для этой сцены —
     *  запрос пользователя: при повторном открытии {@code
     *  ui.VehicleCalculatorDialog} количество кофров, введённое в прошлый раз,
     *  должно подтягиваться, а не начинаться с пустой таблицы. Ключ — {@code
     *  CaseType#getId()} (ссылка по id, как {@link VehicleLoadPlacement
     *  #getCaseTypeId()}, не embed объекта). Отдельно от {@link
     *  #vehicleLoadPlan} (тот хранит РАСКЛАДКУ в кузове, этот — исходные
     *  количества, из которых раскладка считалась) — они могут разойтись,
     *  если пользователь поменял строки в калькуляторе, но ещё не переоткрыл
     *  визуализатор. */
    private Map<String, Integer> vehicleCaseCounts = new LinkedHashMap<>();
    /** Сетевой менеджер этой сцены (см. {@code ui.NetworkManagerPanel}, вкладка
     *  раздела «Сигнал») — {@code null}, пока менеджер ни разу не открывали для
     *  этой сцены. Хранит НЕСКОЛЬКО именованных сетей ({@link
     *  NetworkManagerPlan#getNetworks()}), каждая со своей раскладкой устройств —
     *  позиции {@code xMm}/{@code yMm} внутри устройства СВОИ, не копия координат
     *  связанного {@code SchemaNode} на общей схеме (топология сети и физическая
     *  схема раскладываются независимо). */
    private NetworkManagerPlan networkManagerPlan;
    /** Стартовые значения для НОВЫХ экранов этой сцены (кнопка «Параметры по
     *  умолчанию» в прериге, см. {@link ScreenDefaults}) — {@code null}, пока
     *  пользователь их ни разу не задавал (тогда действуют обычные хардкод-
     *  дефолты {@link Screen}). Не затрагивает уже существующие экраны. */
    private ScreenDefaults screenDefaults;
    /** Группы экранов этой сцены в дереве навигации (см. {@link ScreenGroup}) —
     *  старые проекты без поля десериализуются с пустым списком. */
    private List<ScreenGroup> screenGroups = new ArrayList<>();

    public Scene() {
    }

    public Scene(String name) {
        this.name = name;
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<Screen> getScreens() {
        return screens;
    }

    public void setScreens(List<Screen> screens) {
        this.screens = screens;
    }

    public List<SchemaNode> getSchemaNodes() {
        return schemaNodes;
    }

    public void setSchemaNodes(List<SchemaNode> schemaNodes) {
        this.schemaNodes = schemaNodes;
    }

    public List<SchemaEdge> getSchemaEdges() {
        return schemaEdges;
    }

    public void setSchemaEdges(List<SchemaEdge> schemaEdges) {
        this.schemaEdges = schemaEdges;
    }

    public List<ContentCanvas> getCanvases() {
        return canvases;
    }

    public void setCanvases(List<ContentCanvas> canvases) {
        this.canvases = canvases;
    }

    public List<PowerChain> getPowerChains() {
        return powerChains;
    }

    public void setPowerChains(List<PowerChain> powerChains) {
        this.powerChains = powerChains;
    }

    public List<SignalChain> getSignalChains() {
        return signalChains;
    }

    public void setSignalChains(List<SignalChain> signalChains) {
        this.signalChains = signalChains;
    }

    public List<ProjectorInstance> getProjectors() {
        return projectors;
    }

    public void setProjectors(List<ProjectorInstance> projectors) {
        this.projectors = projectors;
    }

    public VehicleLoadPlan getVehicleLoadPlan() {
        return vehicleLoadPlan;
    }

    public void setVehicleLoadPlan(VehicleLoadPlan vehicleLoadPlan) {
        this.vehicleLoadPlan = vehicleLoadPlan;
    }

    public Map<String, Integer> getVehicleCaseCounts() {
        return vehicleCaseCounts;
    }

    public void setVehicleCaseCounts(Map<String, Integer> vehicleCaseCounts) {
        this.vehicleCaseCounts = vehicleCaseCounts;
    }

    public NetworkManagerPlan getNetworkManagerPlan() {
        return networkManagerPlan;
    }

    public void setNetworkManagerPlan(NetworkManagerPlan networkManagerPlan) {
        this.networkManagerPlan = networkManagerPlan;
    }

    public ScreenDefaults getScreenDefaults() {
        return screenDefaults;
    }

    public void setScreenDefaults(ScreenDefaults screenDefaults) {
        this.screenDefaults = screenDefaults;
    }

    public List<ScreenGroup> getScreenGroups() {
        return screenGroups;
    }

    public void setScreenGroups(List<ScreenGroup> screenGroups) {
        this.screenGroups = screenGroups != null ? screenGroups : new ArrayList<>();
    }

    /** Группа с таким id или {@code null} (в том числе для {@code null}-id). */
    @JsonIgnore
    public ScreenGroup groupById(String groupId) {
        if (groupId == null) {
            return null;
        }
        for (ScreenGroup g : screenGroups) {
            if (groupId.equals(g.getId())) {
                return g;
            }
        }
        return null;
    }

    /** Группа, в которую входит экран, или {@code null} — экран вне групп либо
     *  ссылается на несуществующую группу (битый файл считается «без группы»). */
    @JsonIgnore
    public ScreenGroup groupOf(Screen screen) {
        return screen == null ? null : groupById(screen.getGroupId());
    }

    /** Экраны группы в порядке списка экранов сцены. */
    @JsonIgnore
    public List<Screen> screensOf(ScreenGroup group) {
        List<Screen> out = new ArrayList<>();
        if (group == null) {
            return out;
        }
        for (Screen s : screens) {
            if (group.getId().equals(s.getGroupId())) {
                out.add(s);
            }
        }
        return out;
    }
}
