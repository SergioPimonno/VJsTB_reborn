package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CableLengthProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Округление фактически требуемой длины связи вверх до ближайшей доступной длины
 * катушки (см. model.CableLengthProfile) — единая точка формулы, используется
 * при формировании спецификации коммутации (ui.stage.OutputStagePanel).
 */
public final class CableSpecCalc {

    private CableSpecCalc() {
    }

    /** {@code null}, если ни одна доступная длина не покрывает запрошенную (с учётом
     *  запаса) — нужна сплайсовка/докупка, вызывающий код должен явно предупредить. */
    public static Double roundUpToAvailable(double rawLengthM, CableLengthProfile profile) {
        double target = rawLengthM * (1 + profile.getMarginPercent() / 100.0);
        return profile.getAvailableLengthsM().stream()
                .filter(len -> len >= target)
                .min(Double::compare)
                .orElse(null);
    }

    /** Округляет и суммирует набор связей одного типа провода (каждая — {@code count}
     *  одинаковых линий длиной {@code rawLengthM}, см. SchemaEdge.wireCount/lengthM)
     *  по итоговой округлённой длине. {@code uncovered} на выходе — сумма линий, для
     *  которых не нашлось подходящей доступной длины (см. {@link #roundUpToAvailable}). */
    public static Breakdown breakdown(List<double[]> rawLengthsAndCounts, CableLengthProfile profile) {
        Map<Double, Integer> byRoundedLength = new LinkedHashMap<>();
        int uncoveredCount = 0;
        for (double[] entry : rawLengthsAndCounts) {
            double rawLengthM = entry[0];
            int count = (int) Math.round(entry[1]);
            Double rounded = roundUpToAvailable(rawLengthM, profile);
            if (rounded == null) {
                uncoveredCount += count;
            } else {
                byRoundedLength.merge(rounded, count, Integer::sum);
            }
        }
        return new Breakdown(byRoundedLength, uncoveredCount);
    }

    public record Breakdown(Map<Double, Integer> countByRoundedLengthM, int uncoveredCount) {
    }
}
