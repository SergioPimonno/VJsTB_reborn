package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * ЗАГОТОВКА на будущее, НИКУДА НЕ ПОДКЛЮЧЕНА: ножка напольного каркаса (см. {@code
 * service.FloorCalc}, раздел «Напольный каркас» в STRUCTURE_CALC_NOTES.md).
 *
 * <p>Почему класс есть, но не используется: пользователь (2026-10-01) попросил ножки и зубы
 * в библиотеку пока НЕ заносить — расчёт считает их только штуками (4 ножки на раму), — но
 * оставить точку роста. Задуман как заготовка под будущие виды {@code
 * StructureFrameType.Kind.LEG}/{@code TOOTH} в общей модели {@code ledscheme-model} (там
 * же, где рама/стакан/контейнер балласта — один класс на все виды через дискриминатор
 * {@code kind}); правка {@code ledscheme-model} требует отдельного разрешения, поэтому
 * сейчас поля просто зафиксированы здесь, в клиенте. План расширения — в
 * STRUCTURE_CALC_NOTES.md.
 *
 * <p><b>Высота ножки пока в расчёте не учитывается</b> (прямое указание пользователя) —
 * {@link #heightMm} нужен будет для высоты пола над площадкой (подиум/пандус), когда это
 * понадобится.
 */
public class FloorLegType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    /** Высота ножки, мм — пока в расчёте НЕ участвует, см. class-javadoc. */
    private Double heightMm;
    /** Вес одной ножки, кг — пойдёт в среднюю нагрузку пола, когда ножки появятся в библиотеке. */
    private double weightKg;

    public FloorLegType() {
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

    public Double getHeightMm() {
        return heightMm;
    }

    public void setHeightMm(Double heightMm) {
        this.heightMm = heightMm;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }
}
