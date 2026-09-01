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

    /** Регистрирует цвет как «недавно использованный» — вызывать ПОСЛЕ того, как
     *  пользователь подтвердил выбор (OK диалога), а не при каждом промежуточном
     *  клике по палитре. Повторный выбор того же цвета переносит его в начало
     *  списка, а не дублирует запись. */
    public static void remember(Color c) {
        if (c == null) {
            return;
        }
        RECENT.removeIf(existing -> existing.getRGB() == c.getRGB());
        RECENT.add(0, c);
        while (RECENT.size() > MAX_RECENT) {
            RECENT.remove(RECENT.size() - 1);
        }
    }

    /** Только для тестов — снимок текущего общего списка "недавних" цветов. */
    static List<Color> recentSnapshotForTests() {
        return List.copyOf(RECENT);
    }

    /** Только для тестов — список статический (общий на процесс), тесты не должны
     *  видеть остатки друг от друга. */
    static void clearForTests() {
        RECENT.clear();
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
        if (RECENT.isEmpty()) {
            grid.setLayout(new BorderLayout());
            grid.add(new JLabel("Пока пусто — выбранные цвета появятся здесь"), BorderLayout.NORTH);
            return;
        }
        grid.setLayout(new GridLayout(0, COLS, 4, 4));
        for (Color c : RECENT) {
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
