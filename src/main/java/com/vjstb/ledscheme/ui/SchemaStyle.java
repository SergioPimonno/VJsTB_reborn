package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Все цвета/толщины холста общей схемы в ОДНОМ объекте (docs/schema-ports-rework/
 * PLAN.md, задача T3.1/§2.7/D12) — раньше были разбросаны по {@code
 * SchemaCanvasPanel} литералами и статикой {@link Palette}. Два пресета:
 * {@link #screen()} — ровно текущий вид (следует теме/акценту через живые значения
 * {@link Palette}, вызывать заново на каждой отрисовке — сам объект НЕ кэшируется,
 * т.к. пользователь может переключить тему/акцент между кадрами); {@link #print()} —
 * фиксированный «Печатный» вид (белый фон, жёлтые блоки, номинальные цвета линий),
 * как на референсных схемах пользователя из yEd (см. DIALOG.md).
 *
 * <p>Собирается через {@link Builder} с ИМЕНОВАННЫМИ вызовами, а не позиционным
 * конструктором — первая версия (30 позиционных {@code Color}-параметров подряд)
 * реально подвела: одно поле («цвет подписи гнезда», alpha 190) было по ошибке
 * сопоставлено с ДРУГИМ полем (шапка карты, alpha 150) — визуальная регрессия,
 * пойманная только пиксельным сравнением с эталоном T0.2, не компилятором (см.
 * DIALOG.md/PLAN.md, задача T3.1). Именованные сеттеры делают такую путаницу
 * практически невозможной и явно показывают, если поле забыли задать вовсе
 * ({@link Builder#build()} требует ВСЕ поля, иначе {@link NullPointerException}
 * с именем пропущенного поля — быстрее найти, чем молчаливый {@code null}).
 *
 * <p>{@link #roleLineColor}/{@link #powerNominalLineColor} — цвет связи ПО
 * УМОЛЧАНИЮ (роль гнезда-источника для сигнала, номинал разъёма для питания); это
 * ещё не подключено к отрисовке связей (см. PLAN.md, задача T4.4) — сейчас связь
 * всегда рисуется {@link #defaultEdgeColor} (в точности как раньше {@code
 * Palette.MUTED}), пользовательский цвет ({@code SchemaEdge.getColor()}) как и
 * раньше важнее всего (D9 PLAN.md).
 */
public final class SchemaStyle {

    public final Color background;
    public final Color nodeBorder;
    public final Color selectedOutline;
    public final Color pendingOutline;
    public final Color mutedText;
    public final Color titleText;
    public final Color warn;
    public final Color accent;
    public final Color defaultEdgeColor;
    public final Color labelChipBackground;
    public final Color labelChipBackgroundEmpty;
    public final Color labelChipBorder;
    public final Color labelChipText;
    public final Color labelChipTextEmpty;
    public final Color socketRingHovered;
    public final Color socketRingDefault;
    /** Заливка НЕЗАНЯТОГО гнезда — раньше такое гнездо было просто чёрным
     *  кольцом без заливки и терялось на тёмном фоне блока (отзыв пользователя
     *  2026-09-16). Базовый цвет на контраст с темой интерфейса ({@link
     *  Palette#isDark()}), не с ролью/типом — занятые гнёзда по-прежнему цветные
     *  по {@link #connectorDotColor}. */
    public final Color socketEmptyFill;
    public final Color socketLabelText;
    public final Color cabinetSocketFill;
    public final Color cabinetSocketPending;
    public final Color cabinetSocketHovered;
    public final Color cabinetSocketBorder;
    public final Color rubberBandFill;
    public final Color rubberBandBorder;
    public final Color resizeHandle;
    public final Color cardBlockBorder;
    public final Color cardBlockHeaderText;
    public final Color metaText;

    private final Map<SchemaNodeType, Color> nodeFill;
    private final Color[] connectorPalette;
    private final Map<InterfaceRole, Color> roleLineColors;
    private final SchemaStylePreset preset;

    private SchemaStyle(Builder b) {
        this.preset = require(b.preset, "preset");
        this.background = require(b.background, "background");
        this.nodeBorder = require(b.nodeBorder, "nodeBorder");
        this.selectedOutline = require(b.selectedOutline, "selectedOutline");
        this.pendingOutline = require(b.pendingOutline, "pendingOutline");
        this.mutedText = require(b.mutedText, "mutedText");
        this.titleText = require(b.titleText, "titleText");
        this.warn = require(b.warn, "warn");
        this.accent = require(b.accent, "accent");
        this.defaultEdgeColor = require(b.defaultEdgeColor, "defaultEdgeColor");
        this.labelChipBackground = require(b.labelChipBackground, "labelChipBackground");
        this.labelChipBackgroundEmpty = require(b.labelChipBackgroundEmpty, "labelChipBackgroundEmpty");
        this.labelChipBorder = require(b.labelChipBorder, "labelChipBorder");
        this.labelChipText = require(b.labelChipText, "labelChipText");
        this.labelChipTextEmpty = require(b.labelChipTextEmpty, "labelChipTextEmpty");
        this.socketRingHovered = require(b.socketRingHovered, "socketRingHovered");
        this.socketRingDefault = require(b.socketRingDefault, "socketRingDefault");
        this.socketEmptyFill = require(b.socketEmptyFill, "socketEmptyFill");
        this.socketLabelText = require(b.socketLabelText, "socketLabelText");
        this.cabinetSocketFill = require(b.cabinetSocketFill, "cabinetSocketFill");
        this.cabinetSocketPending = require(b.cabinetSocketPending, "cabinetSocketPending");
        this.cabinetSocketHovered = require(b.cabinetSocketHovered, "cabinetSocketHovered");
        this.cabinetSocketBorder = require(b.cabinetSocketBorder, "cabinetSocketBorder");
        this.rubberBandFill = require(b.rubberBandFill, "rubberBandFill");
        this.rubberBandBorder = require(b.rubberBandBorder, "rubberBandBorder");
        this.resizeHandle = require(b.resizeHandle, "resizeHandle");
        this.cardBlockBorder = require(b.cardBlockBorder, "cardBlockBorder");
        this.cardBlockHeaderText = require(b.cardBlockHeaderText, "cardBlockHeaderText");
        this.metaText = require(b.metaText, "metaText");
        this.nodeFill = require(b.nodeFill, "nodeFill");
        this.connectorPalette = require(b.connectorPalette, "connectorPalette");
        this.roleLineColors = require(b.roleLineColors, "roleLineColors");
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new NullPointerException("SchemaStyle.Builder: поле не задано — " + fieldName);
        }
        return value;
    }

    public SchemaStylePreset preset() {
        return preset;
    }

    public static SchemaStyle forPreset(SchemaStylePreset preset) {
        return preset == SchemaStylePreset.PRINT ? print() : screen();
    }

    /** "Экранный" — ровно текущий вид: читает {@link Palette} ЖИВЬЁМ на момент
     *  вызова (не кэшируется), т.к. тема/акцент могут смениться между кадрами (см.
     *  {@code MainMenuBar}/{@code PersonalizationDialog}) — как и раньше, до этой
     *  переработки, когда холст читал {@code Palette.*} статику напрямую. */
    public static SchemaStyle screen() {
        Map<SchemaNodeType, Color> fill = new EnumMap<>(SchemaNodeType.class);
        fill.put(SchemaNodeType.SOURCE, new Color(0xf78166));
        fill.put(SchemaNodeType.DISTRO, new Color(0xe3b341));
        fill.put(SchemaNodeType.CONVERTER, new Color(0x79c0ff));
        fill.put(SchemaNodeType.SERVER, new Color(0xd2a8ff));
        fill.put(SchemaNodeType.CONTROLLER, new Color(0x76e3ea));
        fill.put(SchemaNodeType.SCREEN, new Color(0x56d364));
        fill.put(SchemaNodeType.MONITOR, new Color(0xff9bce));
        fill.put(SchemaNodeType.CUSTOM, new Color(0xc0c8d0));

        Color[] connectorPalette = {
                new Color(0xf78166), new Color(0x76e3ea), new Color(0xd2a8ff), new Color(0x7ee787),
                new Color(0xffd479), new Color(0xff7b9c), new Color(0x79c0ff), new Color(0xd29922),
        };

        return new Builder()
                .preset(SchemaStylePreset.SCREEN)
                .background(Palette.BG)
                .nodeBorder(Palette.BORDER)
                .selectedOutline(Color.WHITE)
                .pendingOutline(Color.YELLOW)
                .mutedText(Palette.MUTED)
                .titleText(Color.BLACK)
                .warn(Palette.WARN)
                .accent(Palette.ACCENT)
                .defaultEdgeColor(Palette.MUTED)
                .labelChipBackground(new Color(0x0d, 0x11, 0x17, 235))
                .labelChipBackgroundEmpty(new Color(0x0d, 0x11, 0x17, 170))
                .labelChipBorder(Palette.BORDER)
                .labelChipText(Color.WHITE)
                .labelChipTextEmpty(Palette.MUTED)
                .socketRingHovered(Color.WHITE)
                .socketRingDefault(Color.BLACK)
                .socketEmptyFill(Palette.isDark() ? Color.WHITE : Color.BLACK)
                .socketLabelText(new Color(0, 0, 0, 190))
                .cabinetSocketFill(new Color(255, 221, 0, 210))
                .cabinetSocketPending(Color.YELLOW)
                .cabinetSocketHovered(Color.WHITE)
                .cabinetSocketBorder(new Color(0, 0, 0, 180))
                .rubberBandFill(new Color(Palette.ACCENT.getRed(), Palette.ACCENT.getGreen(), Palette.ACCENT.getBlue(), 40))
                .rubberBandBorder(Palette.ACCENT)
                .resizeHandle(new Color(0, 0, 0, 150))
                .cardBlockBorder(new Color(0, 0, 0, 60))
                .cardBlockHeaderText(new Color(0, 0, 0, 150))
                .metaText(new Color(0, 0, 0, 160))
                .nodeFill(fill)
                .connectorPalette(connectorPalette)
                .roleLineColors(new EnumMap<>(InterfaceRole.class)) // ещё не используется, см. T4.4
                .build();
    }

    /** "Печатный" — белый фон, жёлтые блоки с чёрной рамкой, чёрный текст, как на
     *  референсных схемах пользователя из yEd (см. DIALOG.md) — для печати/PDF.
     *  Единственный фиксированный (не следующий теме) пресет. */
    public static SchemaStyle print() {
        Map<SchemaNodeType, Color> fill = new EnumMap<>(SchemaNodeType.class);
        for (SchemaNodeType t : SchemaNodeType.values()) {
            fill.put(t, new Color(0xFF, 0xCC, 0x00));
        }
        Color[] connectorPalette = {Color.BLACK};

        Map<InterfaceRole, Color> roleLines = new EnumMap<>(InterfaceRole.class);
        roleLines.put(InterfaceRole.NETWORK, new Color(0xE0, 0x1B, 0x1B));
        roleLines.put(InterfaceRole.SYNC, new Color(0x7A, 0x1F, 0xA3));
        roleLines.put(InterfaceRole.LED_DATA, new Color(0x1F, 0x6F, 0xD1));
        roleLines.put(InterfaceRole.VIDEO, Color.BLACK);
        roleLines.put(InterfaceRole.AUDIO, Color.BLACK);
        roleLines.put(InterfaceRole.CONTROL, Color.BLACK);
        roleLines.put(InterfaceRole.OTHER, Color.BLACK);
        roleLines.put(InterfaceRole.POWER, Color.BLACK);

        return new Builder()
                .preset(SchemaStylePreset.PRINT)
                .background(Color.WHITE)
                .nodeBorder(Color.BLACK)
                .selectedOutline(new Color(0x1F, 0x6F, 0xD1))
                .pendingOutline(Color.ORANGE)
                .mutedText(new Color(0x50, 0x50, 0x50))
                .titleText(Color.BLACK)
                .warn(new Color(0xC0, 0x00, 0x00))
                .accent(Color.BLACK)
                .defaultEdgeColor(Color.BLACK)
                .labelChipBackground(new Color(0xFF, 0xFF, 0xFF, 235))
                .labelChipBackgroundEmpty(new Color(0xFF, 0xFF, 0xFF, 200))
                .labelChipBorder(Color.BLACK)
                .labelChipText(Color.BLACK)
                .labelChipTextEmpty(new Color(0x60, 0x60, 0x60))
                .socketRingHovered(Color.BLACK)
                .socketRingDefault(Color.BLACK)
                .socketEmptyFill(Color.BLACK)
                .socketLabelText(new Color(0x20, 0x20, 0x20))
                .cabinetSocketFill(Color.WHITE)
                .cabinetSocketPending(Color.YELLOW)
                .cabinetSocketHovered(Color.WHITE)
                .cabinetSocketBorder(Color.BLACK)
                .rubberBandFill(new Color(0x1F, 0x6F, 0xD1, 40))
                .rubberBandBorder(new Color(0x1F, 0x6F, 0xD1))
                .resizeHandle(Color.BLACK)
                .cardBlockBorder(Color.BLACK)
                .cardBlockHeaderText(new Color(0x30, 0x30, 0x30))
                .metaText(new Color(0x40, 0x40, 0x40))
                .nodeFill(fill)
                .connectorPalette(connectorPalette)
                .roleLineColors(roleLines)
                .build();
    }

    public Color nodeFill(SchemaNodeType type) {
        return nodeFill.getOrDefault(type, nodeFill.get(SchemaNodeType.CUSTOM));
    }

    /** Цвет точки-гнезда по типу разъёма — хэш строки типа (не роли: сохраняет
     *  ТОЧНО прежнее поведение "Экранного" пресета, где связи одного типа получали
     *  устойчивый, но произвольный цвет вне зависимости от роли). */
    public Color connectorDotColor(String connectorType) {
        int idx = Math.floorMod(connectorType == null ? 0 : connectorType.hashCode(), connectorPalette.length);
        return connectorPalette[idx];
    }

    /** Цвет связи ПО РОЛИ узла-источника гнезда, для использования как умолчание,
     *  когда у связи нет ни пользовательского цвета ({@code SchemaEdge.getColor()}),
     *  ни специального цвета по номиналу (питание, см. {@link
     *  #powerNominalLineColor}) — см. PLAN.md D9, подключение — задача T4.4.
     *  {@code null} — для этой роли в этом пресете нет отдельного цвета, брать
     *  {@link #defaultEdgeColor}. */
    public Color roleLineColor(InterfaceRole role) {
        return roleLineColors.get(role);
    }

    private static final Pattern AMPS_PATTERN = Pattern.compile("(\\d+)\\s*A\\b", Pattern.CASE_INSENSITIVE);

    /** Цвет силовой связи по номиналу разъёма (например "CEE 32A" → 32А) — только
     *  {@link #print()} различает номиналы (см. PLAN.md §2.7: "питание 125A #C000C0,
     *  63A #8B4513, 32A #E00000, 16A и прочее чёрные"); {@link #screen()} всегда
     *  {@code null} (нет такого поведения сегодня — не добавляем его в "Экранный",
     *  чтобы тот остался визуально прежним). Разрешение — задача T4.4. */
    public Color powerNominalLineColor(String connectorType) {
        if (preset != SchemaStylePreset.PRINT || connectorType == null) {
            return null;
        }
        Matcher m = AMPS_PATTERN.matcher(connectorType);
        if (!m.find()) {
            return null;
        }
        int amps = Integer.parseInt(m.group(1));
        return switch (amps) {
            case 125 -> new Color(0xC0, 0x00, 0xC0);
            case 63 -> new Color(0x8B, 0x45, 0x13);
            case 32 -> new Color(0xE0, 0x00, 0x00);
            default -> Color.BLACK;
        };
    }

    /** Собирает {@link SchemaStyle} именованными вызовами — см. javadoc класса про
     *  причину отказа от позиционного конструктора. {@link #build()} требует, чтобы
     *  ВСЕ поля были заданы (иначе {@link NullPointerException} с именем пропущенного
     *  поля) — оба пресета ({@link #screen()}/{@link #print()}) обязаны задать их все. */
    private static final class Builder {
        private SchemaStylePreset preset;
        private Color background;
        private Color nodeBorder;
        private Color selectedOutline;
        private Color pendingOutline;
        private Color mutedText;
        private Color titleText;
        private Color warn;
        private Color accent;
        private Color defaultEdgeColor;
        private Color labelChipBackground;
        private Color labelChipBackgroundEmpty;
        private Color labelChipBorder;
        private Color labelChipText;
        private Color labelChipTextEmpty;
        private Color socketRingHovered;
        private Color socketRingDefault;
        private Color socketEmptyFill;
        private Color socketLabelText;
        private Color cabinetSocketFill;
        private Color cabinetSocketPending;
        private Color cabinetSocketHovered;
        private Color cabinetSocketBorder;
        private Color rubberBandFill;
        private Color rubberBandBorder;
        private Color resizeHandle;
        private Color cardBlockBorder;
        private Color cardBlockHeaderText;
        private Color metaText;
        private Map<SchemaNodeType, Color> nodeFill;
        private Color[] connectorPalette;
        private Map<InterfaceRole, Color> roleLineColors;

        Builder preset(SchemaStylePreset v) {
            this.preset = v;
            return this;
        }

        Builder background(Color v) {
            this.background = v;
            return this;
        }

        Builder nodeBorder(Color v) {
            this.nodeBorder = v;
            return this;
        }

        Builder selectedOutline(Color v) {
            this.selectedOutline = v;
            return this;
        }

        Builder pendingOutline(Color v) {
            this.pendingOutline = v;
            return this;
        }

        Builder mutedText(Color v) {
            this.mutedText = v;
            return this;
        }

        Builder titleText(Color v) {
            this.titleText = v;
            return this;
        }

        Builder warn(Color v) {
            this.warn = v;
            return this;
        }

        Builder accent(Color v) {
            this.accent = v;
            return this;
        }

        Builder defaultEdgeColor(Color v) {
            this.defaultEdgeColor = v;
            return this;
        }

        Builder labelChipBackground(Color v) {
            this.labelChipBackground = v;
            return this;
        }

        Builder labelChipBackgroundEmpty(Color v) {
            this.labelChipBackgroundEmpty = v;
            return this;
        }

        Builder labelChipBorder(Color v) {
            this.labelChipBorder = v;
            return this;
        }

        Builder labelChipText(Color v) {
            this.labelChipText = v;
            return this;
        }

        Builder labelChipTextEmpty(Color v) {
            this.labelChipTextEmpty = v;
            return this;
        }

        Builder socketRingHovered(Color v) {
            this.socketRingHovered = v;
            return this;
        }

        Builder socketRingDefault(Color v) {
            this.socketRingDefault = v;
            return this;
        }

        Builder socketEmptyFill(Color v) {
            this.socketEmptyFill = v;
            return this;
        }

        Builder socketLabelText(Color v) {
            this.socketLabelText = v;
            return this;
        }

        Builder cabinetSocketFill(Color v) {
            this.cabinetSocketFill = v;
            return this;
        }

        Builder cabinetSocketPending(Color v) {
            this.cabinetSocketPending = v;
            return this;
        }

        Builder cabinetSocketHovered(Color v) {
            this.cabinetSocketHovered = v;
            return this;
        }

        Builder cabinetSocketBorder(Color v) {
            this.cabinetSocketBorder = v;
            return this;
        }

        Builder rubberBandFill(Color v) {
            this.rubberBandFill = v;
            return this;
        }

        Builder rubberBandBorder(Color v) {
            this.rubberBandBorder = v;
            return this;
        }

        Builder resizeHandle(Color v) {
            this.resizeHandle = v;
            return this;
        }

        Builder cardBlockBorder(Color v) {
            this.cardBlockBorder = v;
            return this;
        }

        Builder cardBlockHeaderText(Color v) {
            this.cardBlockHeaderText = v;
            return this;
        }

        Builder metaText(Color v) {
            this.metaText = v;
            return this;
        }

        Builder nodeFill(Map<SchemaNodeType, Color> v) {
            this.nodeFill = v;
            return this;
        }

        Builder connectorPalette(Color[] v) {
            this.connectorPalette = v;
            return this;
        }

        Builder roleLineColors(Map<InterfaceRole, Color> v) {
            this.roleLineColors = v;
            return this;
        }

        SchemaStyle build() {
            return new SchemaStyle(this);
        }
    }
}
