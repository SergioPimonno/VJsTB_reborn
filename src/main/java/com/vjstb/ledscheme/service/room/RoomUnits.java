package com.vjstb.ledscheme.service.room;

import java.util.Locale;

/** Единицы и константы Планера пультовой. Физика библиотеки — миллиметры, геометрия плана —
 *  сантиметры (см. {@link RoomItemSpec}); 1U = 44,45 мм, лицевая панель 19″ = 482,6 мм. */
public final class RoomUnits {

    /** Высота одного юнита, см. */
    public static final double U_CM = 4.445;
    /** Ширина лицевой панели 19″, см. */
    public static final double RACK_WIDTH_CM = 48.26;
    /** Сетка станков, см. */
    public static final double DECK_GRID_CM = 50;
    /** Шаг перетаскивания, см. */
    public static final double MOVE_STEP_CM = 5;
    /** Привязка мелочи к трассе, см. */
    public static final double SNAP_RUN_CM = 30;
    /** Привязка точек трассы к стенам, см. */
    public static final double WALL_SNAP_CM = 12;

    private RoomUnits() {
    }

    public static double mmToCm(double mm) {
        return mm / 10.0;
    }

    /** Число с запятой, без лишних нулей: 4,6 · 482,6 · 89. */
    public static String num(double v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s.replace('.', ',');
    }
}
