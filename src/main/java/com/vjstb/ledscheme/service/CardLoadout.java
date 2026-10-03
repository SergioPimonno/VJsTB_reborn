package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CardKind;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaCard;
import java.util.List;

/**
 * Учёт лимитов модели на число входных/выходных карт в узле (запрос пользователя 2026-10-02, серии
 * оборудования — H-серия Novastar, VFC Disguise D3): модели одной серии делят каталог карт и
 * различаются лимитами {@link EquipmentPreset#getMaxInputCards()}/{@link EquipmentPreset#getMaxOutputCards()}.
 * Входная карта ({@link SchemaCard#effectiveKind()}) занимает слот «входных», выходная — «выходных»,
 * смешанная — оба. Лимит {@code null} — без ограничения. Чистая логика без Swing/модели.
 */
public final class CardLoadout {

    /** Сколько слотов каждого вида занято. */
    public record Counts(int input, int output) {
    }

    private CardLoadout() {
    }

    public static Counts count(List<SchemaCard> cards) {
        int in = 0;
        int out = 0;
        for (SchemaCard c : cards) {
            CardKind kind = c.effectiveKind();
            if (kind == CardKind.INPUT || kind == CardKind.MIXED) {
                in++;
            }
            if (kind == CardKind.OUTPUT || kind == CardKind.MIXED) {
                out++;
            }
        }
        return new Counts(in, out);
    }

    /** Можно ли добавить {@code candidate} к уже набранным {@code current}, не превысив лимиты модели. */
    public static boolean canAdd(EquipmentPreset preset, List<SchemaCard> current, SchemaCard candidate) {
        Counts now = count(current);
        Counts add = count(List.of(candidate));
        return withinLimits(preset, new Counts(now.input() + add.input(), now.output() + add.output()));
    }

    public static boolean withinLimits(EquipmentPreset preset, Counts counts) {
        return (preset.getMaxInputCards() == null || counts.input() <= preset.getMaxInputCards())
                && (preset.getMaxOutputCards() == null || counts.output() <= preset.getMaxOutputCards());
    }

    /** Описание превышения лимита для сообщения пользователю; {@code null} — всё в пределах. */
    public static String problem(EquipmentPreset preset, List<SchemaCard> cards) {
        Counts c = count(cards);
        StringBuilder sb = new StringBuilder();
        if (preset.getMaxInputCards() != null && c.input() > preset.getMaxInputCards()) {
            sb.append("входных карт ").append(c.input()).append(" (максимум ").append(preset.getMaxInputCards())
                    .append(')');
        }
        if (preset.getMaxOutputCards() != null && c.output() > preset.getMaxOutputCards()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("выходных карт ").append(c.output()).append(" (максимум ").append(preset.getMaxOutputCards())
                    .append(')');
        }
        return sb.length() == 0 ? null : "У «" + preset.getName() + "» превышен лимит: " + sb;
    }

    /** Строка счётчиков для диалога сборки: «Входные 2/4 · Выходные 1/2» ({@code ∞} — лимита нет). */
    public static String summary(EquipmentPreset preset, List<SchemaCard> cards) {
        Counts c = count(cards);
        return "Входные " + c.input() + "/" + limit(preset.getMaxInputCards())
                + " · Выходные " + c.output() + "/" + limit(preset.getMaxOutputCards());
    }

    private static String limit(Integer max) {
        return max == null ? "∞" : String.valueOf(max);
    }
}
