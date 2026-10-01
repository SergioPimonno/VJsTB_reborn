package com.vjstb.ledscheme.model;

/**
 * Одна рама напольного каркаса ({@link ScreenMountType#FLOOR}, {@code service.FloorCalc}) —
 * персистентная запись на {@link Screen#getFloorFrameCells()}. Запрос пользователя 2026-10-01:
 * «для пола давай сделаем переключаемый режим отображения — либо 2D схема как сейчас, либо 3D
 * редактор как для конструктива» — редактор правит именно этот список (клик по раме прячет её,
 * Ctrl+клик по «призраку» возвращает/добавляет), а {@code FloorCalc} считает железо по нему.
 *
 * <p>Сознательно КЛИЕНТСКИЙ класс, а не {@code ledscheme-model} (как {@code
 * StructureFrameCell} наземного конструктива): общую модель без отдельного разрешения не
 * меняем, а серверу/админке эта запись не нужна — она живёт только внутри проекта.
 *
 * <p><b>Позиция</b> — левый верхний кабинет рамы в сетке экрана: {@link #row} (первый ряд
 * полосы рам, кратен числу кабинетов на раму по глубине) и {@link #col} (первая колонка).
 * Размер рамы в кабинетах НЕ хранится — он выводится из текущих рамы/стакана/кабинета
 * ({@code FloorCalc}), поэтому после смены рамы в библиотеке запись, которая перестала
 * помещаться, просто отбрасывается при следующем расчёте («вне новой сетки»).
 *
 * <p><b>{@link #hidden}</b> — тот же приём, что {@code StructureFrameCell#isHidden()}: клик по
 * раме в 3D НЕ удаляет запись, а прячет её — иначе пересчёт не отличил бы «убрано руками» от
 * «ещё не встречалось» и молча возвращал бы убранную раму (Phase 2 конструктива, баг по тесту
 * {@code recalculatingStructurePreservesManualRemovalsWithinNewBounds}).
 *
 * <p><b>{@link #manual}</b> — признак ручной правки (спрятана, добавлена вне автоматической
 * расстановки). Зачем, если у конструктива такого нет: у пола «сетка» — не числа-границы, а
 * сама форма экрана; после правки формы (скрыли кабинет) автоматическая расстановка
 * сдвигается, и НЕтронутые руками рамы должны просто пересчитаться заново, как было до
 * 3D-редактора (иначе повторился бы класс бага Round 18 «пересчёт не менял модель»). Ручные
 * же записи переживают пересчёт, пока их позиция остаётся допустимой (merge-not-overwrite,
 * см. {@code FloorCalc#mergeCells}).
 */
public class FloorFrameCell {

    private int row;
    private int col;
    private boolean hidden;
    private boolean manual;

    public FloorFrameCell() {
    }

    public FloorFrameCell(int row, int col, boolean hidden, boolean manual) {
        this.row = row;
        this.col = col;
        this.hidden = hidden;
        this.manual = manual;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public boolean matches(int row, int col) {
        return this.row == row && this.col == col;
    }

    public FloorFrameCell copy() {
        return new FloorFrameCell(row, col, hidden, manual);
    }

    @Override
    public String toString() {
        return "FloorFrameCell{" + row + "," + col + (hidden ? ",hidden" : "") + (manual ? ",manual" : "") + "}";
    }
}
