package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomUnits;

/**
 * Подписи размеров в выбранных единицах (R2 плана: «размеры в сантиметрах И в юнитах», переключатель
 * «U / см / U + см»). Юниты имеют смысл только у блоков в стойку и кофров; у остальных предметов подпись всегда в
 * сантиметрах.
 */
public final class RoomUnitsFormat {

    /** Режим подписей. */
    public enum Mode {
        U("U"), CM("см"), BOTH("U + см");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private RoomUnitsFormat() {
    }

    /** Высота башни или слоя: «94 см», «14U» или «94 см · 14U». */
    public static String height(Mode m, double cm, int units) {
        String c = RoomUnits.num(cm, 0) + " см";
        if (units <= 0) {
            return c;
        }
        return switch (m) {
            case CM -> c;
            case U -> units + "U";
            case BOTH -> c + " · " + units + "U";
        };
    }

    /** Размеры блока или предмета для подписи на карточке. */
    public static String block(Mode m, RoomItemSpec s) {
        String cm = RoomUnits.num(s.widthCm(), 1) + "×" + RoomUnits.num(s.heightCm(), 1) + "×" + RoomUnits.num(s.depthCm(), 1)
                + " см";
        if (s.type() != RoomItemSpec.Type.RACK) {
            return cm;
        }
        String u = s.rackUnits() + "U" + (s.widthTwelfths() < 12 ? " " + s.widthTwelfths() + "/12" : "") + " · гл. "
                + RoomUnits.num(s.depthCm(), 0);
        return switch (m) {
            case U -> u;
            case CM -> cm;
            case BOTH -> u + " · " + cm;
        };
    }

    /** Подпись кофра в шапке слоя. */
    public static String caseHead(Mode m, RoomItemSpec k) {
        String cm = RoomUnits.num(k.widthCm(), 0) + "×" + RoomUnits.num(k.heightCm(), 0) + "×" + RoomUnits.num(k.depthCm(), 0)
                + " см";
        String u = "Кофр " + k.rackUnits() + "U · гл. " + RoomUnits.num(k.innerDepthCm(), 0);
        return switch (m) {
            case U -> u;
            case CM -> "Кофр " + cm;
            case BOTH -> u + " · " + cm;
        };
    }
}
