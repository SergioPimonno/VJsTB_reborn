package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import java.util.Map;

/** Сводные характеристики сцены (базовый прериг): суммарно по всем экранам. */
public record SceneStats(
        int screenCount,
        int totalCabinetCount,
        double totalPowerW,
        double totalWeightKg,
        /** Разбивка активных кабинетов по фактическому типу (с учётом переопределений
         *  по ячейкам) — только типы, которых реально больше 0 на сцене. */
        Map<CabinetType, Integer> cabinetCountByType
) {
}
