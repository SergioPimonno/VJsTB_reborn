package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** Узлы/связи общей схемы площадки (питание и сигнал вместе, различаются полем mode;
     *  с 2026-09-30 — ещё и листом {@code sheetId}, см. {@link #schemaSheets}). Списки
     *  намеренно остаются ПЛОСКИМИ, а не вложенными в листы: админ-консоль
     *  ({@code ProjectSummary}) читает {@code scene.schemaNodes} из JSON напрямую, а
     *  старые клиенты должны открывать новый файл (решение D5). */
    private List<SchemaNode> schemaNodes = new ArrayList<>();
    private List<SchemaEdge> schemaEdges = new ArrayList<>();
    /** Листы (блок-схемы) общей схемы — запрос пользователя 2026-09-30 «несколько
     *  блок-схем на сцену» (docs/masks-and-schema-sheets/PLAN.md, пункт 8). Пусто у
     *  проекта, сохранённого до листов (или пересохранённого старым клиентом) —
     *  тогда {@code service.SchemaSheetMigration} заводит по листу на режим при
     *  загрузке. */
    private List<SchemaSheet> schemaSheets = new ArrayList<>();
    /** true — списки схемы только что пришли из JSON и координаты в них ещё
     *  «файловые» (со сдвигом листов по X, решение D5), см. {@link
     *  #applyPendingStorageOffsets()}. Не сериализуется (нет аксессоров). */
    private boolean storageOffsetsPending;
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

    /** ЖИВОЙ список узлов схемы (все листы, оба режима) с ЛОКАЛЬНЫМИ координатами
     *  листов. {@link JsonIgnore} — в JSON ключ {@code "schemaNodes"} пишут/читают
     *  {@link #storedSchemaNodes()}/{@link #storeSchemaNodes(List)} (сдвиг D5). */
    @JsonIgnore
    public List<SchemaNode> getSchemaNodes() {
        applyPendingStorageOffsets();
        return schemaNodes;
    }

    @JsonIgnore
    public void setSchemaNodes(List<SchemaNode> schemaNodes) {
        applyPendingStorageOffsets();
        this.schemaNodes = schemaNodes;
    }

    /** См. {@link #getSchemaNodes()}. */
    @JsonIgnore
    public List<SchemaEdge> getSchemaEdges() {
        applyPendingStorageOffsets();
        return schemaEdges;
    }

    @JsonIgnore
    public void setSchemaEdges(List<SchemaEdge> schemaEdges) {
        applyPendingStorageOffsets();
        this.schemaEdges = schemaEdges;
    }

    /** ЖИВОЙ список листов (все режимы, в порядке заведения — упорядочивать по
     *  {@link SchemaSheet#getOrderIndex()} в пределах режима, см. {@code
     *  AppModel#schemaSheets}). У листов в памяти {@code storageOffsetX == 0}. */
    @JsonIgnore
    public List<SchemaSheet> getSchemaSheets() {
        applyPendingStorageOffsets();
        return schemaSheets;
    }

    @JsonIgnore
    public void setSchemaSheets(List<SchemaSheet> schemaSheets) {
        applyPendingStorageOffsets();
        this.schemaSheets = schemaSheets != null ? schemaSheets : new ArrayList<>();
    }

    // ---- формат файла: раскладка листов схемы по X (решение D5, запрос 2026-09-30) ----
    //
    // Зачем: старый клиент не знает про листы и рисует все узлы режима на одном
    // холсте. Координаты каждого листа в памяти локальные (от нуля) — записанные как
    // есть, листы легли бы у старого клиента стопкой друг на друга. Поэтому в ФАЙЛЕ
    // листы одного режима разложены по X рядом (offset(первый) = 0, offset(k) =
    // offset(k-1) + ширина(k-1) + STORAGE_SHEET_GAP), а сам сдвиг записан в листе
    // (SchemaSheet.storageOffsetX), чтобы новый клиент мог его вычесть.
    //
    // Почему на уровне Jackson в самой Scene, а не в WorkspaceStore: Scene
    // сериализуется многими путями (workspace.json, экспорт/импорт проекта, облако,
    // LocalArchiveStore, ClientBackup, история версий — у каждого СВОЙ ObjectMapper).
    // Аннотированные аксессоры ниже покрывают их все разом, без регистрации модуля в
    // каждом маппере.
    //
    // Запись: storedSchemaNodes/Edges/Sheets отдают СДВИНУТЫЕ КОПИИ (живые объекты не
    // трогаются — сериализация не должна менять модель в памяти); сдвиг считается
    // одной детерминированной функцией computeStorageOffsets() от текущего состояния,
    // поэтому три геттера согласованы между собой в пределах одной записи.
    //
    // Чтение: сеттеры принимают «сырые» файловые значения и лишь поднимают флаг
    // storageOffsetsPending; вычитание выполняется ОДИН раз — при первом обращении к
    // любому живому аксессору (getSchemaNodes/Edges/Sheets и сеттерам). Выбран
    // ленивый флаг, а не явный post-load: явный вызов пришлось бы не забыть во ВСЕХ
    // путях чтения (см. выше), а порядок ключей в JSON (листы до или после узлов)
    // Jackson не гарантирует — к моменту первого обращения все три списка уже на
    // месте при любом порядке. После вычитания storageOffsetX листов обнуляется —
    // повторное применение ничего не меняет. Файл без "schemaSheets" (старый проект
    // или пересохранённый старым клиентом) — сдвигать нечего, координаты берутся как
    // есть, а листы заводит обычная миграция (SchemaSheetMigration).

    /** Зазор между листами одного режима в файле (решение D5), единицы холста схемы. */
    public static final double STORAGE_SHEET_GAP = 400;

    @JsonProperty("schemaNodes")
    private List<SchemaNode> storedSchemaNodes() {
        Map<String, Double> offsets = computeStorageOffsets();
        List<SchemaNode> out = new ArrayList<>(schemaNodes.size());
        for (SchemaNode n : schemaNodes) {
            double dx = offsetOf(offsets, n.getSheetId());
            if (dx == 0) {
                out.add(n);
            } else {
                SchemaNode shifted = n.copy();
                shifted.setX(n.getX() + dx);
                out.add(shifted);
            }
        }
        return out;
    }

    @JsonProperty("schemaNodes")
    private void storeSchemaNodes(List<SchemaNode> raw) {
        this.schemaNodes = raw != null ? raw : new ArrayList<>();
        this.storageOffsetsPending = true;
    }

    @JsonProperty("schemaEdges")
    private List<SchemaEdge> storedSchemaEdges() {
        Map<String, Double> offsets = computeStorageOffsets();
        List<SchemaEdge> out = new ArrayList<>(schemaEdges.size());
        for (SchemaEdge e : schemaEdges) {
            double dx = offsetOf(offsets, e.getSheetId());
            if (dx == 0 || e.getWaypoints().isEmpty()) {
                out.add(e);
            } else {
                SchemaEdge shifted = e.copy();
                for (EdgeWaypoint w : shifted.getWaypoints()) {
                    w.setX(w.getX() + dx);
                }
                out.add(shifted);
            }
        }
        return out;
    }

    @JsonProperty("schemaEdges")
    private void storeSchemaEdges(List<SchemaEdge> raw) {
        this.schemaEdges = raw != null ? raw : new ArrayList<>();
        this.storageOffsetsPending = true;
    }

    @JsonProperty("schemaSheets")
    private List<SchemaSheet> storedSchemaSheets() {
        Map<String, Double> offsets = computeStorageOffsets();
        List<SchemaSheet> out = new ArrayList<>(schemaSheets.size());
        for (SchemaSheet s : schemaSheets) {
            SchemaSheet c = s.copy();
            c.setStorageOffsetX(offsetOf(offsets, s.getId()));
            out.add(c);
        }
        return out;
    }

    @JsonProperty("schemaSheets")
    private void storeSchemaSheets(List<SchemaSheet> raw) {
        this.schemaSheets = raw != null ? raw : new ArrayList<>();
        this.storageOffsetsPending = true;
    }

    private static double offsetOf(Map<String, Double> offsets, String sheetId) {
        if (sheetId == null) {
            return 0;
        }
        Double v = offsets.get(sheetId);
        return v != null ? v : 0;
    }

    /** Снимает файловый сдвиг листов (см. комментарий к секции выше) — ровно один раз
     *  после чтения из JSON; вне десериализации флаг не поднимается и метод ничего не
     *  делает. */
    private void applyPendingStorageOffsets() {
        if (!storageOffsetsPending) {
            return;
        }
        storageOffsetsPending = false;
        Map<String, Double> offsets = new java.util.HashMap<>();
        for (SchemaSheet s : schemaSheets) {
            if (s.getStorageOffsetX() != 0) {
                offsets.put(s.getId(), s.getStorageOffsetX());
            }
            s.setStorageOffsetX(0);
        }
        if (offsets.isEmpty()) {
            return;
        }
        for (SchemaNode n : schemaNodes) {
            double dx = offsetOf(offsets, n.getSheetId());
            if (dx != 0) {
                n.setX(n.getX() - dx);
            }
        }
        for (SchemaEdge e : schemaEdges) {
            double dx = offsetOf(offsets, e.getSheetId());
            if (dx != 0) {
                for (EdgeWaypoint w : e.getWaypoints()) {
                    w.setX(w.getX() - dx);
                }
            }
        }
    }

    /** Сдвиг по X каждого листа в файле (id листа → сдвиг; листы с нулевым сдвигом
     *  могут отсутствовать). По режимам независимо: листы режима по {@code
     *  orderIndex} (при равенстве — по порядку в списке), первый — 0, каждый
     *  следующий — правее правого края предыдущего на {@link #STORAGE_SHEET_GAP}.
     *  Ширина листа — правый край самого дальнего узла ({@code x + width}) или точки
     *  излома его связей, не меньше 0. Если у листа есть содержимое левее нуля
     *  (узел перетащили в отрицательные координаты), лист дополнительно сдвигается
     *  на эту величину — иначе его левый край заехал бы на предыдущий лист. */
    private Map<String, Double> computeStorageOffsets() {
        applyPendingStorageOffsets();
        Map<String, Double> offsets = new java.util.HashMap<>();
        if (schemaSheets.size() < 2) {
            return offsets;
        }
        Map<String, double[]> extents = new java.util.HashMap<>(); // id -> {minLeft, maxRight}
        for (SchemaSheet s : schemaSheets) {
            extents.put(s.getId(), new double[]{0, 0});
        }
        for (SchemaNode n : schemaNodes) {
            double[] ext = n.getSheetId() != null ? extents.get(n.getSheetId()) : null;
            if (ext != null) {
                ext[0] = Math.min(ext[0], n.getX());
                ext[1] = Math.max(ext[1], n.getX() + n.getWidth());
            }
        }
        for (SchemaEdge e : schemaEdges) {
            double[] ext = e.getSheetId() != null ? extents.get(e.getSheetId()) : null;
            if (ext != null) {
                for (EdgeWaypoint w : e.getWaypoints()) {
                    ext[0] = Math.min(ext[0], w.getX());
                    ext[1] = Math.max(ext[1], w.getX());
                }
            }
        }
        Map<SchemaMode, List<SchemaSheet>> byMode = new java.util.EnumMap<>(SchemaMode.class);
        for (SchemaSheet s : schemaSheets) {
            if (s.getMode() != null) {
                byMode.computeIfAbsent(s.getMode(), k -> new ArrayList<>()).add(s);
            }
        }
        for (List<SchemaSheet> sheets : byMode.values()) {
            List<SchemaSheet> ordered = new ArrayList<>(sheets);
            ordered.sort(java.util.Comparator.comparingInt(SchemaSheet::getOrderIndex)); // стабильная
            double prevOffset = 0;
            double prevRight = 0;
            for (int k = 0; k < ordered.size(); k++) {
                double[] ext = extents.get(ordered.get(k).getId());
                double offset = k == 0 ? 0 : prevOffset + prevRight + STORAGE_SHEET_GAP - ext[0];
                if (offset != 0) {
                    offsets.put(ordered.get(k).getId(), offset);
                }
                prevOffset = offset;
                prevRight = ext[1];
            }
        }
        return offsets;
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
