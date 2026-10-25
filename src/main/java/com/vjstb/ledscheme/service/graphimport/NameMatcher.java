package com.vjstb.ledscheme.service.graphimport;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подбор оборудования библиотеки по названию блока из внешнего файла. Названия в схемах пользователя те же, что в его
 * библиотеке, но с мелкими расхождениями: другой порядок слов («FOH UPS» / «UPS FOH»), пометка в скобках
 * («CVT4k (Main)»), другое число выходов щита («AlpenBox 125A->4xSocapex» / «AlpenBox 125A->8xSocapex»).
 * Поэтому подбор многоступенчатый, и каждое совпадение несёт оценку и причину — по ним диалог импорта решает, что
 * выбрать сразу, а что показать пользователю для проверки.
 *
 * <p>Оценки: {@code 1,0} — названия совпали (без регистра, пробелов и знаков); {@code 0,97} — те же слова в другом
 * порядке; {@code 0,6…0,95} — «АВТОМАТ 125A->N×разъём» с тем же именем и входным током, но другим составом выходов
 * (чем ближе число выходов, тем выше), или похожее написание; {@code 0,6} — общее редкое слово. Ниже
 * {@link #MIN_SCORE} кандидат не предлагается.
 */
public final class NameMatcher {

    /** Совпадение, которое можно принимать без вопросов. */
    public static final double AUTO_SCORE = 0.95;
    /** С этой оценки лучший кандидат подставляется в диалоге заранее (пользователь всё равно может поменять). */
    public static final double PRESELECT_SCORE = 0.7;
    /** Ниже — кандидат не предлагается вовсе. */
    public static final double MIN_SCORE = 0.6;

    public enum Reason {
        /** Названия совпали. */
        EXACT("совпадает"),
        /** Те же слова в другом порядке. */
        WORD_ORDER("те же слова в другом порядке"),
        /** Тот же щит/дистрибьютор и входной ток, но другой состав выходов. */
        SPEC_NEAR("тот же вход, другие выходы"),
        /** Похожее написание. */
        SIMILAR("похожее название"),
        /** Общее слово в названии. */
        SHARED_WORD("общее слово");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Candidate(EquipmentPreset preset, double score, Reason reason) {
    }

    /** «[имя] 125A->4x32A»: имя (может быть пустым), входной ток, состав выходов. */
    private static final Pattern SPEC = Pattern.compile("^(.*?)\\s*(\\d+)\\s*[aа]?\\s*(?:->|→)\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OUT_ITEM = Pattern.compile("^(\\d+)\\s*[xх×]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private NameMatcher() {
    }

    /** До {@code limit} лучших кандидатов по убыванию оценки; только пресеты режима {@code mode}. */
    public static List<Candidate> rank(String label, Collection<EquipmentPreset> library, SchemaMode mode,
            int limit) {
        String base = baseName(label);
        if (base.isEmpty()) {
            return List.of();
        }
        List<String> tokens = tokens(base);
        String compact = String.join("", tokens);
        Spec spec = parseSpec(base);
        // одинаковые по названию пресеты (общий + свой, серийный без карт + собранный) — берём тот, у которого есть
        // гнёзда: именно он даст узлу порты для связей
        Map<String, EquipmentPreset> byName = new LinkedHashMap<>();
        for (EquipmentPreset p : library) {
            if (p.getMode() != mode || p.getName() == null || p.getName().isBlank()) {
                continue;
            }
            String key = String.join("", tokens(baseName(p.getName())));
            EquipmentPreset prev = byName.get(key);
            if (prev == null || (!hasPorts(prev) && hasPorts(p))) {
                byName.put(key, p);
            }
        }
        List<Candidate> out = new ArrayList<>();
        for (EquipmentPreset p : byName.values()) {
            Candidate c = score(base, tokens, compact, spec, p);
            if (c != null && c.score() >= MIN_SCORE) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static boolean hasPorts(EquipmentPreset p) {
        return !p.getPowerConnectors().isEmpty() || p.getCards().stream().anyMatch(c -> !c.getPorts().isEmpty());
    }

    private static Candidate score(String base, List<String> tokens, String compact, Spec spec, EquipmentPreset p) {
        String pBase = baseName(p.getName());
        List<String> pTokens = tokens(pBase);
        String pCompact = String.join("", pTokens);
        if (pCompact.isEmpty()) {
            return null;
        }
        if (pCompact.equals(compact)) {
            return new Candidate(p, 1.0, Reason.EXACT);
        }
        if (sorted(tokens).equals(sorted(pTokens))) {
            return new Candidate(p, 0.97, Reason.WORD_ORDER);
        }
        double best = 0;
        Reason reason = null;
        Spec pSpec = parseSpec(pBase);
        if (spec != null && pSpec != null && spec.amps().equals(pSpec.amps())
                && String.join("", tokens(spec.name())).equals(String.join("", tokens(pSpec.name())))) {
            double s = 0.6 + 0.35 * outputsSimilarity(spec.outs(), pSpec.outs());
            if (s > best) {
                best = s;
                reason = Reason.SPEC_NEAR;
            }
        }
        // два названия «щит N→выходы» с разным составом выходов различаются ровно одним символом («4x» / «8x») и
        // по написанию выглядят почти одинаковыми — оценку им даёт только сравнение выходов выше
        double sim = spec != null && pSpec != null ? 0 : similarity(compact, pCompact);
        if (sim >= 0.8) {
            double s = 0.72 + (sim - 0.8) * 1.1;
            if (s > best) {
                best = s;
                reason = Reason.SIMILAR;
            }
        }
        // общее «редкое» слово (≥5 знаков, не число-спецификация): «Socapex->6x16A» ~ «Проходная Socapex»
        for (String t : tokens) {
            if (t.length() >= 5 && !t.matches(".*\\d.*") && pTokens.contains(t) && 0.6 > best) {
                best = 0.6;
                reason = Reason.SHARED_WORD;
            }
        }
        return reason == null ? null : new Candidate(p, Math.min(best, 0.94), reason);
    }

    // ---- разбор названий ----

    /** Название без пометок в скобках и кавычек: «CVT4k (Main)» → «CVT4k». */
    static String baseName(String label) {
        if (label == null) {
            return "";
        }
        return label.replaceAll("\\([^)]*\\)", " ").replaceAll("[\"«»]", " ").replaceAll("\\s+", " ").trim();
    }

    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : s.toLowerCase(Locale.ROOT).replace('ё', 'е').split("[^\\p{L}\\p{N}]+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<String> sorted(List<String> tokens) {
        List<String> copy = new ArrayList<>(tokens);
        java.util.Collections.sort(copy);
        return copy;
    }

    private record Spec(String name, String amps, Map<String, Integer> outs) {
    }

    private static Spec parseSpec(String base) {
        Matcher m = SPEC.matcher(base);
        if (!m.matches()) {
            return null;
        }
        Map<String, Integer> outs = new LinkedHashMap<>();
        for (String item : m.group(3).split("[+,]")) {
            String t = item.trim();
            if (t.isEmpty()) {
                continue;
            }
            Matcher om = OUT_ITEM.matcher(t);
            int count = 1;
            String type = t;
            if (om.matches()) {
                count = Integer.parseInt(om.group(1));
                type = om.group(2);
            }
            outs.merge(String.join("", tokens(type)), count, Integer::sum);
        }
        return new Spec(m.group(1).trim(), m.group(2), outs);
    }

    /** Насколько совпадает состав выходов: по каждому типу выхода отношение меньшего числа к большему, среднее по
     *  объединению типов (тип, которого нет с одной стороны, даёт 0). */
    private static double outputsSimilarity(Map<String, Integer> a, Map<String, Integer> b) {
        java.util.Set<String> types = new java.util.LinkedHashSet<>(a.keySet());
        types.addAll(b.keySet());
        if (types.isEmpty()) {
            return 1;
        }
        double sum = 0;
        for (String t : types) {
            int x = a.getOrDefault(t, 0);
            int y = b.getOrDefault(t, 0);
            sum += x == 0 || y == 0 ? 0 : (double) Math.min(x, y) / Math.max(x, y);
        }
        return sum / types.size();
    }

    /** Сходство строк по расстоянию Левенштейна, 0…1. */
    static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return 1.0 - (double) prev[b.length()] / Math.max(a.length(), b.length());
    }
}
