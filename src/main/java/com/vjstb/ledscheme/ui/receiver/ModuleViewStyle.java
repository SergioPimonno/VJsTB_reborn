package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.ui.Palette;
import java.awt.Color;

/**
 * Цвета рендера видов «Приёмные карты» и «Блоки питания». Вынесены из кода рисования, потому что одни и те же
 * холсты рисуют и экран приложения ({@link #live()} — по теме и профилю пользователя), и печатную «Карту
 * подключения» ({@link #print()} — светлый фон независимо от темы, чтобы схема на бумаге не зависела от того,
 * в какой теме сидел автор): рендер получает стиль параметром и не читает {@link Palette} сам.
 *
 * <p>Состояния окрашены одинаково во всех видах: оранжевый — «нет карты / нет питания», красный — нарушение лимита
 * (перегруз выходов, длинный шлейф или кабель), цвет «Приёмных карт» из «Цветов и профилей» — карты.
 */
public final class ModuleViewStyle {

    public final Color background;
    public final Color moduleFill;
    public final Color moduleStroke;
    public final Color ordinaryFill;
    public final Color ordinaryStroke;
    public final Color text;
    public final Color mutedText;
    public final Color orphanFill;
    public final Color orphanStroke;
    public final Color selectionFill;
    public final Color selectionStroke;
    public final Color problem;
    public final Color problemText;
    public final Color card;
    public final Color cardStroke;
    public final Color cardActiveStroke;
    public final Color plateText;
    /** Цвета областей блоков питания по кругу (БП1 — первый, БП5 — снова первый). */
    public final Color[] areas;

    private ModuleViewStyle(boolean dark, Color background, Color text, Color mutedText, Color card) {
        this.background = background;
        this.text = text;
        this.mutedText = mutedText;
        this.card = card;
        this.cardStroke = darker(card, 0.78);
        this.cardActiveStroke = darker(card, 0.5);
        this.moduleFill = dark ? new Color(0x3b434d) : new Color(0xb4b2a9);
        this.moduleStroke = dark ? new Color(0x6e7681) : new Color(0x888780);
        this.ordinaryFill = new Color(0x5f5e5a);
        this.ordinaryStroke = new Color(0x444441);
        // Модули без карты/блока — лёгкая оранжевая подсветка, а не сплошная заливка: пока ничего не назначено, без
        // питания весь экран, и плотный оранжевый превращал вид в сплошное пятно (баг-репорт: «тяжело воспринимается»)
        this.orphanFill = dark ? new Color(240, 136, 62, 38) : new Color(216, 90, 48, 34);
        this.orphanStroke = dark ? new Color(240, 136, 62, 150) : new Color(216, 90, 48, 150);
        this.selectionFill = dark ? new Color(0x2f5d8f) : new Color(0xb5d4f4);
        this.selectionStroke = dark ? new Color(0x58a6ff) : new Color(0x185fa5);
        this.problem = new Color(0xe24b4a);
        this.problemText = dark ? new Color(0xff7b72) : new Color(0xa32d2d);
        this.plateText = new Color(0xeaf3de);
        this.areas = new Color[]{new Color(0xba7517), new Color(0x7f77dd), new Color(0x1d9e75), new Color(0xd4537e)};
    }

    /** Стиль окна приложения: тема и цвет «Приёмных карт» профиля. */
    public static ModuleViewStyle live() {
        boolean dark = Palette.isDark();
        return new ModuleViewStyle(dark, Palette.BG, Palette.TEXT, Palette.MUTED, Palette.RECEIVER_CARD);
    }

    /** Печатный стиль: белый фон, тёмный текст; цвет карт — тот, что выбран в профиле. */
    public static ModuleViewStyle print() {
        return new ModuleViewStyle(false, Color.WHITE, new Color(0x2c2c2a), new Color(0x5f5e5a), Palette.RECEIVER_CARD);
    }

    /** Цвет области блока №{@code index} (с 0). */
    public Color area(int index) {
        return areas[Math.floorMod(index, areas.length)];
    }

    /** Заливка клетки области блока: цвет области, смешанный с фоном модуля, — читаем и на светлой, и на тёмной теме. */
    public Color areaFill(int index) {
        return blend(moduleFill, area(index), 0.55);
    }

    /** Подпись поверх заливки области (номер шлейфа): тёмная на светлой заливке, светлая на тёмной. */
    public Color areaText(int index) {
        Color f = areaFill(index);
        double lum = (0.299 * f.getRed() + 0.587 * f.getGreen() + 0.114 * f.getBlue()) / 255.0;
        return lum > 0.55 ? new Color(0x2c2c2a) : Color.WHITE;
    }

    public static Color darker(Color c, double k) {
        return new Color((int) (c.getRed() * k), (int) (c.getGreen() * k), (int) (c.getBlue() * k));
    }

    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.max(0, Math.min(1, a))));
    }

    /** {@code base·(1−t) + top·t}. */
    public static Color blend(Color base, Color top, double t) {
        return new Color(
                (int) Math.round(base.getRed() * (1 - t) + top.getRed() * t),
                (int) Math.round(base.getGreen() * (1 - t) + top.getGreen() * t),
                (int) Math.round(base.getBlue() * (1 - t) + top.getBlue() * t));
    }
}
