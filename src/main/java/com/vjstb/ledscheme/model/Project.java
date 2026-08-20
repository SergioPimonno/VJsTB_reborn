package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Project {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String description;
    private long createdAt = System.currentTimeMillis();
    private long updatedAt = System.currentTimeMillis();
    private List<Scene> scenes = new ArrayList<>();
    /** Привязка к общему командному облачному хранилищу (см. sync.ProjectArchiveClient
     *  / ui.CloudProjectsDialog) — null, пока проект не сохранён в облако ни разу.
     *  cloudRevision — ревизия, на которой основана ЭТА локальная копия (обязательна
     *  для следующего сохранения: сервер отклонит его, если кто-то уже сохранил более
     *  новую ревизию — "принудительный" контроль версий, без тихой перезаписи). */
    private String cloudId;
    private Integer cloudRevision;
    /** Строки калькулятора транспорта ({@code ui.VehicleCalculatorDialog}) для
     *  области расчёта "Весь проект" — см. {@link Scene#getVehicleCaseCounts()}
     *  за общую мотивацию (то же самое, но для другой области расчёта; у
     *  каждой области своё хранилище — они считаются по разным наборам сцен
     *  и не должны затирать значения друг друга). */
    private Map<String, Integer> vehicleCaseCountsProject = new LinkedHashMap<>();
    /** То же самое для области "Несколько сцен…" — вместе с {@link
     *  #vehicleCaseCustomSceneIds} (какой именно набор сцен был выбран в
     *  прошлый раз), иначе восстановленные количества было бы не с чем
     *  сверить при повторном открытии. */
    private Map<String, Integer> vehicleCaseCountsCustom = new LinkedHashMap<>();
    private List<String> vehicleCaseCustomSceneIds = new ArrayList<>();
    /** Какая область расчёта калькулятора транспорта использовалась последней
     *  — имя константы {@code ui.VehicleCalculatorDialog.CalcScope}
     *  ("SCENE"/"PROJECT"/"CUSTOM") — восстанавливает выбор в комбобоксе при
     *  повторном открытии диалога вместо того, чтобы всегда сбрасывать его на
     *  "Текущая сцена". {@code null} — калькулятор для этого проекта ещё ни
     *  разу не открывали. */
    private String vehicleCaseLastScope;

    public Project() {
    }

    public Project(String name) {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = scenes;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public Integer getCloudRevision() {
        return cloudRevision;
    }

    public void setCloudRevision(Integer cloudRevision) {
        this.cloudRevision = cloudRevision;
    }

    public Map<String, Integer> getVehicleCaseCountsProject() {
        return vehicleCaseCountsProject;
    }

    public void setVehicleCaseCountsProject(Map<String, Integer> vehicleCaseCountsProject) {
        this.vehicleCaseCountsProject = vehicleCaseCountsProject;
    }

    public Map<String, Integer> getVehicleCaseCountsCustom() {
        return vehicleCaseCountsCustom;
    }

    public void setVehicleCaseCountsCustom(Map<String, Integer> vehicleCaseCountsCustom) {
        this.vehicleCaseCountsCustom = vehicleCaseCountsCustom;
    }

    public List<String> getVehicleCaseCustomSceneIds() {
        return vehicleCaseCustomSceneIds;
    }

    public void setVehicleCaseCustomSceneIds(List<String> vehicleCaseCustomSceneIds) {
        this.vehicleCaseCustomSceneIds = vehicleCaseCustomSceneIds;
    }

    public String getVehicleCaseLastScope() {
        return vehicleCaseLastScope;
    }

    public void setVehicleCaseLastScope(String vehicleCaseLastScope) {
        this.vehicleCaseLastScope = vehicleCaseLastScope;
    }
}
