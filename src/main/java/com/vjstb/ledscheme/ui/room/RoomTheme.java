package com.vjstb.ledscheme.ui.room;

import com.formdev.flatlaf.util.UIScale;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.ui.Palette;
import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/**
 * Цвета и размеры Планнера пультовой, выведенные из оформления основного приложения: фон, панели, рамки, текст и
 * акцент берутся из {@link Palette} (тёмная/светлая тема и акцентный цвет профиля), шрифты — от текущего L&amp;F
 * (шрифт и масштаб интерфейса из «Персонализации»). Собственные только цвета предметной области — корпус кофров,
 * лицевые панели блоков, станки, трассы по типам: они нужны, чтобы рисунок читался как оборудование, но подобраны в
 * тон палитре приложения (GitHub-подобной).
 *
 * <p>Тема пересобирается при смене {@link Palette#BG} или {@link Palette#ACCENT} — живое переключение темы в меню
 * «Персонализация» подхватывается при следующей отрисовке.
 */
public final class RoomTheme {

    public final boolean dark;
    /** Фон рабочей зоны вокруг плана. */
    public final Color panel;
    /** Пол площадки и карточки башен. */
    public final Color floor;
    public final Color card;
    public final Color grid;
    public final Color wall;
    public final Color stage;
    public final Color stageInk;
    public final Color furn;
    public final Color deck;
    public final Color deckLine;
    public final Color tableDeck;
    public final Color tableDeckLine;
    public final Color ink;
    public final Color muted;
    public final Color line;
    public final Color accent;
    public final Color accentSoft;
    public final Color warn;
    public final Color warnBg;
    public final Color err;
    public final Color errBg;
    public final Color ok;
    public final Color caseBody;
    public final Color caseInk;
    public final Color rail;
    public final Color slot;
    public final Color slotLine;
    public final Color face;
    public final Color faceInk;
    public final Color hole;
    public final Color video;
    public final Color net;
    public final Color pwr;
    public final Color misc;
    /** Цвет текста на акцентной заливке. */
    public final Color onAccent;

    private final Color optic;
    private final Color sdi;

    private static RoomTheme cached;
    private static Color cachedBg;
    private static Color cachedAccent;

    private RoomTheme() {
        dark = Palette.isDark();
        panel = Palette.BG;
        floor = Palette.PANEL;
        card = dark ? mix(Palette.PANEL, Palette.BG, 0.0) : Palette.BG;
        grid = mix(Palette.PANEL, Palette.BORDER, dark ? 0.55 : 0.7);
        wall = Palette.MUTED;
        stage = dark ? c(0x010409) : c(0x24292f);
        stageInk = c(0xe6edf3);
        furn = dark ? c(0x2d333b) : c(0xdde3ea);
        deck = dark ? c(0x30363d) : c(0xd0d7de);
        deckLine = dark ? c(0x6e7681) : c(0x8c959f);
        tableDeck = dark ? c(0x5a4a1f) : c(0xeed9a0);
        tableDeckLine = dark ? c(0xd29922) : c(0xb08a2e);
        ink = Palette.TEXT;
        muted = Palette.MUTED;
        line = Palette.BORDER;
        accent = Palette.ACCENT;
        accentSoft = mix(Palette.BG, Palette.ACCENT, dark ? 0.28 : 0.2);
        warn = dark ? c(0xd29922) : c(0x9a6700);
        warnBg = mix(Palette.BG, dark ? c(0xd29922) : c(0xd4a72c), dark ? 0.22 : 0.3);
        err = dark ? c(0xf85149) : c(0xcf222e);
        errBg = mix(Palette.BG, err, 0.2);
        ok = dark ? c(0x3fb950) : c(0x1a7f37);
        caseBody = dark ? c(0x262c36) : c(0x2b3138);
        caseInk = c(0xd0d7de);
        rail = dark ? c(0x6e7681) : c(0x8c959f);
        slot = dark ? c(0x010409) : c(0x0d1117);
        slotLine = dark ? c(0x21262d) : c(0x30363d);
        face = dark ? c(0xa5adb7) : c(0xd0d7de);
        faceInk = c(0x0d1117);
        hole = dark ? c(0x484f58) : c(0x57606a);
        video = dark ? c(0x58a6ff) : c(0x0969da);
        net = dark ? c(0x3fb950) : c(0x1a7f37);
        pwr = dark ? c(0xf85149) : c(0xcf222e);
        misc = dark ? c(0xbc8cff) : c(0x8250df);
        onAccent = readableOn(Palette.ACCENT);
        optic = dark ? c(0xd29922) : c(0xbf8700);
        sdi = dark ? c(0xf0883e) : c(0xbc4c00);
    }

    /** Чёрный или белый — что лучше читается на заливке {@code bg}. */
    public static Color readableOn(Color bg) {
        double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return lum > 0.6 ? new Color(0x0d1117) : Color.WHITE;
    }

    private static Color c(int rgb) {
        return new Color(rgb);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Тема по текущему оформлению приложения. */
    public static synchronized RoomTheme current() {
        if (cached == null || cachedBg != Palette.BG || cachedAccent != Palette.ACCENT) {
            cached = new RoomTheme();
            cachedBg = Palette.BG;
            cachedAccent = Palette.ACCENT;
        }
        return cached;
    }

    /** Размер в пикселях с учётом масштаба интерфейса приложения. */
    public static int scale(int px) {
        return UIScale.scale(px);
    }

    /** Размер шрифта относительно базового шрифта приложения: {@code 12} — как у обычного текста. */
    public static float fontSize(float relativeTo12) {
        Font base = UIManager.getFont("Label.font");
        float b = base != null ? base.getSize2D() : 12f;
        return Math.max(8f, b * relativeTo12 / 12f);
    }

    /** Цвет трассы по типу (токены {@code --c-optic}, {@code --c-sdi}…). */
    public Color runColor(RoomRunKind kind) {
        return switch (kind) {
            case OPTIC -> optic;
            case SDI -> sdi;
            case NET -> net;
            case PWR -> pwr;
            case MISC -> misc;
        };
    }

    /**
     * Цвет блока по смыслу: питание, сеть, видео или прочее. Категории библиотеки свободные, поэтому
     * определяется по словам в категории и названии — ошибка тут косметическая (другая полоска цвета),
     * на расчёты не влияет.
     */
    public Color categoryColor(RoomItemSpec s) {
        String t = ((s.category() == null ? "" : s.category()) + " " + s.name()).toLowerCase(java.util.Locale.ROOT);
        if (has(t, "питан", "pdu", "ибп", "ups", "розет", "силов")) {
            return pwr;
        }
        if (has(t, "сеть", "коммутатор", "свитч", "switch", "роутер", "патч", "оптич", "ethernet")) {
            return net;
        }
        if (has(t, "процессор", "контроллер", "видео", "сервер", "конвертер", "микшер", "мультивьюер", "hdmi", "sdi",
                "led", "медиа")) {
            return video;
        }
        return misc;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
