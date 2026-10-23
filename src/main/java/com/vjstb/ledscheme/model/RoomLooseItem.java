package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Мелочь (конвертеры и т.п.): свободно на плане, на трассе (с расстоянием от начала трассы) или
 * в кейсе мелочёвки.
 */
public class RoomLooseItem {

    /** Где лежит. */
    public enum Mode {
        FREE, RUN, BOX
    }

    private String id = UUID.randomUUID().toString();
    private RoomLibRef ref;
    private Mode mode = Mode.FREE;
    /** Только для FREE. */
    private Double xCm;
    private Double yCm;
    /** Только для RUN. */
    private String runId;
    /** Расстояние вдоль трассы от первой точки, см. */
    private Double sCm;

    public RoomLooseItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RoomLibRef getRef() {
        return ref;
    }

    public void setRef(RoomLibRef ref) {
        this.ref = ref;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Double getXCm() {
        return xCm;
    }

    public void setXCm(Double xCm) {
        this.xCm = xCm;
    }

    public Double getYCm() {
        return yCm;
    }

    public void setYCm(Double yCm) {
        this.yCm = yCm;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Double getSCm() {
        return sCm;
    }

    public void setSCm(Double sCm) {
        this.sCm = sCm;
    }

}
