package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link MinimalKitCalc} — DP-ядро, вынесенное из {@link CableSpecCalc#minimalKit} для
 *  переиспользования в {@link TrussCalc}. Прямые воспроизведения примеров пользователя из
 *  запроса на фермы (RIGGING_CALC_NOTES.md): доступны 1/2/0.5м — для 2.5м это 2+0.5, для
 *  7м — 2+2+2+1. {@link CableSpecCalcTest} остаётся регрессионной проверкой, что вынос DP
 *  не изменил поведение сплайсовки кабеля (метод-делегат {@code minimalKit}). */
class MinimalKitCalcTest {

    @Test
    void target2_5FromCatalog1_2_0half_givesTwoPlusHalf() {
        List<CableSpecCalc.Piece> kit = MinimalKitCalc.solve(2.5, List.of(1.0, 2.0, 0.5));
        assertEquals(2, kit.size());
        assertEquals(2.0, kit.get(0).lengthM());
        assertEquals(1, kit.get(0).count());
        assertEquals(0.5, kit.get(1).lengthM());
        assertEquals(1, kit.get(1).count());
    }

    @Test
    void target7FromCatalog1_2_0half_givesThreeTwosAndOneOne() {
        List<CableSpecCalc.Piece> kit = MinimalKitCalc.solve(7.0, List.of(1.0, 2.0, 0.5));
        assertEquals(2, kit.size());
        assertEquals(2.0, kit.get(0).lengthM());
        assertEquals(3, kit.get(0).count());
        assertEquals(1.0, kit.get(1).lengthM());
        assertEquals(1, kit.get(1).count());
        double sum = kit.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        assertEquals(7.0, sum, 1e-9);
    }

    @Test
    void returnsNullWhenCatalogEmpty() {
        assertNull(MinimalKitCalc.solve(5.0, List.of()));
    }

    @Test
    void returnsNullWhenCatalogHasNoPositiveLengths() {
        java.util.List<Double> avail = new java.util.ArrayList<>();
        avail.add(0.0);
        avail.add(-1.0);
        avail.add(null);
        assertNull(MinimalKitCalc.solve(5.0, avail));
    }

    @Test
    void returnsEmptyListWhenTargetNonPositive() {
        assertTrue(MinimalKitCalc.solve(0, List.of(1.0, 2.0)).isEmpty());
        assertTrue(MinimalKitCalc.solve(-3, List.of(1.0, 2.0)).isEmpty());
    }

    @Test
    void solveMinimizingOverage_prefersExactTwoPieceMatchOverSingleOversizedPiece() {
        // Прямой тест на баг-репорт пользователя (2026-09-14): для 1.5м экрана с каталогом
        // 1/2/0.5м фактический service.TrussCalc выдавал ОДИН кусок 2м (solve() минимизирует
        // ЧИСЛО кусков -- один кусок меньше двух, даже с 33% излишком), хотя доступна точная
        // комбинация 1+0.5=1.5м без всякого излишка. solveMinimizingOverage минимизирует
        // именно излишек (суммарную длину), только потом число кусков.
        List<CableSpecCalc.Piece> byCount = MinimalKitCalc.solve(1.5, List.of(1.0, 2.0, 0.5));
        assertEquals(1, byCount.size());
        assertEquals(2.0, byCount.get(0).lengthM());
        assertEquals(1, byCount.get(0).count(), "solve() (минимум кусков) для 1.5м ожидаемо берёт один кусок 2м");

        List<CableSpecCalc.Piece> byOverage = MinimalKitCalc.solveMinimizingOverage(1.5, List.of(1.0, 2.0, 0.5));
        assertEquals(2, byOverage.size());
        assertEquals(1.0, byOverage.get(0).lengthM());
        assertEquals(1, byOverage.get(0).count());
        assertEquals(0.5, byOverage.get(1).lengthM());
        assertEquals(1, byOverage.get(1).count());
        double sum = byOverage.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        assertEquals(1.5, sum, 1e-9, "минимизация излишка обязана найти точное совпадение 1+0.5=1.5, без переизбытка");
    }

    @Test
    void solveMinimizingOverage_fallsBackToPieceCountWhenSumsTie() {
        // Когда несколько комплектов дают ОДНУ И ТУ ЖЕ минимальную суммарную длину, тай-брейк
        // -- меньшее число кусков (то же значение, что даёт dp[] для этой суммы "бесплатно").
        List<CableSpecCalc.Piece> kit = MinimalKitCalc.solveMinimizingOverage(4.0, List.of(1.0, 2.0));
        // Единственная сумма, реально достижимая РОВНО 4 -- 2+2 (2 куска) либо 1*4 (4 куска);
        // минимальная сумма >=4 -- это 4 (обе комбинации дают её), tie-break на dp[] выбирает
        // меньшее число кусков -- 2+2.
        assertEquals(1, kit.size());
        assertEquals(2.0, kit.get(0).lengthM());
        assertEquals(2, kit.get(0).count());
    }

    @Test
    void tiesPreferSmallerTotalLength() {
        // 45, доступны 10/20/30 -- 30+20=50 (2 куска) при равном минимальном числе
        // кусков предпочитается меньшей суммарной длине, чем, например, 30+30=60.
        List<CableSpecCalc.Piece> kit = MinimalKitCalc.solve(45, List.of(10.0, 20.0, 30.0));
        double sum = kit.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        assertEquals(2, kit.stream().mapToInt(CableSpecCalc.Piece::count).sum());
        assertEquals(50.0, sum);
    }
}
