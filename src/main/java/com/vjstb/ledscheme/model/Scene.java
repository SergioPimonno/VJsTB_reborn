package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
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
}
