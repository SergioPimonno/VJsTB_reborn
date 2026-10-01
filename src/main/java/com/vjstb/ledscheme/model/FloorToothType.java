package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * ЗАГОТОВКА на будущее, НИКУДА НЕ ПОДКЛЮЧЕНА: «зуб» — фиксатор кабинета к раме напольного
 * каркаса (см. {@code service.FloorCalc}, раздел «Напольный каркас» в
 * STRUCTURE_CALC_NOTES.md). Количество зубов на кабинет — настройка пользователя на экран
 * ({@link Screen#getFloorTeethPerCabinet()}, 2..4), сам тип зуба в библиотеку пока НЕ
 * заносится (решение пользователя 2026-10-01).
 *
 * <p>Заготовка под будущий вид {@code StructureFrameType.Kind.TOOTH} в общей модели {@code
 * ledscheme-model} — см. javadoc {@link FloorLegType} за тем, почему сейчас это отдельный
 * неподключённый класс в клиенте, а не правка общей модели; план расширения — в
 * STRUCTURE_CALC_NOTES.md.
 */
public class FloorToothType {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    /** Вес одного зуба, кг — пойдёт в среднюю нагрузку пола, когда зубы появятся в библиотеке. */
    private double weightKg;

    public FloorToothType() {
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

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }
}
