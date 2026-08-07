package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран составляется из сетки rows x cols однотипных LED cabinets.
 * Ссылается на тип кабинета из библиотеки по cabinetTypeId; характеристики
 * (разрешение, габариты, вес, потребление) вычисляются из типа и размера сетки.
 */
public class Screen {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String cabinetTypeId;
    private int rows;
    private int cols;
    /** Позиция левого верхнего угла экрана в сцене, мм. */
    private double posXMm;
    private double posYMm;
    /** Количество портов контроллера, доступных для расключения сигнала на этом экране. */
    private int signalPortCount = 8;
    /** Герцовка контента, Гц — влияет на реальную ёмкость порта контроллера в пикселях. */
    private int refreshRateHz = 60;
    /** Глубина цвета на канал, бит (8/10/12) — тоже влияет на ёмкость порта. */
    private int colorBitDepth = 8;

    /** Способ монтажа — влияет на применимость расчёта точек подвеса. */
    private ScreenMountType mountType = ScreenMountType.RIGGED;
    /** Заготовка под расчёт точек подвеса (формула будет добавлена отдельно):
     *  пока просто вручную вводимое количество точек и заметки. */
    private int riggingPointsCount = 0;
    private String riggingNotes;

    /** Контроллеры, обслуживающие экран (может быть несколько). Если список не пуст,
     *  суммарное число их портов определяет доступные порты сигнала вместо signalPortCount. */
    private List<ControllerInstance> controllers = new ArrayList<>();

    private List<CabinetInstance> cabinets = new ArrayList<>();
    private List<PowerChain> powerChains = new ArrayList<>();
    private List<SignalChain> signalChains = new ArrayList<>();

    public Screen() {
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

    public String getCabinetTypeId() {
        return cabinetTypeId;
    }

    public void setCabinetTypeId(String cabinetTypeId) {
        this.cabinetTypeId = cabinetTypeId;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public double getPosXMm() {
        return posXMm;
    }

    public void setPosXMm(double posXMm) {
        this.posXMm = posXMm;
    }

    public double getPosYMm() {
        return posYMm;
    }

    public void setPosYMm(double posYMm) {
        this.posYMm = posYMm;
    }

    public int getSignalPortCount() {
        return signalPortCount;
    }

    public void setSignalPortCount(int signalPortCount) {
        this.signalPortCount = signalPortCount;
    }

    public int getRefreshRateHz() {
        return refreshRateHz;
    }

    public void setRefreshRateHz(int refreshRateHz) {
        this.refreshRateHz = refreshRateHz;
    }

    public int getColorBitDepth() {
        return colorBitDepth;
    }

    public void setColorBitDepth(int colorBitDepth) {
        this.colorBitDepth = colorBitDepth;
    }

    public ScreenMountType getMountType() {
        return mountType;
    }

    public void setMountType(ScreenMountType mountType) {
        this.mountType = mountType;
    }

    public int getRiggingPointsCount() {
        return riggingPointsCount;
    }

    public void setRiggingPointsCount(int riggingPointsCount) {
        this.riggingPointsCount = riggingPointsCount;
    }

    public String getRiggingNotes() {
        return riggingNotes;
    }

    public void setRiggingNotes(String riggingNotes) {
        this.riggingNotes = riggingNotes;
    }


    public List<ControllerInstance> getControllers() {
        return controllers;
    }

    public void setControllers(List<ControllerInstance> controllers) {
        this.controllers = controllers;
    }

    public List<CabinetInstance> getCabinets() {
        return cabinets;
    }

    public void setCabinets(List<CabinetInstance> cabinets) {
        this.cabinets = cabinets;
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

    // ---- вспомогательное ----

    @JsonIgnore
    public CabinetInstance cabinetById(String cabId) {
        for (CabinetInstance c : cabinets) {
            if (c.getId().equals(cabId)) {
                return c;
            }
        }
        return null;
    }

    @JsonIgnore
    public CabinetInstance cabinetAt(int row, int col) {
        for (CabinetInstance c : cabinets) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    public Screen copy() {
        Screen s = new Screen();
        s.id = id;
        s.name = name;
        s.cabinetTypeId = cabinetTypeId;
        s.rows = rows;
        s.cols = cols;
        s.posXMm = posXMm;
        s.posYMm = posYMm;
        s.signalPortCount = signalPortCount;
        s.refreshRateHz = refreshRateHz;
        s.colorBitDepth = colorBitDepth;
        s.mountType = mountType;
        s.riggingPointsCount = riggingPointsCount;
        s.riggingNotes = riggingNotes;
        s.controllers = new ArrayList<>();
        for (ControllerInstance c : controllers) {
            s.controllers.add(c.copy());
        }
        s.cabinets = new ArrayList<>();
        for (CabinetInstance c : cabinets) {
            s.cabinets.add(c.copy());
        }
        // powerChains/signalChains НЕ копируются здесь — цепочки хранятся на уровне
        // сцены, а не экрана (см. Task #78); эти поля на Screen остаются только для
        // чтения СТАРЫХ файлов проектов (миграция в WorkspaceStore переносит их на
        // сцену при загрузке), копия экрана (используется для undo-снимка) их больше
        // не касается — AppModel.undo() снимает/восстанавливает цепочки сцены отдельно.
        return s;
    }
}
