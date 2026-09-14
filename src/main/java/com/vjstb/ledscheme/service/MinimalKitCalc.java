package com.vjstb.ledscheme.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Набор длин (с повторами) из произвольного каталога, сумма которых не меньше запрошенной
 * целевой длины — общее DP-ядро ("задача монет"), вынесенное из {@link
 * CableSpecCalc#minimalKit(double, com.vjstb.ledscheme.model.CableLengthProfile)} (сплайсовка
 * кабеля) для повторного использования {@link TrussCalc} (комплект сегментов фермы).
 * Возвращает {@link CableSpecCalc.Piece} напрямую (без отдельного типа) — этот класс
 * сознательно живёт в одном пакете с {@code CableSpecCalc}, чтобы не тащить маппинг между
 * двумя одинаковыми record-типами через все существующие места использования {@code
 * CableSpecCalc.Piece} ({@code OutputStagePanel}, {@code Breakdown}, тесты).
 *
 * <p><b>Два разных критерия отбора — по прямому запросу пользователя (баг-репорт
 * 2026-09-14)</b>: {@link #solve} минимизирует ЧИСЛО КУСКОВ (тай-брейк — меньшая суммарная
 * длина) — подходит для сплайсовки КАБЕЛЯ, где лишний метр почти ничего не стоит, а лишний
 * физический СТЫК стоит труда/надёжности (меньше стыков важнее меньшего отхода). {@link
 * #solveMinimizingOverage} минимизирует СУММАРНУЮ ДЛИНУ (тай-брейк — меньше кусков) —
 * подходит для ФЕРМЫ, где лишний метр — это физически ЛИШНЯЯ ферма, торчащая за экран
 * (баг-репорт: "для 1.5м экрана рассчиталась ферма 2м [из одного куска], хотя 1+0.5 дают
 * ровно 1.5м" — старый единый критерий {@link #solve} предпочёл бы ОДИН кусок 2м ДВУМ кускам
 * 1м+0.5м именно потому, что кусков меньше, даже с 33% лишней длины). Оба метода — один и тот
 * же DP-массив {@code dp}/{@code via} (минимум кусков на КАЖДУЮ точную достижимую сумму),
 * различается только то, КАКУЮ из достижимых сумм {@code >= target} выбрать: {@link #solve} —
 * с минимальным {@code dp[s]} среди всех s в диапазоне; {@link #solveMinimizingOverage} —
 * САМУЮ МАЛЕНЬКУЮ достижимую s (первую по возрастанию, раз диапазон переберается по
 * возрастанию) — доказательство, что она тоже лежит в том же диапазоне {@code [targetUnits,
 * targetUnits+maxDenom-1]}, что и минимум по кускам, смотри комментарий у {@code upper} ниже
 * (оно не зависит от критерия отбора, только от структуры "монет"). Для точки s,
 * минимизирующей ДЛИНУ, {@code dp[s]}/{@code via[s]} уже дают МИНИМАЛЬНОЕ число кусков СРЕДИ
 * способов набрать именно эту сумму — то есть тай-брейк "меньше кусков" достаётся бесплатно,
 * без отдельной логики.
 */
public final class MinimalKitCalc {

    /** Точность подбора комплекта, единиц на метр (0.01 м = 1 см). */
    private static final int SCALE = 100;

    private enum Objective { MIN_PIECE_COUNT, MIN_TOTAL_LENGTH }

    private MinimalKitCalc() {
    }

    /** Минимальный по КОЛИЧЕСТВУ КУСКОВ набор (тай-брейк — меньшая суммарная длина) — см.
     *  class-javadoc за обоснованием, когда это правильный критерий (сплайсовка кабеля).
     *  {@code null}, если каталог пуст или не содержит ни одной положительной длины —
     *  комплектовать нечем. Пустой список, если {@code targetLengthM <= 0}. */
    public static List<CableSpecCalc.Piece> solve(double targetLengthM, List<Double> availableLengthsM) {
        return solve(targetLengthM, availableLengthsM, Objective.MIN_PIECE_COUNT);
    }

    /** Минимальный по СУММАРНОЙ ДЛИНЕ (наименьший излишек сверх целевой) набор (тай-брейк —
     *  меньше кусков) — см. class-javadoc за обоснованием, когда это правильный критерий
     *  (комплект сегментов фермы: лишняя длина физически нежелательна). Тот же контракт
     *  {@code null}/пустой список, что у {@link #solve}. */
    public static List<CableSpecCalc.Piece> solveMinimizingOverage(double targetLengthM,
            List<Double> availableLengthsM) {
        return solve(targetLengthM, availableLengthsM, Objective.MIN_TOTAL_LENGTH);
    }

    private static List<CableSpecCalc.Piece> solve(double targetLengthM, List<Double> availableLengthsM,
            Objective objective) {
        List<Double> avail = availableLengthsM.stream()
                .filter(len -> len != null && len > 0)
                .distinct()
                .sorted()
                .toList();
        if (avail.isEmpty()) {
            return null;
        }
        if (targetLengthM <= 0) {
            return List.of();
        }

        int targetUnits = (int) Math.ceil(targetLengthM * SCALE - 1e-6);
        int[] denoms = avail.stream().mapToInt(len -> (int) Math.round(len * SCALE)).toArray();
        int maxDenom = Arrays.stream(denoms).max().orElse(0);
        int upper = targetUnits + maxDenom - 1;

        // dp[s] — минимум кусков, чтобы набрать РОВНО s единиц; via[s] — индекс в
        // denoms последнего использованного куска (для восстановления комплекта).
        // И минимум кусков, И минимальная достижимая сумма для покрытия s >= targetUnits
        // всегда достигаются при s < targetUnits + maxDenom (иначе можно убрать последний
        // добавленный кусок и остаться с суммой >= targetUnits — меньшей и/или меньшим
        // числом кусков) — поэтому достаточно перебрать точные суммы в этом диапазоне для
        // ОБОИХ критериев отбора (см. class-javadoc).
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
        if (objective == Objective.MIN_TOTAL_LENGTH) {
            // Диапазон переберается по ВОЗРАСТАНИЮ s -- первая достижимая сумма уже
            // наименьшая, дальше искать нечего.
            for (int s = targetUnits; s <= upper; s++) {
                if (dp[s] < Integer.MAX_VALUE / 2) {
                    bestS = s;
                    break;
                }
            }
        } else {
            for (int s = targetUnits; s <= upper; s++) {
                if (dp[s] < Integer.MAX_VALUE / 2 && (bestS == -1 || dp[s] < dp[bestS])) {
                    bestS = s;
                }
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
                .map(e -> new CableSpecCalc.Piece(e.getKey(), e.getValue()))
                .toList();
    }
}
