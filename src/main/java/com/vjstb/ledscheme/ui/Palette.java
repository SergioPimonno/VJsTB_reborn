package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.UserProfile;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Цвета интерфейса и схемы. BG/PANEL/BORDER/TEXT/MUTED переключаются темой (тёмная/
 * светлая, см. {@link #applyTheme(boolean)}) — сами по себе не персонализируются
 * (нет отдельного пользовательского оверрайда, в отличие от акцента); PHASE1-3/
 * PHASE_NONE/ACCENT/SIGNAL — переопределяются пользовательским профилем через
 * {@link #applyProfile(UserProfile)} (палитра цветов цепочек питания/сигнала и
 * блоков, см. меню «Персонализация»). Обе оси независимы: тема и акцентные цвета
 * можно менять по отдельности.
 */
public final class Palette {

    private Palette() {
    }

    private static final Color DARK_BG = new Color(0x0d1117);
    private static final Color DARK_PANEL = new Color(0x161b22);
    private static final Color DARK_BORDER = new Color(0x30363d);
    private static final Color DARK_TEXT = new Color(0xe6edf3);
    private static final Color DARK_MUTED = new Color(0x7d8590);

    /** Светлый вариант — цвета в духе GitHub Light (тот же источник, что и
     *  тёмный набор выше — GitHub Dark), чтобы контраст текст/фон/бордер был
     *  выверен так же, а не подобран на глаз. */
    private static final Color LIGHT_BG = new Color(0xffffff);
    private static final Color LIGHT_PANEL = new Color(0xf6f8fa);
    private static final Color LIGHT_BORDER = new Color(0xd0d7de);
    private static final Color LIGHT_TEXT = new Color(0x1f2328);
    private static final Color LIGHT_MUTED = new Color(0x656d76);

    public static Color BG = DARK_BG;
    public static Color PANEL = DARK_PANEL;
    public static Color BORDER = DARK_BORDER;
    public static Color TEXT = DARK_TEXT;
    public static Color MUTED = DARK_MUTED;
    /** Ячейки (кабинеты) в картинках схем: заливка площади экрана, заливка по умолчанию на схеме расключения и контур.
     *  Следуют теме ({@link #applyTheme}), но в ПЕЧАТНОМ экспорте ({@link #withLightTheme}) кабинет остаётся
     *  тёмным с хорошо видным контуром: белая заливка с бледным контуром на белом листе сливалась (отзыв 2026-10-24). */
    public static Color CELL_BG = DARK_BG;
    public static Color CELL_FILL = DARK_PANEL;
    public static Color CELL_BORDER = DARK_BORDER;
    /** Печатный экспорт: тёмный кабинет и светло-серый контур, различимый на тёмной заливке. */
    private static final Color PRINT_CELL_FILL = new Color(0x2d333b);
    private static final Color PRINT_CELL_BORDER = new Color(0x9aa4ad);
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

    /** Заливка приёмных карт поверх серых модулей (вид «Приёмные карты», «Расключение экрана»
     *  сигнала, «Карта подключения») — docs/receiver-cards/PLAN.md, 6.4. */
    private static final Color DEFAULT_RECEIVER_CARD = new Color(0x639922);

    public static Color ACCENT = DEFAULT_ACCENT;
    public static Color RECEIVER_CARD = DEFAULT_RECEIVER_CARD;
    public static Color PHASE_NONE = DEFAULT_PHASE_NONE;
    public static Color PHASE1 = DEFAULT_PHASE1;
    public static Color PHASE2 = DEFAULT_PHASE2;
    public static Color PHASE3 = DEFAULT_PHASE3;
    public static Color[] SIGNAL = DEFAULT_SIGNAL;

    /** Переключает BG/PANEL/BORDER/TEXT/MUTED между тёмным и светлым набором —
     *  вызывается при старте (см. App) и при живом переключении темы в меню
     *  «Персонализация» (см. MainMenuBar). Акцентные цвета (applyProfile) не
     *  трогает — независимая ось. */
    public static void applyTheme(boolean dark) {
        BG = dark ? DARK_BG : LIGHT_BG;
        PANEL = dark ? DARK_PANEL : LIGHT_PANEL;
        BORDER = dark ? DARK_BORDER : LIGHT_BORDER;
        TEXT = dark ? DARK_TEXT : LIGHT_TEXT;
        MUTED = dark ? DARK_MUTED : LIGHT_MUTED;
        CELL_BG = BG;
        CELL_FILL = PANEL;
        CELL_BORDER = BORDER;
    }

    /** Действие, выполняемое в особой теме (может бросать проверяемое исключение — рендер для экспорта). */
    @FunctionalInterface
    public interface Scoped<T, E extends Exception> {
        T get() throws E;
    }

    /** Выполняет {@code body} со СВЕТЛОЙ темой {@link #BG}/{@link #PANEL}/{@link #BORDER}/
     *  {@link #TEXT}/{@link #MUTED} и возвращает прежние значения — для печатного стиля
     *  экспорта (см. {@link ExportRendering}): схемы расключения, обзор сцены, таблицы и
     *  прочие картинки рисуют именно этими полями, так что белый фон и тёмный текст
     *  получаются без отдельного «печатного» кода в каждой. Акцентные цвета цепочек
     *  (профиль пользователя) не трогаются. */
    public static <T, E extends Exception> T withLightTheme(Scoped<T, E> body) throws E {
        Color bg = BG;
        Color panel = PANEL;
        Color border = BORDER;
        Color text = TEXT;
        Color muted = MUTED;
        Color cellBg = CELL_BG;
        Color cellFill = CELL_FILL;
        Color cellBorder = CELL_BORDER;
        applyTheme(false);
        CELL_BG = PRINT_CELL_FILL;
        CELL_FILL = PRINT_CELL_FILL;
        CELL_BORDER = PRINT_CELL_BORDER;
        try {
            return body.get();
        } finally {
            CELL_BG = cellBg;
            CELL_FILL = cellFill;
            CELL_BORDER = cellBorder;
            BG = bg;
            PANEL = panel;
            BORDER = border;
            TEXT = text;
            MUTED = muted;
        }
    }

    /** Текущая тема (тёмная/светлая) — по текущему {@link #BG}, а не отдельным
     *  флагом, чтобы не рассинхронизироваться с {@link #applyTheme}. Нужен
     *  {@code SchemaStyle.screen()} — незанятое гнездо схемы заливается базовым
     *  цветом на контраст с фоном темы (docs/schema-ports-rework/PLAN.md, отзыв
     *  пользователя 2026-09-16: пустые гнёзда неразличимы на тёмном фоне). */
    public static boolean isDark() {
        return BG.equals(DARK_BG);
    }

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

    /** Цвет из той же палитры {@link #signalColor}, СТАБИЛЬНЫЙ для данного {@code id}
     *  (хеш строки, не позиция в каком-либо списке) — годится для окраски "категории"
     *  сущности (например, типа кабинета) там, где важно, чтобы одна и та же
     *  сущность получала один и тот же цвет КАЖДЫЙ раз, а не только пока порядок
     *  окружающего списка не менялся (баг-репорт: "палитру нужно сохранять между
     *  перезапусками" — при окраске по индексу в списке цвет типа сдвигался, стоило
     *  где-то раньше по списку добавить/удалить любой ДРУГОЙ тип). */
    public static Color stableColorFor(String id) {
        return signalColor(id == null ? 0 : id.hashCode());
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
        RECEIVER_CARD = p.getReceiverCardColor() != null ? new Color(p.getReceiverCardColor())
                : DEFAULT_RECEIVER_CARD;
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
