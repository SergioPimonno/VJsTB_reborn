package com.vjstb.ledscheme.model;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Тип LED cabinet в библиотеке. Библиотека общая для всех проектов,
 * экспортируется/импортируется в JSON.
 */
public class CabinetType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    /** Физическая ширина, мм. */
    private double widthMm = 500;
    /** Физическая высота, мм. */
    private double heightMm = 500;
    /** Физическая глубина (габарит), мм. Необязательно. */
    private Double depthMm;
    /** Разрешение по горизонтали, px. */
    private int resolutionWidth = 128;
    /** Разрешение по вертикали, px. */
    private int resolutionHeight = 128;
    /** Потребление электричества, Вт. */
    private double powerConsumptionW = 150;
    /** Вес, кг. */
    private double weightKg = 12;
    /** Физическая форма кабинета по умолчанию — назначается новым ячейкам этого типа. */
    private CabinetShape shape = CabinetShape.RECTANGLE;
    /** Формы, которые физически СУЩЕСТВУЮТ для этой модели кабинета (кроме shape —
     *  см. Task #79/v1.4) — сужает выбор в радиальном меню "Форма" при ручном
     *  назначении формы конкретной ячейке: раньше можно было назначить ЛЮБУЮ из 4
     *  форм любому кабинету, даже ту, которой у данной модели физически не бывает
     *  (например, круглый вариант прямоугольного кабинета). Пустое множество (в т.ч.
     *  файлы проектов, сохранённые до появления этого поля) трактуется как "только
     *  shape" — см. {@link #getAllowedShapes()}, поэтому явной миграции не требуется. */
    private Set<CabinetShape> allowedShapes = new LinkedHashSet<>();
    /** Тип разъёма ввода питания — сужает список доступных силовых кабелей при
     *  подписи связи в общей схеме питания, если узел ссылается на экран этого типа. */
    private PowerConnectorType powerConnectorType = PowerConnectorType.OTHER;
    /** Сколько отдельных линий питания нужно развести на кабинет (0 = коммутация встроена/сквозная). */
    private int powerConnectorsNeeded = 0;
    /** Сколько отдельных линий сигнала нужно развести на кабинет (0 = коммутация встроена/сквозная). */
    private int signalConnectorsNeeded = 0;
    /** Номинал разъёма (А) для {@link PowerConnectorType#OTHER} — для PowerCon/TRUEcon
     *  номинал фиксирован (см. {@link com.vjstb.ledscheme.service.PowerCalc#CABINET_CONNECTOR_DEFAULT_AMPS}:
     *  ограничивает не сам разъём, а вводной кабель за ним), а для произвольного
     *  разъёма его нужно указать вручную, иначе расчёт нагрузки цепочки (Task #80/#81)
     *  не сможет проверить эту цепочку. null — не указан (проверка нагрузки для
     *  цепочек с этим типом кабинета не выполняется). */
    private Double customConnectorAmpRating;

    public CabinetType() {
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

    public double getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(double widthMm) {
        this.widthMm = widthMm;
    }

    public double getHeightMm() {
        return heightMm;
    }

    public void setHeightMm(double heightMm) {
        this.heightMm = heightMm;
    }

    public Double getDepthMm() {
        return depthMm;
    }

    public void setDepthMm(Double depthMm) {
        this.depthMm = depthMm;
    }

    public int getResolutionWidth() {
        return resolutionWidth;
    }

    public void setResolutionWidth(int resolutionWidth) {
        this.resolutionWidth = resolutionWidth;
    }

    public int getResolutionHeight() {
        return resolutionHeight;
    }

    public void setResolutionHeight(int resolutionHeight) {
        this.resolutionHeight = resolutionHeight;
    }

    public double getPowerConsumptionW() {
        return powerConsumptionW;
    }

    public void setPowerConsumptionW(double powerConsumptionW) {
        this.powerConsumptionW = powerConsumptionW;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public CabinetShape getShape() {
        return shape;
    }

    public void setShape(CabinetShape shape) {
        this.shape = shape;
    }

    /** Формы, допустимые для РУЧНОГО назначения конкретной ячейке этого типа —
     *  всегда непустое: если явно не заданы (старые файлы проектов, либо тип с
     *  единственной физически возможной формой), это ровно {@link #getShape()}. */
    public Set<CabinetShape> getAllowedShapes() {
        return allowedShapes.isEmpty() ? EnumSet.of(shape) : allowedShapes;
    }

    public void setAllowedShapes(Set<CabinetShape> allowedShapes) {
        this.allowedShapes = allowedShapes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedShapes);
    }

    public PowerConnectorType getPowerConnectorType() {
        return powerConnectorType;
    }

    public void setPowerConnectorType(PowerConnectorType powerConnectorType) {
        this.powerConnectorType = powerConnectorType;
    }

    public int getPowerConnectorsNeeded() {
        return powerConnectorsNeeded;
    }

    public void setPowerConnectorsNeeded(int powerConnectorsNeeded) {
        this.powerConnectorsNeeded = powerConnectorsNeeded;
    }

    public int getSignalConnectorsNeeded() {
        return signalConnectorsNeeded;
    }

    public void setSignalConnectorsNeeded(int signalConnectorsNeeded) {
        this.signalConnectorsNeeded = signalConnectorsNeeded;
    }

    public Double getCustomConnectorAmpRating() {
        return customConnectorAmpRating;
    }

    public void setCustomConnectorAmpRating(Double customConnectorAmpRating) {
        this.customConnectorAmpRating = customConnectorAmpRating;
    }

    public CabinetType copy() {
        CabinetType c = new CabinetType();
        c.id = id;
        c.name = name;
        c.widthMm = widthMm;
        c.heightMm = heightMm;
        c.depthMm = depthMm;
        c.resolutionWidth = resolutionWidth;
        c.resolutionHeight = resolutionHeight;
        c.powerConsumptionW = powerConsumptionW;
        c.weightKg = weightKg;
        c.shape = shape;
        c.allowedShapes = new LinkedHashSet<>(allowedShapes);
        c.powerConnectorType = powerConnectorType;
        c.powerConnectorsNeeded = powerConnectorsNeeded;
        c.signalConnectorsNeeded = signalConnectorsNeeded;
        c.customConnectorAmpRating = customConnectorAmpRating;
        return c;
    }
}
