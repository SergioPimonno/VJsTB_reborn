package com.vjstb.ledscheme.service;

/** Сводные характеристики сцены (базовый прериг): суммарно по всем экранам. */
public record SceneStats(
        int screenCount,
        int totalCabinetCount,
        double totalPowerW,
        double totalWeightKg
) {
}
