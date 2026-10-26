package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Вес и потребление приёмных карт и блоков питания в характеристиках экрана, подвесе и сводке сцены
 * (docs/receiver-cards/PLAN.md, раздел 7, трек E). Карта даёт {@code weightKg}/{@code powerW}, блок — только
 * {@code weightKg} (его {@code ratedPowerW} — отдаваемая мощность, а не потребление). Экран без карт и блоков — прежние
 * числа.
 */
class ModularLoadTest {

    /** Экран 2×4 модулей по 30 Вт и 2 кг; карта А (левая половина) — 0,5 кг, 7 Вт; карта Б — 0,5 кг, 7 Вт; один блок
     *  на весь экран — 1,5 кг. */
    private ModularFixture fixture() {
        ModularFixture f = new ModularFixture(2, 4);
        f.module.setWeightKg(2);
        ReceiverCardType ct = f.cardType("К", 8, null);
        ct.setWeightKg(0.5);
        ct.setPowerW(7.0);
        PowerSupplyType pt = f.psuType("БП", 8, null, false);
        pt.setWeightKg(1.5);
        f.addCard(ct.getId(), 0, 0, 1, 1);
        f.addCard(ct.getId(), 0, 2, 1, 3);
        f.addSupply(pt.getId(), 0, 0, 1, 3);
        return f;
    }

    @Test
    void screenStatsAddCardsAndSuppliesToWeightAndCardsToPower() {
        ModularFixture f = fixture();

        var st = ScreenLogic.stats(f.screen, f.module, f.ws);

        assertEquals(8 * 30 + 14, st.totalPowerW(), 1e-9, "8 модулей по 30 Вт + две карты по 7 Вт; блок не потребляет");
        assertEquals(8 * 2 + 1 + 1.5, st.totalWeightKg(), 1e-9, "модули + две карты по 0,5 кг + блок 1,5 кг");
    }

    @Test
    void sceneStatsSeeTheSameTotals() {
        ModularFixture f = fixture();
        Scene scene = new Scene();
        scene.getScreens().add(f.screen);

        SceneStats ss = ScreenLogic.sceneStats(scene, f.ws);

        assertEquals(8 * 30 + 14, ss.totalPowerW(), 1e-9);
        assertEquals(8 * 2 + 2.5, ss.totalWeightKg(), 1e-9);
    }

    @Test
    void typesWithoutWeightAndPowerAddNothing() {
        ModularFixture f = new ModularFixture(2, 4);
        ReceiverCardType ct = f.cardType("К", 8, null);
        f.addCard(ct.getId(), 0, 0, 1, 1);
        f.addSupply(f.psuType("БП", 8, null, false).getId(), 0, 0, 1, 3);
        var before = ScreenLogic.stats(new ModularFixture(2, 4).screen, f.module, f.ws);

        var st = ScreenLogic.stats(f.screen, f.module, f.ws);

        assertEquals(before.totalPowerW(), st.totalPowerW(), 1e-9, "числа типов не указаны — это не ноль, а «неизвестно»");
        assertEquals(before.totalWeightKg(), st.totalWeightKg(), 1e-9);
    }

    @Test
    void riggingPutsCardAndSupplyWeightOnTheColumnOfTheirPlace() {
        ModularFixture f = fixture();

        List<RiggingCalc.ColumnWeight> cols = RiggingCalc.columnWeights(f.screen, f.module, f.ws);

        assertEquals(4, cols.size());
        // Место карты А — первый из равноудалённых модулей (ряд 1, столбец 1); блока — модуль у центра экрана (столбец 2).
        assertEquals(2 * 2 + 0.5, cols.get(0).weightKg(), 1e-9);
        assertEquals(2 * 2 + 1.5, cols.get(1).weightKg(), 1e-9);
        assertEquals(2 * 2 + 0.5, cols.get(2).weightKg(), 1e-9);
        assertEquals(2 * 2, cols.get(3).weightKg(), 1e-9);
        double sum = cols.stream().mapToDouble(RiggingCalc.ColumnWeight::weightKg).sum();
        assertEquals(ScreenLogic.stats(f.screen, f.module, f.ws).totalWeightKg(), sum, 1e-9,
                "вес на подвесе = вес экрана из характеристик");
    }

    @Test
    void screenWithoutCardsKeepsOldNumbers() {
        ModularFixture f = new ModularFixture(2, 4);
        f.module.setWeightKg(2);

        var st = ScreenLogic.stats(f.screen, f.module, f.ws);

        assertEquals(8 * 30, st.totalPowerW(), 1e-9);
        assertEquals(16, st.totalWeightKg(), 1e-9);
        assertTrue(ModularLoad.items(f.screen, f.ws).isEmpty());
        assertEquals(0, ModularLoad.weightKg(f.screen, f.ws));
    }
}
