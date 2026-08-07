package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void returnsNullWhenNothingCoversRequirement() {
        CableLengthProfile p = profile(0, 10, 20);
        assertNull(CableSpecCalc.roundUpToAvailable(25, p));
    }

    @Test
    void breakdownGroupsMultipleEdgesByRoundedLength() {
        CableLengthProfile p = profile(0, 10, 15, 20, 30);
        // 3 связи по 12м (округлятся до 15) + 2 связи по 28м (округлятся до 30) + 1 связь по 40м (не покрыта)
        CableSpecCalc.Breakdown result = CableSpecCalc.breakdown(
                List.of(new double[]{12, 3}, new double[]{28, 2}, new double[]{40, 1}), p);
        assertEquals(3, result.countByRoundedLengthM().get(15.0));
        assertEquals(2, result.countByRoundedLengthM().get(30.0));
        assertEquals(1, result.uncoveredCount());
    }
}
