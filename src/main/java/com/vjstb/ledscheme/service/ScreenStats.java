package com.vjstb.ledscheme.service;

/** Вычисленные характеристики экрана (не сохраняются, считаются по требованию). */
public record ScreenStats(
        double physicalWidthMm,
        double physicalHeightMm,
        int resolutionWidthPx,
        int resolutionHeightPx,
        int activeCabinetCount,
        double totalPowerW,
        double totalWeightKg,
        int[] phaseCabinetCounts,   // индексы 1..3 заполнены, 0 не используется
        double[] phasePowerW
) {
}
