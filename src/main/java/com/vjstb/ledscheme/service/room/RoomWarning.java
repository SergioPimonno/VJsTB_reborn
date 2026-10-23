package com.vjstb.ledscheme.service.room;

/**
 * Замечание по раскладке для нижнего окна «Замечания»: уровень, текст по-русски (тексты
 * перенесены из прототипа буквально, см. PLAN.md раздел 7.6) и цель — объект, который надо
 * выделить по клику. {@code target == null} — замечание общее.
 */
public record RoomWarning(Level level, String text, Target target) {

    public enum Level {
        /** Ошибка: так физически нельзя (пересечение, «висит в воздухе», не помещается). */
        ERROR,
        /** Предупреждение: возможно, но стоит проверить. */
        WARNING,
        /** К сведению. */
        INFO
    }

    /** Объект, к которому относится замечание: {@code type} — tower/furn/ritem/sitem/loose/run. */
    public record Target(String type, String id, String towerId) {
    }

    public static RoomWarning error(String text, Target target) {
        return new RoomWarning(Level.ERROR, text, target);
    }

    public static RoomWarning warning(String text, Target target) {
        return new RoomWarning(Level.WARNING, text, target);
    }

    public static RoomWarning info(String text, Target target) {
        return new RoomWarning(Level.INFO, text, target);
    }
}
