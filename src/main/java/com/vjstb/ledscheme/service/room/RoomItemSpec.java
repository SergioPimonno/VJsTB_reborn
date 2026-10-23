package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomOverrides;
import java.util.ArrayList;
import java.util.List;

/**
 * Позиция библиотеки, приведённая к тому, что нужно Планнеру пультовой: тип, размеры в
 * САНТИМЕТРАХ, юниты, доля ширины, вес и мощность. Раскладка ({@link
 * com.vjstb.ledscheme.model.ControlRoomLayout}) хранит только ссылки {@link RoomLibRef}, а
 * этот record строится в момент расчёта из библиотеки каталогом ({@link RoomCatalog}) —
 * физика библиотеки хранится в миллиметрах ({@code PhysicalSpec}, {@code CaseType}), а
 * геометрия планнера — в сантиметрах, пересчёт делается ровно один раз, здесь и в адаптере.
 *
 * <p>{@code missing} — чего не хватает позиции для безопасной постановки (пусто — габариты
 * достаточны). Такие позиции Планнер помечает цветом и предлагает указать габариты
 * (запрос пользователя 2026-10-23); расчёт при этом идёт с условными размерами.
 */
public record RoomItemSpec(RoomLibRef ref, Type type, Role role, String name, String category, double widthCm,
                           double heightCm, double depthCm, int rackUnits, int widthTwelfths, double innerDepthCm,
                           double weightKg, double powerW, Double diagonalIn, List<String> missing) {

    /** Как позиция ведёт себя на плане. */
    public enum Type {
        /** Рэк-кофр (слои башни). */
        CASE,
        /** Блок в стойку (в слот кофра или в стопку). */
        RACK,
        /** Мелочь (конвертеры и т.п.): на трассе, свободно или в кейсе. */
        LOOSE,
        /** Мебель на полу. */
        FURNITURE,
        /** Напольный станок (платформа). */
        DECK,
        /** Монитор. */
        MONITOR
    }

    /** Роль мебели — стол/тумба держит башни и мониторы, стул нет. */
    public enum Role {
        TABLE, CHAIR, OTHER
    }

    public RoomItemSpec {
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    public boolean complete() {
        return missing.isEmpty();
    }

    /** Те же параметры с переопределениями объекта на плане («Применить к этому объекту»). */
    public RoomItemSpec withOverrides(RoomOverrides o) {
        if (o == null) {
            return this;
        }
        return new RoomItemSpec(ref, type, role, o.getName() != null ? o.getName() : name, category,
                o.getWidthCm() != null ? o.getWidthCm() : widthCm,
                o.getHeightCm() != null ? o.getHeightCm() : heightCm,
                o.getDepthCm() != null ? o.getDepthCm() : depthCm, rackUnits, widthTwelfths, innerDepthCm,
                o.getWeightKg() != null ? o.getWeightKg() : weightKg,
                o.getPowerW() != null ? o.getPowerW() : powerW, diagonalIn, missing);
    }

    // ---- фабрики (каталоги и тесты) ----

    /** Блок в стойку: высота из юнитов, ширина — доля 19″. */
    public static RoomItemSpec rack(RoomLibRef ref, String name, int units, double depthCm, double kg, double powerW,
                                    int widthTwelfths) {
        return new RoomItemSpec(ref, Type.RACK, Role.OTHER, name, "", RoomUnits.RACK_WIDTH_CM * widthTwelfths / 12.0,
                units * RoomUnits.U_CM, depthCm, units, widthTwelfths, 0, kg, powerW, null, List.of());
    }

    public static RoomItemSpec loose(RoomLibRef ref, String name, double w, double h, double d, double kg,
                                     double powerW) {
        return new RoomItemSpec(ref, Type.LOOSE, Role.OTHER, name, "", w, h, d, 0, 12, 0, kg, powerW, null,
                List.of());
    }

    /** Рэк-кофр: внешние размеры, число юнитов и внутренняя глубина. */
    public static RoomItemSpec caseOf(RoomLibRef ref, String name, int units, double innerDepthCm, double outerW,
                                      double outerH, double outerD, double kg) {
        return new RoomItemSpec(ref, Type.CASE, Role.OTHER, name, "", outerW, outerH, outerD, units, 12, innerDepthCm,
                kg, 0, null, List.of());
    }

    public static RoomItemSpec furniture(RoomLibRef ref, String name, Role role, double w, double d, double h,
                                         double kg) {
        return new RoomItemSpec(ref, Type.FURNITURE, role, name, "", w, h, d, 0, 12, 0, kg, 0, null, List.of());
    }

    public static RoomItemSpec deck(RoomLibRef ref, String name, double w, double d, double h, double kg) {
        return new RoomItemSpec(ref, Type.DECK, Role.OTHER, name, "", w, h, d, 0, 12, 0, kg, 0, null, List.of());
    }

    public static RoomItemSpec monitor(RoomLibRef ref, String name, double diagonalIn, double w, double d, double h,
                                       double kg, double powerW) {
        return new RoomItemSpec(ref, Type.MONITOR, Role.OTHER, name, "", w, h, d, 0, 12, 0, kg, powerW, diagonalIn,
                List.of());
    }

    /** Позиция, которой в библиотеке не нашлось (удалена или не синхронизирована): условные
     *  размеры, чтобы раскладка не падала, и пометка, что габариты не указаны. */
    public static RoomItemSpec unknown(RoomLibRef ref) {
        List<String> miss = new ArrayList<>();
        miss.add("позиция не найдена в библиотеке");
        return new RoomItemSpec(ref, Type.LOOSE, Role.OTHER, "Неизвестная позиция", "", 10, 5, 10, 0, 12, 0, 0, 0, null,
                miss);
    }
}
