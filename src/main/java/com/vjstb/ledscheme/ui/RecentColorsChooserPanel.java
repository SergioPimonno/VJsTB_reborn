package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;

/**
 * Вкладка «Недавние» палитры {@link javax.swing.JColorChooser} — ОБЩАЯ для всех
 * мест приложения, что красят "линии" (цепочки питания/сигнала, связи общей
 * схемы, связи сетевого менеджера), а не отдельная на каждый вызов диалога.
 *
 * <p>Баг-репорт: "вкладка recent должна быть общей для всех линий схемы, а не
 * только для текущей выбранной. Сейчас для каждой линии цвет приходится
 * выбирать отдельно, это тяжело из-за количества оттенков" — встроенная вкладка
 * "Swatches" стандартного Swing {@code JColorChooser} строит свою историю
 * недавних цветов ({@code DefaultSwatchChooserPanel.RecentSwatchPanel}) как
 * ПОЛЕ ЭКЗЕМПЛЯРА самой панели — а {@code JColorChooser.showDialog(...)}
 * (см. {@code UiKit#showColorChooser}, единственный способ вызова диалога во
 * всём проекте) создаёт НОВЫЙ {@code JColorChooser} с нуля при КАЖДОМ вызове,
 * поэтому история "недавних" стандартной вкладки обнуляется на каждой новой
 * линии — то, что видит пользователь, никогда не накапливается. Эта вкладка —
 * замена/дополнение: список хранится в {@code static}-поле класса (общий на
 * весь процесс приложения, не персистентный — не переживает перезапуск, как и
 * раньше у стандартной Swing-панели, только теперь ОБЩИЙ, а не per-диалог).
 */
public class RecentColorsChooserPanel extends AbstractColorChooserPanel {

    private static final int MAX_RECENT = 24;
    private static final int COLS = 8;
    private static final int SWATCH_PX = 26;
    private static final List<Color> RECENT = new LinkedList<>();
    private static final List<Color> RECENT_MASKS = new LinkedList<>();

    /** Канал памяти «недавних» цветов (2026-09-30, запрос пользователя: «механизм палитры
     *  из общих схем, но память цветов масок — отдельная») — у каждого канала СВОЙ список
     *  в памяти процесса и свой персистентный список в профиле. Все прежние вызовы без
     *  канала остаются на {@link #LINES}, их поведение не менялось. */
    public enum Channel {
        /** Цвета линий схем/цепочек/сетевого менеджера (как было до 2026-09-30). */
        LINES,
        /** Цвета шахматки масок экранов («Свои цвета…»). */
        MASKS
    }

    private static List<Color> listOf(Channel channel) {
        return channel == Channel.MASKS ? RECENT_MASKS : RECENT;
    }

    private final Channel channel;

    /** Вкладка канала {@link Channel#LINES} — как раньше (палитра линий). */
    public RecentColorsChooserPanel() {
        this(Channel.LINES);
    }

    public RecentColorsChooserPanel(Channel channel) {
        this.channel = channel != null ? channel : Channel.LINES;
    }

    /** Регистрирует цвет как «недавно использованный» — вызывать ПОСЛЕ того, как
     *  пользователь подтвердил выбор (OK диалога), а не при каждом промежуточном
     *  клике по палитре. Повторный выбор того же цвета переносит его в начало
     *  списка, а не дублирует запись. Только в памяти процесса — см. {@link
     *  #remember(Color, com.vjstb.ledscheme.settings.SettingsManager)} для
     *  персистентной версии; эта перегрузка оставлена ради теста и на случай
     *  вызова без доступа к настройкам. */
    public static void remember(Color c) {
        remember(c, Channel.LINES);
    }

    /** Как {@link #remember(Color)}, но для указанного канала (только память процесса). */
    public static void remember(Color c, Channel channel) {
        if (c == null) {
            return;
        }
        List<Color> list = listOf(channel);
        list.removeIf(existing -> existing.getRGB() == c.getRGB());
        list.add(0, c);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
    }

    /** Как {@link #remember(Color)}, но ещё и сохраняет список на диск через
     *  {@code settings} (баг-репорт: "палитру нужно сохранять между
     *  перезапусками" — про цвета линий соединений/цепочек расключения, не про
     *  типы кабинетов) — вызывающий код ({@link UiKit#showColorChooser}) всегда
     *  использует эту версию, у него settings под рукой. */
    public static void remember(Color c, com.vjstb.ledscheme.settings.SettingsManager settings) {
        remember(c);
        if (c != null) {
            settings.rememberRecentLineColor(c.getRGB());
        }
    }

    /** Как {@link #remember(Color, com.vjstb.ledscheme.settings.SettingsManager)}, но для
     *  указанного канала: {@link Channel#MASKS} пишет в {@code UserProfile.recentMaskColors}
     *  (отдельно от цветов линий). */
    public static void remember(Color c, com.vjstb.ledscheme.settings.SettingsManager settings,
                                Channel channel) {
        remember(c, channel);
        if (c != null) {
            if (channel == Channel.MASKS) {
                settings.rememberRecentMaskColor(c.getRGB());
            } else {
                settings.rememberRecentLineColor(c.getRGB());
            }
        }
    }

    /** Подмешивает в память процесса цвета, ранее сохранённые в профиле — только
     *  если общий {@code RECENT} сейчас пуст (первое обращение за эту сессию
     *  приложения): иначе уже накопленные за сессию цвета молча перетёрлись бы
     *  списком из профиля при каждом вызове диалога. Вызывать ПЕРЕД показом
     *  диалога (см. {@link UiKit#showColorChooser}). */
    public static void loadPersisted(com.vjstb.ledscheme.settings.SettingsManager settings) {
        loadPersisted(settings, Channel.LINES);
    }

    /** Как {@link #loadPersisted(com.vjstb.ledscheme.settings.SettingsManager)}, но для
     *  указанного канала (свой список в памяти, свой — в профиле). */
    public static void loadPersisted(com.vjstb.ledscheme.settings.SettingsManager settings, Channel channel) {
        List<Color> list = listOf(channel);
        if (!list.isEmpty()) {
            return;
        }
        List<Integer> persisted = channel == Channel.MASKS
                ? settings.activeProfile().getRecentMaskColors()
                : settings.activeProfile().getRecentLineColors();
        for (Integer rgb : persisted) {
            list.add(new Color(rgb));
        }
    }

    /** Только для тестов — снимок текущего общего списка "недавних" цветов. */
    static List<Color> recentSnapshotForTests() {
        return List.copyOf(RECENT);
    }

    /** Только для тестов — снимок списка указанного канала. */
    static List<Color> recentSnapshotForTests(Channel channel) {
        return List.copyOf(listOf(channel));
    }

    /** Только для тестов — список статический (общий на процесс), тесты не должны
     *  видеть остатки друг от друга. */
    static void clearForTests() {
        RECENT.clear();
        RECENT_MASKS.clear();
    }

    private JPanel grid;

    @Override
    public void updateChooser() {
        // Перестройка сетки не нужна при смене выбранного цвета в ДРУГИХ вкладках
        // палитры — список "недавних" отражает только ПОДТВЕРЖДЁННЫЕ прошлые
        // выборы (см. remember), не текущее превью.
    }

    @Override
    protected void buildChooser() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        grid = new JPanel(new GridLayout(0, COLS, 4, 4));
        refreshGrid();
        add(grid, BorderLayout.NORTH);
    }

    private void refreshGrid() {
        grid.removeAll();
        List<Color> recent = listOf(channel);
        if (recent.isEmpty()) {
            grid.setLayout(new BorderLayout());
            grid.add(new JLabel("Пока пусто — выбранные цвета появятся здесь"), BorderLayout.NORTH);
            return;
        }
        grid.setLayout(new GridLayout(0, COLS, 4, 4));
        for (Color c : recent) {
            JButton b = new JButton();
            b.setBackground(c);
            b.setOpaque(true);
            b.setBorderPainted(true);
            b.setBorder(new LineBorder(Color.BLACK, 1));
            b.setPreferredSize(new Dimension(SWATCH_PX, SWATCH_PX));
            b.setToolTipText(String.format("#%06X", c.getRGB() & 0xFFFFFF));
            b.addActionListener(e -> getColorSelectionModel().setSelectedColor(c));
            grid.add(b);
        }
    }

    @Override
    public String getDisplayName() {
        return "Недавние";
    }

    @Override
    public Icon getSmallDisplayIcon() {
        return null;
    }

    @Override
    public Icon getLargeDisplayIcon() {
        return null;
    }
}
