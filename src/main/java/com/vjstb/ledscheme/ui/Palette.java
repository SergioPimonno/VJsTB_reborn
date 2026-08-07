package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.UserProfile;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Цвета интерфейса и схемы. BG/PANEL/BORDER/TEXT/MUTED привязаны к теме (тёмная/
 * светлая) и не персонализируются; PHASE1-3/PHASE_NONE/ACCENT/SIGNAL — переопределяются
 * пользовательским профилем через {@link #applyProfile(UserProfile)} (палитра цветов
 * цепочек питания/сигнала и блоков, см. меню «Персонализация»).
 */
public final class Palette {

    private Palette() {
    }

    public static final Color BG = new Color(0x0d1117);
    public static final Color PANEL = new Color(0x161b22);
    public static final Color BORDER = new Color(0x30363d);
    public static final Color TEXT = new Color(0xe6edf3);
    public static final Color MUTED = new Color(0x7d8590);
    /** Предупреждение о перегрузке (Task #81/#87) — фиксированный цвет, не персонализируется. */
    public static final Color WARN = new Color(0xf0883e);
    /** Метка "общий элемент библиотеки" (см. NamedRenderer) — фиксированный цвет, НЕ
     *  персонализируется (в отличие от ACCENT, который меняется профилем для цепочек/
     *  схемы) — иначе смена личного акцента незаметно поменяла бы смысл этой метки. */
    public static final Color LIBRARY_SHARED = new Color(0x58a6ff);

    private static final Color DEFAULT_ACCENT = new Color(0x58a6ff);
    private static final Color DEFAULT_PHASE_NONE = new Color(0x1c2128);
    private static final Color DEFAULT_PHASE1 = new Color(247, 129, 102);
    private static final Color DEFAULT_PHASE2 = new Color(63, 185, 80);
    private static final Color DEFAULT_PHASE3 = new Color(0xd2a8ff);
    private static final Color[] DEFAULT_SIGNAL = {
            new Color(0xffa657), new Color(0x79c0ff), new Color(0x56d364), new Color(0xf778ba),
            new Color(0xe3b341), new Color(0x76e3ea), new Color(0xd2a8ff), new Color(0xff7b72)
    };

    public static Color ACCENT = DEFAULT_ACCENT;
    public static Color PHASE_NONE = DEFAULT_PHASE_NONE;
    public static Color PHASE1 = DEFAULT_PHASE1;
    public static Color PHASE2 = DEFAULT_PHASE2;
    public static Color PHASE3 = DEFAULT_PHASE3;
    public static Color[] SIGNAL = DEFAULT_SIGNAL;

    public static Color phaseColor(int phase) {
        return switch (phase) {
            case 1 -> PHASE1;
            case 2 -> PHASE2;
            case 3 -> PHASE3;
            default -> PHASE_NONE;
        };
    }

    public static Color signalColor(int index) {
        return SIGNAL[Math.floorMod(index, SIGNAL.length)];
    }

    public static Color defaultAccent() {
        return DEFAULT_ACCENT;
    }

    public static Color defaultPhaseNone() {
        return DEFAULT_PHASE_NONE;
    }

    public static Color defaultPhase(int phase) {
        return switch (phase) {
            case 1 -> DEFAULT_PHASE1;
            case 2 -> DEFAULT_PHASE2;
            case 3 -> DEFAULT_PHASE3;
            default -> DEFAULT_PHASE_NONE;
        };
    }

    public static Color[] defaultSignalColors() {
        return DEFAULT_SIGNAL.clone();
    }

    /** Переносит цвета из профиля (null-поля профиля — оставляют встроенное значение). */
    public static void applyProfile(UserProfile p) {
        ACCENT = p.getAccentColor() != null ? new Color(p.getAccentColor()) : DEFAULT_ACCENT;
        PHASE_NONE = p.getPhaseNoneColor() != null ? new Color(p.getPhaseNoneColor()) : DEFAULT_PHASE_NONE;
        PHASE1 = p.getPhase1Color() != null ? new Color(p.getPhase1Color()) : DEFAULT_PHASE1;
        PHASE2 = p.getPhase2Color() != null ? new Color(p.getPhase2Color()) : DEFAULT_PHASE2;
        PHASE3 = p.getPhase3Color() != null ? new Color(p.getPhase3Color()) : DEFAULT_PHASE3;
        if (p.getSignalColors() != null && !p.getSignalColors().isEmpty()) {
            List<Color> cols = new ArrayList<>();
            for (Integer rgb : p.getSignalColors()) {
                cols.add(new Color(rgb));
            }
            SIGNAL = cols.toArray(new Color[0]);
        } else {
            SIGNAL = DEFAULT_SIGNAL.clone();
        }
    }
}
