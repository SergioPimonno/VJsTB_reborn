package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CableLengthProfile;
import java.util.ArrayList;
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
        return MinimalKitCalc.solve(target, profile.getAvailableLengthsM());
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
