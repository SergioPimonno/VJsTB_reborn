package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CableLengthProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Формирование минимально необходимого комплекта кабеля (с учётом сплайсовки) для
 * покрытия фактически требуемой длины связи — единая точка формулы, используется
 * при формировании спецификации коммутации (ui.stage.OutputStagePanel). Если ни
 * одна ОДНА доступная длина каталога (см. model.CableLengthProfile) не покрывает
 * запрошенную длину (с запасом), связь не отбраковывается — вместо этого
 * подбирается минимальное по числу кусков сочетание нескольких длин каталога,
 * которое в сумме её покрывает (сплайсовка), — в этом весь смысл каталога длин.
 */
public final class CableSpecCalc {

    /** Точность подбора комплекта, единиц на метр (0.01 м = 1 см). */
    private static final int SCALE = 100;

    private CableSpecCalc() {
    }

    /** {@code null}, если ни одна доступная длина не покрывает запрошенную (с учётом
     *  запаса) одним куском — см. {@link #minimalKit} для покрытия несколькими
     *  (сплайсовка). */
    public static Double roundUpToAvailable(double rawLengthM, CableLengthProfile profile) {
        double target = rawLengthM * (1 + profile.getMarginPercent() / 100.0);
        return profile.getAvailableLengthsM().stream()
                .filter(len -> len >= target)
                .min(Double::compare)
                .orElse(null);
    }

    /** Минимальный по количеству кусков набор длин из каталога (с повторами),
     *  сумма которых не меньше запрошенной длины с запасом профиля — то есть
     *  минимально необходимый комплект кабеля для реализации связи через сплайсовку,
     *  когда одного куска каталога не хватает (обычный случай — комплект из одного
     *  куска, как раньше в {@link #roundUpToAvailable}). При нескольких вариантах с
     *  одинаковым минимальным числом кусков предпочитается меньшая суммарная длина
     *  (меньше отход). {@code null}, если каталог пуст или не содержит ни одной
     *  положительной длины — комплектовать нечем. */
    public static List<Piece> minimalKit(double rawLengthM, CableLengthProfile profile) {
        double target = rawLengthM * (1 + profile.getMarginPercent() / 100.0);
        List<Double> avail = profile.getAvailableLengthsM().stream()
                .filter(len -> len != null && len > 0)
                .distinct()
                .sorted()
                .toList();
        if (avail.isEmpty()) {
            return null;
        }
        if (target <= 0) {
            return List.of();
        }

        int targetUnits = (int) Math.ceil(target * SCALE - 1e-6);
        int[] denoms = avail.stream().mapToInt(len -> (int) Math.round(len * SCALE)).toArray();
        int maxDenom = Arrays.stream(denoms).max().orElse(0);
        int upper = targetUnits + maxDenom - 1;

        // dp[s] — минимум кусков, чтобы набрать РОВНО s единиц; via[s] — индекс в
        // denoms последнего использованного куска (для восстановления комплекта).
        // Минимум кусков для покрытия s >= targetUnits всегда достигается при
        // s < targetUnits + maxDenom (иначе можно убрать последний добавленный
        // кусок и остаться с суммой >= targetUnits меньшим числом кусков) — поэтому
        // достаточно перебрать точные суммы в этом диапазоне.
        int[] dp = new int[upper + 1];
        int[] via = new int[upper + 1];
        Arrays.fill(dp, Integer.MAX_VALUE / 2);
        dp[0] = 0;
        for (int s = 1; s <= upper; s++) {
            for (int di = 0; di < denoms.length; di++) {
                int d = denoms[di];
                if (d <= s && dp[s - d] + 1 < dp[s]) {
                    dp[s] = dp[s - d] + 1;
                    via[s] = di;
                }
            }
        }

        int bestS = -1;
        for (int s = targetUnits; s <= upper; s++) {
            if (dp[s] < Integer.MAX_VALUE / 2 && (bestS == -1 || dp[s] < dp[bestS])) {
                bestS = s;
            }
        }
        if (bestS == -1) {
            return null;
        }

        Map<Double, Integer> counts = new LinkedHashMap<>();
        int cur = bestS;
        while (cur > 0) {
            int di = via[cur];
            counts.merge(avail.get(di), 1, Integer::sum);
            cur -= denoms[di];
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Double, Integer>comparingByKey().reversed())
                .map(e -> new Piece(e.getKey(), e.getValue()))
                .toList();
    }

    /** Округляет/комплектует и суммирует набор связей одного типа провода (каждая —
     *  {@code count} одинаковых линий длиной {@code rawLengthM}, см.
     *  SchemaEdge.wireCount/lengthM). {@code countByRoundedLengthM} — итоговый
     *  список закупки: сколько кусков каждой доступной длины нужно суммарно (уже
     *  включая куски, ушедшие в сплайсованные комплекты). {@code spliced} —
     *  какие именно линии потребовали больше одного куска, с составом их
     *  комплекта (для наглядности в спецификации). {@code uncoveredCount} — линии,
     *  для которых каталог длин пуст/непригоден (см. {@link #minimalKit}). */
    public static Breakdown breakdown(List<double[]> rawLengthsAndCounts, CableLengthProfile profile) {
        Map<Double, Integer> byRoundedLength = new LinkedHashMap<>();
        List<SpliceInfo> spliced = new ArrayList<>();
        int uncoveredCount = 0;
        for (double[] entry : rawLengthsAndCounts) {
            double rawLengthM = entry[0];
            int count = (int) Math.round(entry[1]);
            List<Piece> kit = minimalKit(rawLengthM, profile);
            if (kit == null) {
                uncoveredCount += count;
                continue;
            }
            for (Piece p : kit) {
                byRoundedLength.merge(p.lengthM(), p.count() * count, Integer::sum);
            }
            if (kit.size() > 1) {
                spliced.add(new SpliceInfo(rawLengthM, count, kit));
            }
        }
        return new Breakdown(byRoundedLength, spliced, uncoveredCount);
    }

    /** Один кусок кабеля определённой длины в комплекте, повторённый {@code count} раз. */
    public record Piece(double lengthM, int count) {
    }

    /** Линия(и) одной и той же требуемой длины, покрытая несколькими кусками
     *  (сплайсовка) — {@code lineCount} одинаковых линий, каждая из которых
     *  комплектуется набором {@code pieces}. */
    public record SpliceInfo(double rawLengthM, int lineCount, List<Piece> pieces) {
    }

    public record Breakdown(Map<Double, Integer> countByRoundedLengthM, List<SpliceInfo> spliced,
            int uncoveredCount) {
    }
}
