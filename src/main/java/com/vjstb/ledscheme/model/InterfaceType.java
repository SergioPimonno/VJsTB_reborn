package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Вид интерфейса (например, HDMI, DisplayPort, SDI) и список его версий/
 * вариантов (например, для HDMI — 1.4/2.0/2.1) — общий справочник, как
 * {@link CabinetType}/{@link ControllerType}/{@link CableType}, используется
 * везде, где раньше был плоский список строк вроде "HDMI 2.1"/"HDMI 2.0"
 * (карты оборудования, библиотека кабелей, подпись связи схемы): сначала
 * выбирается вид, затем — версия из его собственного списка. Виды без версий
 * (пустой список, например XLR/BNC) используются одним названием без версии.
 */
public class InterfaceType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private List<String> versions = new ArrayList<>();

    public InterfaceType() {
    }

    public InterfaceType(String name, List<String> versions) {
        this.name = name;
        this.versions = new ArrayList<>(versions);
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

    public List<String> getVersions() {
        return versions;
    }

    public void setVersions(List<String> versions) {
        this.versions = versions;
    }

    /** Составляет отображаемую строку "Вид Версия" (или просто "Вид", если версия
     *  не выбрана/у вида нет версий) — итоговый формат, в котором тип разъёма
     *  всегда хранился как одна строка (CardPort.connectorType и т.п.), чтобы не
     *  ломать уже сохранённые данные и остальную логику, завязанную на строку. */
    public String composedLabel(String version) {
        return version == null || version.isEmpty() ? name : name + " " + version;
    }

    public InterfaceType copy() {
        InterfaceType t = new InterfaceType();
        t.id = id;
        t.name = name;
        t.versions = new ArrayList<>(versions);
        return t;
    }
}
