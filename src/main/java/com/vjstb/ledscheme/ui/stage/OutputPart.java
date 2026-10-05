package com.vjstb.ledscheme.ui.stage;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Части вывода этапа «Вывод» (запрос 2026-10-05): каждая — строка таблицы «Состав вывода» с галочкой
 * «в общий вывод» и своей кнопкой «Экспортировать». Пакет документации выгружает ровно отмеченные части,
 * отдельная кнопка — одну; код один и тот же ({@code OutputStagePanel#runExport}).
 *
 * <p>{@link #defaultOn} — состав пакета ДО появления галочек: питание, сигнал, маски, транспорт, сводка и
 * спецификация. Таблица экранов и пресеты (Resolume, After Effects) выключены, пока пользователь сам их не
 * отметил, чтобы привычный пакет не разросся неожиданными файлами.
 */
public enum OutputPart {
    POWER("Питание — схемы расключения", true),
    SIGNAL("Сигнал — схемы расключения", true),
    MASKS("Маски экранов и канвасов", true),
    TRANSPORT("Транспорт — схемы загрузки машин", true),
    SCREENS("Экраны — таблица экранов", false),
    RESOLUME("Пресеты Resolume", false),
    AFTER_EFFECTS("Пресеты After Effects", false),
    REPORT("Сводка по проекту", true),
    SPEC("Спецификация оборудования", true);

    private final String label;
    private final boolean defaultOn;

    OutputPart(String label, boolean defaultOn) {
        this.label = label;
        this.defaultOn = defaultOn;
    }

    public String label() {
        return label;
    }

    /** Части, входящие в общий вывод, пока выбор не сохранён в профиле. */
    public static Set<OutputPart> defaults() {
        Set<OutputPart> result = EnumSet.noneOf(OutputPart.class);
        for (OutputPart p : values()) {
            if (p.defaultOn) {
                result.add(p);
            }
        }
        return result;
    }

    /** Сохранённый в профиле выбор (имена частей); {@code null} — выбор не делали, берутся
     *  {@link #defaults()}. Неизвестные имена (профиль от будущей версии) пропускаются. */
    public static Set<OutputPart> fromNames(List<String> names) {
        if (names == null) {
            return defaults();
        }
        Set<OutputPart> result = EnumSet.noneOf(OutputPart.class);
        for (String n : names) {
            for (OutputPart p : values()) {
                if (p.name().equals(n)) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    public static List<String> toNames(Set<OutputPart> parts) {
        List<String> names = new java.util.ArrayList<>();
        for (OutputPart p : values()) {
            if (parts.contains(p)) {
                names.add(p.name());
            }
        }
        return names;
    }
}
