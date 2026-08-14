package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.LookAndFeel;

/** Варианты отрисовки FlatLaf, доступные в персонализации — все входят в уже
 *  подключённый основной артефакт {@code flatlaf} (отдельная зависимость
 *  {@code flatlaf-intellij-themes} не нужна, та несёт только сторонние темы).
 *  {@code dark} здесь определяет, какой бакет цветов Palette.BG/PANEL/BORDER/
 *  TEXT/MUTED используется (см. {@link Palette#applyTheme}) — Darcula/IntelliJ
 *  не подменяют этот бакет своим, используют тот же тёмный/светлый набор, что и
 *  стандартные Dark/Light варианты (наши собственные панели красятся Palette,
 *  а не читают цвета из UIManager). */
public enum LafStyle {
    FLAT_DARK("flatdark", "Тёмная (стандарт)", true),
    DARCULA("darcula", "Тёмная (Darcula)", true),
    FLAT_LIGHT("flatlight", "Светлая (стандарт)", false),
    INTELLIJ("intellij", "Светлая (IntelliJ)", false);

    private final String id;
    private final String label;
    private final boolean dark;

    LafStyle(String id, String label, boolean dark) {
        this.id = id;
        this.label = label;
        this.dark = dark;
    }

    public String getId() {
        return id;
    }

    public boolean isDark() {
        return dark;
    }

    @Override
    public String toString() {
        return label;
    }

    public LookAndFeel createLaf() {
        return switch (this) {
            case DARCULA -> new FlatDarculaLaf();
            case FLAT_LIGHT -> new FlatLightLaf();
            case INTELLIJ -> new FlatIntelliJLaf();
            default -> new FlatDarkLaf();
        };
    }

    public static LafStyle byId(String id) {
        for (LafStyle s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return FLAT_DARK;
    }
}
