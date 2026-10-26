package com.vjstb.ledscheme.model;

import java.util.Objects;

/**
 * Прямоугольник ячеек сетки экрана (индексы строк и столбцов, обе границы ВКЛЮЧИТЕЛЬНО) — то, что пользователь
 * выделяет протяжкой по модулям в видах «Приёмные карты» и «Блоки питания». Хранится в {@link PowerAutoArea}:
 * автоматически разбитая область помнится именно прямоугольником, а не набором модулей, чтобы пересчитать
 * её при смене параметров типа блока питания (решение D19 плана {@code docs/receiver-cards/PLAN.md}).
 *
 * <p>Конструктор нормализует границы (протяжка справа налево или снизу вверх даёт «перевёрнутые» углы) —
 * иначе каждое место, принимающее прямоугольник, обязано было бы проверять порядок углов само.
 */
public class GridRect {

    private int row0;
    private int col0;
    private int row1;
    private int col1;

    /** Для Jackson. */
    public GridRect() {
    }

    public GridRect(int rowA, int colA, int rowB, int colB) {
        this.row0 = Math.min(rowA, rowB);
        this.row1 = Math.max(rowA, rowB);
        this.col0 = Math.min(colA, colB);
        this.col1 = Math.max(colA, colB);
    }

    public int getRow0() {
        return row0;
    }

    public void setRow0(int row0) {
        this.row0 = row0;
    }

    public int getCol0() {
        return col0;
    }

    public void setCol0(int col0) {
        this.col0 = col0;
    }

    public int getRow1() {
        return row1;
    }

    public void setRow1(int row1) {
        this.row1 = row1;
    }

    public int getCol1() {
        return col1;
    }

    public void setCol1(int col1) {
        this.col1 = col1;
    }

    public boolean contains(int row, int col) {
        return row >= row0 && row <= row1 && col >= col0 && col <= col1;
    }

    /** Есть ли хотя бы одна общая ячейка — условие, по которому ручной блок «съедает» запись об автообласти. */
    public boolean intersects(GridRect o) {
        return o != null && row0 <= o.row1 && row1 >= o.row0 && col0 <= o.col1 && col1 >= o.col0;
    }

    public int rowCount() {
        return row1 - row0 + 1;
    }

    public int colCount() {
        return col1 - col0 + 1;
    }

    public GridRect copy() {
        GridRect r = new GridRect();
        r.row0 = row0;
        r.col0 = col0;
        r.row1 = row1;
        r.col1 = col1;
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GridRect r)) {
            return false;
        }
        return row0 == r.row0 && col0 == r.col0 && row1 == r.row1 && col1 == r.col1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row0, col0, row1, col1);
    }

    @Override
    public String toString() {
        return "[" + row0 + "," + col0 + " .. " + row1 + "," + col1 + "]";
    }
}
