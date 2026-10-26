package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Приёмная карта на экране (docs/receiver-cards/PLAN.md, 4.2): набор МОДУЛЕЙ экрана, каждый из которых включён в карту
 * своим шлейфом (топология «звезда»), плюс порядок подключения и «место» карты.
 *
 * <p>{@link #id} — внутренний идентификатор, НЕ имя: на него ссылаются цепочки расключения сигнала
 * ({@code SignalChain.cabinetInstanceIds} хранит id звеньев), поэтому он не меняется при пересчёте имён К1…КN
 * (имена сквозные по экрану и вычисляются, см. {@code service.UnitNames}). Модуль состоит максимум в одной карте
 * экрана (инвариант держит {@code service.ReceiverCardEditor}).
 */
public class ReceiverCardInstance {

    private String id = UUID.randomUUID().toString();
    /** Тип карты из библиотеки ({@link ReceiverCardType}); тип мог быть удалён/не синхронизирован — тогда проверки
     *  лимитов молча пропускаются, карта остаётся рабочим звеном. */
    private String receiverCardTypeId;
    /** Id модулей ({@link CabinetInstance#getId()}) карты, в порядке чтения (строка за строкой). */
    private List<String> moduleIds = new ArrayList<>();
    /** РУЧНОЙ порядок подключения (id модулей: первый — выход 1…). {@code null} — порядок по шаблону экрана
     *  ({@link Screen#getModuleNumbering()}). Модули карты, которых в списке нет, остаются без номера («?»). */
    private List<String> manualPortOrder;
    /** Модуль, на котором стоит карта («место», решение D11): от него по прямой считается длина шлейфов и в нём
     *  стоит плашка с именем. {@code null} — авто: модуль, ближайший к центру карты. */
    private String hostModuleId;

    public ReceiverCardInstance() {
    }

    public ReceiverCardInstance(String receiverCardTypeId, List<String> moduleIds) {
        this.receiverCardTypeId = receiverCardTypeId;
        this.moduleIds = new ArrayList<>(moduleIds);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReceiverCardTypeId() {
        return receiverCardTypeId;
    }

    public void setReceiverCardTypeId(String receiverCardTypeId) {
        this.receiverCardTypeId = receiverCardTypeId;
    }

    public List<String> getModuleIds() {
        return moduleIds;
    }

    public void setModuleIds(List<String> moduleIds) {
        this.moduleIds = moduleIds != null ? moduleIds : new ArrayList<>();
    }

    public List<String> getManualPortOrder() {
        return manualPortOrder;
    }

    public void setManualPortOrder(List<String> manualPortOrder) {
        this.manualPortOrder = manualPortOrder;
    }

    public String getHostModuleId() {
        return hostModuleId;
    }

    public void setHostModuleId(String hostModuleId) {
        this.hostModuleId = hostModuleId;
    }

    public ReceiverCardInstance copy() {
        ReceiverCardInstance c = new ReceiverCardInstance();
        c.id = id;
        c.receiverCardTypeId = receiverCardTypeId;
        c.moduleIds = new ArrayList<>(moduleIds);
        c.manualPortOrder = manualPortOrder != null ? new ArrayList<>(manualPortOrder) : null;
        c.hostModuleId = hostModuleId;
        return c;
    }
}
