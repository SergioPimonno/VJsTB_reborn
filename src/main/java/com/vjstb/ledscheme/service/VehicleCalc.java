package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.VehicleType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Подбор минимально достаточной машины под кофры проекта — см.
 * VEHICLE_CALC_NOTES.md за мотивацию и допущения. Площадь пола — простое
 * сравнение (сумма площадей кофров, каждый — {@code (L+clearance)×(W+clearance)}
 * по {@code CaseType#getClearanceMm()}, с учётом штабелирования ≤ площадь
 * пола кузова), без настоящей 2D-раскладки (v1, см. допущения в заметках).
 *
 * <p><b>Штабелирование пересчитывается для КАЖДОЙ машины-кандидата отдельно</b>
 * ({@link #checkFit}), а не фиксируется один раз глобально — высота кузова
 * машины ограничивает, сколько уровней данного типа кофра реально влезет
 * ({@code min(CaseType.maxStackCount, floor(cargoHeight / caseHeight))}), и
 * это меняет требуемую площадь пола от машины к машине. Это единственная
 * трактовка, согласующаяся с требованием «высота кузова учитывается»: машина
 * с меньшей площадью пола, но более высоким кузовом может позволить более
 * плотный штабель и оказаться меньше по площади — фиксированная заранее
 * высота штабеля не даст правильно её выбрать.
 */
public final class VehicleCalc {

    private VehicleCalc() {
    }

    /** Одна строка калькулятора: тип кофра + итоговое (возможно, авто-предложенное
     *  и вручную переопределённое) количество. */
    public record CaseRow(CaseType type, int count) {
    }

    /** Результат проверки одной машины-кандидата. {@code failureReason} — null,
     *  если {@code fits}; иначе краткое объяснение для UI. */
    public record VehicleFitResult(VehicleType vehicle, boolean fits, double requiredFloorAreaM2,
                                    double cargoFloorAreaM2, Double totalWeightKg, boolean weightOk,
                                    String failureReason) {
    }

    /** Площадь пола кузова, м². */
    public static double cargoAreaM2(VehicleType vehicle) {
        return (vehicle.getCargoLengthMm() / 1000.0) * (vehicle.getCargoWidthMm() / 1000.0);
    }

    /** Сколько кофров типа "несёт кабинеты" нужно под {@code totalCabinets}
     *  кабинетов проекта при вместимости {@code cabinetsPerCase} на кофр —
     *  округление вверх. Вынесено отдельной чистой функцией (не в диалог),
     *  чтобы её можно было unit-тестировать без Swing/AppModel. */
    public static int suggestCaseCount(int totalCabinets, int cabinetsPerCase) {
        return (int) Math.ceil(totalCabinets / (double) Math.max(1, cabinetsPerCase));
    }

    /** Проверяет, помещаются ли строки {@code rows} в машину {@code vehicle} —
     *  по площади пола (с учётом штабелирования, ограниченного высотой ИМЕННО
     *  этой машины) и, опционально, по грузоподъёмности. */
    public static VehicleFitResult checkFit(VehicleType vehicle, List<CaseRow> rows, boolean checkWeight) {
        double cargoArea = cargoAreaM2(vehicle);
        double requiredAreaM2 = 0;
        for (CaseRow row : rows) {
            if (row.count() <= 0) {
                continue;
            }
            CaseType t = row.type();
            int heightCap = (int) Math.floor(vehicle.getCargoHeightMm() / t.getHeightMm());
            if (heightCap < 1) {
                return new VehicleFitResult(vehicle, false, requiredAreaM2, cargoArea, null, true,
                        "Кофр \"" + t.getName() + "\" выше грузового отсека");
            }
            int effectiveStack = Math.max(1, Math.min(t.getMaxStackCount(), heightCap));
            int footprintUnits = (int) Math.ceil(row.count() / (double) effectiveStack);
            // Запас по периметру (CaseType.clearanceMm) — кофры в машине физически не
            // стоят впритирку, каждая занятая "клетка" пола реально больше голого L×W
            // на этот зазор с каждой стороны.
            double effLengthM = (t.getLengthMm() + t.getClearanceMm()) / 1000.0;
            double effWidthM = (t.getWidthMm() + t.getClearanceMm()) / 1000.0;
            requiredAreaM2 += footprintUnits * effLengthM * effWidthM;
        }
        boolean areaOk = requiredAreaM2 <= cargoArea;

        Double totalWeight = null;
        boolean weightOk = true;
        if (checkWeight) {
            double sum = 0;
            for (CaseRow row : rows) {
                sum += row.count() * row.type().getWeightKg();
            }
            totalWeight = sum;
            weightOk = sum <= vehicle.getPayloadKg();
        }

        boolean fits = areaOk && weightOk;
        String reason = !areaOk ? "Недостаточно площади пола" : !weightOk ? "Превышена грузоподъёмность" : null;
        return new VehicleFitResult(vehicle, fits, requiredAreaM2, cargoArea, totalWeight, weightOk, reason);
    }

    /** Минимально достаточная машина из {@code candidates} — ранжирование "от
     *  меньшей к большей": площадь пола по возрастанию, тай-брейки — высота
     *  кузова, затем грузоподъёмность. Первая подходящая по {@link #checkFit}
     *  в этом порядке. Пусто, если ни одна не подходит. */
    public static Optional<VehicleType> recommend(List<VehicleType> candidates, List<CaseRow> rows,
                                                    boolean checkWeight) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((VehicleType v) -> v.getCargoLengthMm() * v.getCargoWidthMm())
                        .thenComparingDouble(VehicleType::getCargoHeightMm)
                        .thenComparingDouble(VehicleType::getPayloadKg))
                .filter(v -> checkFit(v, rows, checkWeight).fits())
                .findFirst();
    }
}
