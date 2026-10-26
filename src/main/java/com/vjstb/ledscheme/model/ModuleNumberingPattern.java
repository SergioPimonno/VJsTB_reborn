package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Шаблон нумерации модулей в приёмной карте — ОДИН на весь экран (решение D7: смешивать порядки на экране
 * нежелательно, техник читает карту подключения по единому правилу). Восемь вариантов — угол первого модуля
 * ({@link Corner}) × направление обхода ({@link Axis}: по строкам или по столбцам) — плюс «змейка», при которой
 * каждая следующая строка/столбец идёт в обратную сторону. Номер модуля = номер выхода карты (D6).
 * По умолчанию — «первый слева сверху, по строкам, без змейки».
 */
public class ModuleNumberingPattern {

    /** Угол, в котором лежит модуль №1. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** ROWS — идём вдоль строки, потом переходим к следующей; COLUMNS — вдоль столбца. */
    public enum Axis {
        ROWS, COLUMNS
    }

    private Corner corner = Corner.TOP_LEFT;
    private Axis axis = Axis.ROWS;
    private boolean snake;

    public ModuleNumberingPattern() {
    }

    public ModuleNumberingPattern(Corner corner, Axis axis, boolean snake) {
        this.corner = corner != null ? corner : Corner.TOP_LEFT;
        this.axis = axis != null ? axis : Axis.ROWS;
        this.snake = snake;
    }

    public Corner getCorner() {
        return corner != null ? corner : Corner.TOP_LEFT;
    }

    public void setCorner(Corner corner) {
        this.corner = corner != null ? corner : Corner.TOP_LEFT;
    }

    public Axis getAxis() {
        return axis != null ? axis : Axis.ROWS;
    }

    public void setAxis(Axis axis) {
        this.axis = axis != null ? axis : Axis.ROWS;
    }

    public boolean isSnake() {
        return snake;
    }

    public void setSnake(boolean snake) {
        this.snake = snake;
    }

    /** Первый модуль — в верхнем ряду? (методы без префикса get/is — не попадают в JSON). */
    @JsonIgnore
    public boolean top() {
        return getCorner() == Corner.TOP_LEFT || getCorner() == Corner.TOP_RIGHT;
    }

    @JsonIgnore
    public boolean left() {
        return getCorner() == Corner.TOP_LEFT || getCorner() == Corner.BOTTOM_LEFT;
    }

    /** Восемь вариантов палитры нумерации (без змейки), в порядке плиток прототипа: сначала четыре угла
     *  «по строкам», затем четыре «по столбцам». */
    public static List<ModuleNumberingPattern> palette() {
        List<ModuleNumberingPattern> out = new ArrayList<>();
        for (Axis a : Axis.values()) {
            out.add(new ModuleNumberingPattern(Corner.TOP_LEFT, a, false));
            out.add(new ModuleNumberingPattern(Corner.TOP_RIGHT, a, false));
            out.add(new ModuleNumberingPattern(Corner.BOTTOM_LEFT, a, false));
            out.add(new ModuleNumberingPattern(Corner.BOTTOM_RIGHT, a, false));
        }
        return out;
    }

    public ModuleNumberingPattern copy() {
        return new ModuleNumberingPattern(getCorner(), getAxis(), snake);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModuleNumberingPattern p)) {
            return false;
        }
        return getCorner() == p.getCorner() && getAxis() == p.getAxis() && snake == p.snake;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCorner(), getAxis(), snake);
    }
}
