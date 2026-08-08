package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CableLengthProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class CableSpecCalcTest {

    private CableLengthProfile profile(double marginPercent, double... lengths) {
        CableLengthProfile p = new CableLengthProfile();
        p.setName("Тест");
        p.setMarginPercent(marginPercent);
        java.util.List<Double> list = new java.util.ArrayList<>();
        for (double l : lengths) {
            list.add(l);
        }
        p.setAvailableLengthsM(list);
        return p;
    }

    @Test
    void roundsUpToNearestAvailableLength() {
        CableLengthProfile p = profile(0, 10, 20, 30);
        assertEquals(20.0, CableSpecCalc.roundUpToAvailable(12, p));
    }

    @Test
    void exactMatchStaysAsIs() {
        CableLengthProfile p = profile(0, 10, 20, 30);
        assertEquals(20.0, CableSpecCalc.roundUpToAvailable(20, p));
    }

    @Test
    void marginPushesToNextLength() {
        // 19м при запасе 10% -> нужно 20.9м -> 20м уже не хватает, берём 30м
        CableLengthProfile p = profile(10, 10, 20, 30);
        assertEquals(30.0, CableSpecCalc.roundUpToAvailable(19, p));
    }

    @Test
    void roundUpToAvailableReturnsNullWhenNoSinglePieceCovers() {
        CableLengthProfile p = profile(0, 10, 20);
        assertNull(CableSpecCalc.roundUpToAvailable(25, p));
    }

    @Test
    void minimalKitUsesSinglePieceWhenOneCovers() {
        CableLengthProfile p = profile(0, 10, 20, 30);
        List<CableSpecCalc.Piece> kit = CableSpecCalc.minimalKit(12, p);
        assertEquals(1, kit.size());
        assertEquals(20.0, kit.get(0).lengthM());
        assertEquals(1, kit.get(0).count());
    }

    @Test
    void minimalKitSplicesWhenNoSinglePieceCovers() {
        // 45м, доступны 10/20/30 -> ни один кусок не покрывает, минимальный
        // комплект по числу кусков — 30+20=50 (2 куска), а не 30+10+10 и т.п.
        CableLengthProfile p = profile(0, 10, 20, 30);
        List<CableSpecCalc.Piece> kit = CableSpecCalc.minimalKit(45, p);
        assertEquals(2, kit.stream().mapToInt(CableSpecCalc.Piece::count).sum());
        double sum = kit.stream().mapToDouble(pc -> pc.lengthM() * pc.count()).sum();
        assertTrue(sum >= 45, "комплект должен покрывать запрошенную длину");
        assertEquals(30.0, kit.get(0).lengthM());
        assertEquals(1, kit.get(0).count());
        assertEquals(20.0, kit.get(1).lengthM());
        assertEquals(1, kit.get(1).count());
    }

    @Test
    void minimalKitReturnsNullWhenCatalogEmpty() {
        CableLengthProfile p = profile(0);
        assertNull(CableSpecCalc.minimalKit(10, p));
    }

    @Test
    void breakdownGroupsMultipleEdgesByRoundedLength() {
        CableLengthProfile p = profile(0, 10, 15, 20, 30);
        // 3 связи по 12м (округлятся до 15) + 2 связи по 28м (округлятся до 30) +
        // 1 связь по 40м — одного куска не хватает, комплектуется сплайсовкой
        // (30+10, минимум кусков) — уже не "не покрыта", а входит в закупку.
        CableSpecCalc.Breakdown result = CableSpecCalc.breakdown(
                List.of(new double[]{12, 3}, new double[]{28, 2}, new double[]{40, 1}), p);
        assertEquals(3, result.countByRoundedLengthM().get(15.0));
        assertEquals(3, result.countByRoundedLengthM().get(30.0));
        assertEquals(1, result.countByRoundedLengthM().get(10.0));
        assertEquals(0, result.uncoveredCount());
        assertEquals(1, result.spliced().size());
        CableSpecCalc.SpliceInfo splice = result.spliced().get(0);
        assertEquals(40.0, splice.rawLengthM());
        assertEquals(1, splice.lineCount());
        assertEquals(2, splice.pieces().size());
    }

    @Test
    void breakdownReportsUncoveredOnlyWhenCatalogEmpty() {
        CableLengthProfile p = profile(0);
        CableSpecCalc.Breakdown result = CableSpecCalc.breakdown(List.of(new double[]{40, 1}), p);
        assertEquals(1, result.uncoveredCount());
        assertTrue(result.countByRoundedLengthM().isEmpty());
        assertTrue(result.spliced().isEmpty());
    }
}
