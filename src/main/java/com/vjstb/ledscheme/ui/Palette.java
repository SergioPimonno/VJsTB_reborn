package com.vjstb.ledscheme.ui;

import java.awt.Color;

/** Цвета интерфейса и схемы (согласованы с прежним тёмным веб-клиентом). */
public final class Palette {

    private Palette() {
    }

    public static final Color BG = new Color(0x0d1117);
    public static final Color PANEL = new Color(0x161b22);
    public static final Color BORDER = new Color(0x30363d);
    public static final Color TEXT = new Color(0xe6edf3);
    public static final Color MUTED = new Color(0x7d8590);
    public static final Color ACCENT = new Color(0x58a6ff);

    public static final Color PHASE_NONE = new Color(0x1c2128);
    public static final Color PHASE1 = new Color(247, 129, 102);
    public static final Color PHASE2 = new Color(63, 185, 80);
    public static final Color PHASE3 = new Color(0xd2a8ff);

    public static final Color[] SIGNAL = {
            new Color(0xffa657), new Color(0x79c0ff), new Color(0x56d364), new Color(0xf778ba),
            new Color(0xe3b341), new Color(0x76e3ea), new Color(0xd2a8ff), new Color(0xff7b72)
    };

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
}
