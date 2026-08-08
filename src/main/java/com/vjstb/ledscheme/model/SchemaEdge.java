package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Связь между двумя узлами общей схемы площадки (линия питания/сигнала). Каждая
 * связь — это коммутация, поэтому подпись всегда имеет вид «N×Тип» (например,
 * «3×CEE 32A» или «2×SDI»), задаётся структурированными полями wireCount/wireType
 * (+ lengthM, где применимо, — метраж важен для спецификации, в т.ч. для сигнала,
 * если тип провода зарегистрирован в каталоге длин, см. CableLengthProfile). label хранит либо
 * составленную из этих полей строку, либо старую свободную подпись (до появления
 * структуры) — используется как есть, если структурированные поля не заданы.
 */
public class SchemaEdge {

    private String id = UUID.randomUUID().toString();
    private SchemaMode mode = SchemaMode.POWER;
    private String fromNodeId;
    private String toNodeId;
    /** id конкретного разъёма (CardPort) на узле-источнике/приёмнике — заполняется,
     *  только если связь создана коммутацией через гнёзда (см. настройку персонализации
     *  «коммутация через гнёзда разъёмов»); null — связь идёт от узла целиком, как раньше. */
    private String fromPortId;
    private String toPortId;
    /** id конкретного КАБИНЕТА (CabinetInstance), выступающего гнездом подключения на
     *  миниатюре расключения узла-экрана — заполняется, только если связь заведена в
     *  такой кабинет-«гнездо» (см. AppModel.chainEndpointSocketCabinetIds и настройку
     *  «вводные кабинеты цепочек — тоже гнёзда подключения»); null — как раньше, связь
     *  не привязана к конкретному кабинету. Независимо от {@link #fromPortId}/
     *  {@link #toPortId} — у одного конца связи задан не более чем ОДИН из двух
     *  (кабинет-гнездо есть только у узла-экрана, у него нет своих CardPort). */
    private String fromCabinetInstanceId;
    private String toCabinetInstanceId;
    private String label;

    private Integer wireCount;
    private String wireType;
    /** Метраж линии, м — необязателен; важен для спецификации коммутации, когда
     *  wireType зарегистрирован в каталоге длин (см. model.CableLengthProfile,
     *  см. также CableType.fixedLengthM для переходников) — как для питания, так
     *  и для сигнала (например, оптика на десятки-сотни метров). */
    private Double lengthM;
    /** Точки излома маршрута связи (см. {@link EdgeWaypoint}) — пусто (по умолчанию)
     *  означает прямую линию узел-узел, как раньше; непустой список рисует связь
     *  ломаной линией через эти точки (ортогональная/произвольная маршрутизация,
     *  Task #85/v1.4, задаётся перетаскиванием в редакторе схемы). */
    private List<EdgeWaypoint> waypoints = new ArrayList<>();
    /** Пунктирная линия связи вместо сплошной — визуальное отличие (например, для
     *  обходного/резервного/мониторингового пути, как в референсном PDF). */
    private boolean dashed;
    /** Пользовательский цвет линии (RGB, см. Color.getRGB()) — null означает
     *  «стандартный цвет по режиму схемы» (см. SchemaCanvasPanel); отдельная связь
     *  сигнала может получить свой цвет для читаемости плотной схемы. */
    private Integer color;
    /** Смещение подписи (экранные пиксели) от расчётной точки по центру маршрута
     *  ({@code midOfRoute}) — 0,0 (по умолчанию) означает текущее поведение
     *  «подпись всегда по центру линии»; ненулевое значение задаётся перетаскиванием
     *  чипа подписи в редакторе схемы (Task #3). */
    private double labelDx;
    private double labelDy;

    public SchemaEdge() {
    }

    public SchemaEdge(SchemaMode mode, String fromNodeId, String toNodeId, String label) {
        this.mode = mode;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.label = label;
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

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getFromPortId() {
        return fromPortId;
    }

    public void setFromPortId(String fromPortId) {
        this.fromPortId = fromPortId;
    }

    public String getToPortId() {
        return toPortId;
    }

    public void setToPortId(String toPortId) {
        this.toPortId = toPortId;
    }

    public String getFromCabinetInstanceId() {
        return fromCabinetInstanceId;
    }

    public void setFromCabinetInstanceId(String fromCabinetInstanceId) {
        this.fromCabinetInstanceId = fromCabinetInstanceId;
    }

    public String getToCabinetInstanceId() {
        return toCabinetInstanceId;
    }

    public void setToCabinetInstanceId(String toCabinetInstanceId) {
        this.toCabinetInstanceId = toCabinetInstanceId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getWireCount() {
        return wireCount;
    }

    public void setWireCount(Integer wireCount) {
        this.wireCount = wireCount;
    }

    public String getWireType() {
        return wireType;
    }

    public void setWireType(String wireType) {
        this.wireType = wireType;
    }

    public Double getLengthM() {
        return lengthM;
    }

    public void setLengthM(Double lengthM) {
        this.lengthM = lengthM;
    }

    public List<EdgeWaypoint> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<EdgeWaypoint> waypoints) {
        this.waypoints = waypoints != null ? waypoints : new ArrayList<>();
    }

    public boolean isDashed() {
        return dashed;
    }

    public void setDashed(boolean dashed) {
        this.dashed = dashed;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    public double getLabelDx() {
        return labelDx;
    }

    public void setLabelDx(double labelDx) {
        this.labelDx = labelDx;
    }

    public double getLabelDy() {
        return labelDy;
    }

    public void setLabelDy(double labelDy) {
        this.labelDy = labelDy;
    }

    /** true, если подпись задана структурированно (N×тип) — только такие связи
     *  попадают в автоматическую спецификацию коммутации на этапе «Вывод». */
    public boolean hasStructuredWire() {
        return wireCount != null && wireCount > 0 && wireType != null && !wireType.isBlank();
    }

    /** Подпись для отображения: составленная из N×тип(+метраж), либо старая свободная. */
    public String displayLabel() {
        if (hasStructuredWire()) {
            StringBuilder sb = new StringBuilder();
            sb.append(wireCount).append('×').append(wireType);
            if (lengthM != null && lengthM > 0) {
                sb.append(", ").append(formatLength(lengthM)).append("м");
            }
            return sb.toString();
        }
        return label;
    }

    private static String formatLength(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    public SchemaEdge copy() {
        SchemaEdge e = new SchemaEdge();
        e.id = id;
        e.mode = mode;
        e.fromNodeId = fromNodeId;
        e.toNodeId = toNodeId;
        e.fromPortId = fromPortId;
        e.toPortId = toPortId;
        e.fromCabinetInstanceId = fromCabinetInstanceId;
        e.toCabinetInstanceId = toCabinetInstanceId;
        e.label = label;
        e.wireCount = wireCount;
        e.wireType = wireType;
        e.lengthM = lengthM;
        for (EdgeWaypoint w : waypoints) {
            e.waypoints.add(w.copy());
        }
        e.dashed = dashed;
        e.color = color;
        e.labelDx = labelDx;
        e.labelDy = labelDy;
        return e;
    }
}
