package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
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
}
