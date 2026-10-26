package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Вес и потребление приёмных карт и блоков питания модульного экрана (docs/receiver-cards/PLAN.md, раздел 7, трек E).
 * Карты и блоки физически висят/стоят на экране и тянут нагрузку, но в «кабинетах» не числятся — без этой добавки
 * прериг (вес экрана, нагрузка по точкам подвеса), расчёт балласта/каркаса и ёмкость питания недосчитывали бы их.
 * <ul>
 *   <li>карта: {@code weightKg} и {@code powerW} типа (числа необязательны — не указано, значит 0);</li>
 *   <li>блок питания: только {@code weightKg}. Потребления у блока нет: {@code ratedPowerW} — то, что он МОЖЕТ отдать
 *   (по нему считается нагрузка фазы, D22), а не то, что берёт сам, поэтому в «мощность экрана» не добавляется.</li>
 * </ul>
 * Каждая позиция «стоит» на модуле-месте (D11/D20): к нему её и относит подвес. Экран без карт и блоков даёт нули —
 * немодульные проекты считаются как прежде.
 */
public final class ModularLoad {

    private ModularLoad() {
    }

    /** Карта или блок: модуль-место, вес и потребление. */
    public record Item(CabinetInstance place, double weightKg, double powerW) {
    }

    /** Позиции экрана, у которых есть место (хотя бы один существующий видимый модуль). */
    public static List<Item> items(Screen screen, Workspace workspace) {
        List<Item> out = new ArrayList<>();
        if (screen == null || workspace == null
                || (screen.getReceiverCards().isEmpty() && screen.getPowerSupplies().isEmpty())) {
            return out;
        }
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        for (ReceiverCardInstance card : screen.getReceiverCards()) {
            CabinetInstance place = placeOf(idx, card.getModuleIds(), card.getHostModuleId());
            if (place == null) {
                continue;
            }
            ReceiverCardType t = card.getReceiverCardTypeId() != null
                    ? workspace.receiverCardTypeById(card.getReceiverCardTypeId()) : null;
            out.add(new Item(place, t != null ? orZero(t.getWeightKg()) : 0, t != null ? orZero(t.getPowerW()) : 0));
        }
        for (PowerSupplyInstance supply : screen.getPowerSupplies()) {
            CabinetInstance place = placeOf(idx, supply.getModuleIds(), supply.getHostModuleId());
            if (place == null) {
                continue;
            }
            PowerSupplyType t = supply.getPowerSupplyTypeId() != null
                    ? workspace.powerSupplyTypeById(supply.getPowerSupplyTypeId()) : null;
            out.add(new Item(place, t != null ? orZero(t.getWeightKg()) : 0, 0));
        }
        return out;
    }

    /** Суммарный вес карт и блоков экрана, кг. */
    public static double weightKg(Screen screen, Workspace workspace) {
        double sum = 0;
        for (Item i : items(screen, workspace)) {
            sum += i.weightKg();
        }
        return sum;
    }

    /** Суммарное потребление карт экрана, Вт. */
    public static double powerW(Screen screen, Workspace workspace) {
        double sum = 0;
        for (Item i : items(screen, workspace)) {
            sum += i.powerW();
        }
        return sum;
    }

    private static CabinetInstance placeOf(Map<String, CabinetInstance> idx, List<String> moduleIds, String host) {
        String id = UnitNames.placeOf(idx, moduleIds, host);
        return id != null ? idx.get(id) : null;
    }

    private static double orZero(Double v) {
        return v != null ? v : 0;
    }
}
