package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.VideoTimingCalc;
import com.vjstb.ledscheme.service.VideoTimingCalc.Bandwidth;
import com.vjstb.ledscheme.service.VideoTimingCalc.ColorFormat;
import com.vjstb.ledscheme.service.VideoTimingCalc.Input;
import com.vjstb.ledscheme.service.VideoTimingCalc.LinkCheck;
import com.vjstb.ledscheme.service.VideoTimingCalc.LinkStandard;
import com.vjstb.ledscheme.service.VideoTimingCalc.Standard;
import com.vjstb.ledscheme.service.VideoTimingCalc.Timing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Калькулятор видеотайминга (VESA CVT/CVT-RB/CVT-RBv2) и требуемой полосы канала —
 * аналог tomverbeure.github.io/video_timings_calculator (см. задачу пользователя,
 * образец для сверки формул). Полезен при выборе медиасервера/карты и разъёма для
 * нестандартного разрешения LED-стены: показывает реальную частоту пикселей и
 * проходит ли она по DisplayPort/HDMI/SDI/Ethernet без сжатия (DSC) или вообще.
 * Расчёт — {@link VideoTimingCalc}; DMT/CEA-861 таблицы готовых режимов сюда
 * намеренно не перенесены (см. javadoc класса) — колонки CVT/CVT-RB/CVT-RBv2/
 * «Пользовательский» покрывают реальный сценарий использования этого инструмента.
 */
public class VideoTimingCalculatorDialog extends JDialog {

    private static final Standard[] STANDARDS = {Standard.CVT, Standard.CVT_RB, Standard.CVT_RB2, Standard.CUSTOM};
    private static final String[] STANDARD_LABELS = {"CVT", "CVT-RB", "CVT-RBv2", "Пользовательский"};

    private record PresetMode(String name, int h, int v, double refresh) {
    }

    private static final List<PresetMode> PRESETS = List.of(
            new PresetMode("16K / 60", 15360, 8640, 60),
            new PresetMode("10K / 60", 10240, 4320, 60),
            new PresetMode("8K / 60", 7680, 4320, 60),
            new PresetMode("6K / 60", 6016, 3384, 60),
            new PresetMode("5K / 60", 5120, 2880, 60),
            new PresetMode("4K / 60", 3840, 2160, 60),
            new PresetMode("4K / 120", 3840, 2160, 120),
            new PresetMode("1440p / 60", 2560, 1440, 60),
            new PresetMode("1080p / 60", 1920, 1080, 60),
            new PresetMode("1080p / 120", 1920, 1080, 120),
            new PresetMode("720p / 60", 1280, 720, 60));

    private final JComboBox<String> presetBox = new JComboBox<>();
    private final JTextField horizField = new JTextField("5120", 6);
    private final JTextField vertField = new JTextField("2048", 6);
    private final JTextField refreshField = new JTextField("60", 5);
    private final JComboBox<String> marginsBox = new JComboBox<>(new String[]{"Нет", "Да"});
    private final JComboBox<String> interlacedBox = new JComboBox<>(new String[]{"Нет", "Да"});
    private final JComboBox<Integer> bpcBox = new JComboBox<>(new Integer[]{5, 6, 8, 10, 12, 16});
    {
        bpcBox.setSelectedItem(8);
    }
    private final JComboBox<ColorFormat> colorFmtBox = new JComboBox<>(ColorFormat.values());
    private final JComboBox<String> videoOptBox = new JComboBox<>(new String[]{"Нет", "Да"});
    private final JTextField customHBlankField = new JTextField("80", 5);
    private final JTextField customVBlankField = new JTextField("6", 5);
    private final JLabel statusLabel = UiKit.muted(" ");

    private final TimingTableModel timingModel = new TimingTableModel();
    private final BandwidthTableModel bwModel = new BandwidthTableModel();
    private final JLabel[] modelineFields = new JLabel[STANDARDS.length];

    public VideoTimingCalculatorDialog(Window owner) {
        super(owner, "Калькулятор видеотайминга", ModalityType.MODELESS);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildInputPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Тайминги", buildTimingsTab());
        tabs.addTab("Modeline", buildModelinesTab());
        tabs.addTab("Пропускная способность", buildBandwidthTab());
        // Явный размер вкладок — иначе pack() ниже сжал бы диалог до естественной
        // высоты таблицы (все 36 строк «Пропускной способности» без скролла).
        tabs.setPreferredSize(new Dimension(900, 420));
        content.add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        // pack() вместо фиксированного setSize(...) — иначе поля ввода в
        // FlowLayout-панели параметров сжимались/переносились непредсказуемо,
        // если заданная ширина не совпадала с тем, что реально нужно подписям
        // (баг-репорт при первой версии: обрезанные подписи вроде "Пикселей по
        // горизонталь"). pack() даёт диалогу именно ту ширину, которая нужна
        // содержимому, без гадания с константой.
        pack();
        setLocationRelativeTo(owner);

        wireLiveRecalculation();
        recalculate();
    }

    // ---- входные параметры ----

    private JPanel buildInputPanel() {
        // FlowLayout вместо GridBagLayout с фиксированными колонками — каждая
        // подпись+поле сами решают свою ширину и переносятся на новую строку по
        // факту нехватки места, а не сжимаются вместе с соседями до нечитаемого
        // обрезанного текста (баг-репорт при первой версии: "Пикселей по
        // горизонталь", "RGB 4:4" — подписи и значения обрезались из-за того, что
        // 4 колонки в ряд не помещались в ширину диалога).
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Параметры"));

        presetBox.addItem("— свои значения —");
        for (PresetMode p : PRESETS) {
            presetBox.addItem(p.name());
        }
        presetBox.addActionListener(e -> {
            int idx = presetBox.getSelectedIndex();
            if (idx <= 0) {
                return;
            }
            PresetMode p = PRESETS.get(idx - 1);
            horizField.setText(String.valueOf(p.h()));
            vertField.setText(String.valueOf(p.v()));
            refreshField.setText(fmtRefresh(p.refresh()));
        });
        videoOptBox.setToolTipText("Только для CVT-RBv2 — снижает частоту кадров в 1000/1001 раз"
                + " (как 59.94 Гц вместо 60), совместимость со старыми декодерами.");

        horizField.setColumns(6);
        vertField.setColumns(6);
        refreshField.setColumns(5);
        customHBlankField.setColumns(5);
        customVBlankField.setColumns(5);

        panel.add(UiKit.formRow("Готовый режим", presetBox));
        panel.add(UiKit.formRow("Пикселей по горизонтали", horizField));
        panel.add(UiKit.formRow("Пикселей по вертикали", vertField));
        panel.add(UiKit.formRow("Частота обновления, Гц", refreshField));
        panel.add(UiKit.formRow("Поля (margins)", marginsBox));
        panel.add(UiKit.formRow("Чересстрочная развёртка", interlacedBox));
        panel.add(UiKit.formRow("Бит на компонент", bpcBox));
        panel.add(UiKit.formRow("Формат цвета", colorFmtBox));
        panel.add(UiKit.formRow("Video Optimized", videoOptBox));
        panel.add(UiKit.formRow("Свой режим: H Blank", customHBlankField));
        panel.add(UiKit.formRow("Свой режим: V Blank", customVBlankField));

        return panel;
    }

    private static String fmtRefresh(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void wireLiveRecalculation() {
        DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                recalculate();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                recalculate();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                recalculate();
            }
        };
        for (JTextField f : new JTextField[]{horizField, vertField, refreshField, customHBlankField, customVBlankField}) {
            f.getDocument().addDocumentListener(docListener);
        }
        ActionListener recalc = e -> recalculate();
        for (JComboBox<?> c : new JComboBox<?>[]{marginsBox, interlacedBox, bpcBox, colorFmtBox, videoOptBox}) {
            c.addActionListener(recalc);
        }
    }

    // ---- пересчёт ----

    private Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void recalculate() {
        Integer h = parseIntOrNull(horizField.getText());
        Integer v = parseIntOrNull(vertField.getText());
        Double refresh = parseDoubleOrNull(refreshField.getText());
        Integer hBlank = parseIntOrNull(customHBlankField.getText());
        Integer vBlank = parseIntOrNull(customVBlankField.getText());
        if (h == null || v == null || refresh == null || hBlank == null || vBlank == null
                || h <= 0 || v <= 0 || refresh <= 0) {
            statusLabel.setText(" Заполните числовые поля корректными положительными значениями.");
            return;
        }
        statusLabel.setText(" ");

        boolean margins = marginsBox.getSelectedIndex() == 1;
        boolean interlaced = interlacedBox.getSelectedIndex() == 1;
        boolean videoOpt = videoOptBox.getSelectedIndex() == 1;
        int bpc = (Integer) bpcBox.getSelectedItem();
        ColorFormat colorFmt = (ColorFormat) colorFmtBox.getSelectedItem();

        Input in = new Input(h, v, refresh, margins, interlaced, videoOpt, hBlank, vBlank);
        Timing[] timings = new Timing[STANDARDS.length];
        for (int i = 0; i < STANDARDS.length; i++) {
            timings[i] = VideoTimingCalc.compute(STANDARDS[i], in);
        }

        timingModel.update(timings, bpc, colorFmt);
        bwModel.update(timings, bpc, colorFmt);
        for (int i = 0; i < STANDARDS.length; i++) {
            Timing t = timings[i];
            String ml = t == null ? null : t.modeline(h + "x" + v);
            modelineFields[i].setText(ml != null ? ml : "— (нет разбивки на порчи для этого режима)");
        }
    }

    // ---- вкладка «Тайминги» ----

    private JScrollPane buildTimingsTab() {
        JTable table = new JTable(timingModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        for (int i = 1; i <= STANDARDS.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(120);
        }
        table.setRowHeight(20);
        return new JScrollPane(table);
    }

    private record TimingRow(String label, String unit, BiFunction<Timing, VideoTimingCalc.Bandwidth, String> fmt) {
    }

    private static String num(double v, int decimals) {
        double f = Math.pow(10, decimals);
        double r = Math.round(v * f) / f;
        return decimals == 0 ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static String opt(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String optChar(Character c) {
        return c == null ? "" : String.valueOf(c);
    }

    private static final List<TimingRow> TIMING_ROWS = List.of(
            new TimingRow("Соотношение сторон", "", (t, bw) -> t.aspectRatio()),
            new TimingRow("Частота пикселей", "МГц", (t, bw) -> num(t.pixelClockHz() / 1_000_000.0, 3)),
            new TimingRow("H Total", "пикс.", (t, bw) -> String.valueOf(t.hTotal())),
            new TimingRow("H Active", "пикс.", (t, bw) -> String.valueOf(t.hActive())),
            new TimingRow("H Blank", "пикс.", (t, bw) -> String.valueOf(t.hBlank())),
            new TimingRow("H Front Porch", "пикс.", (t, bw) -> opt(t.hFrontPorch())),
            new TimingRow("H Sync", "пикс.", (t, bw) -> opt(t.hSync())),
            new TimingRow("H Back Porch", "пикс.", (t, bw) -> opt(t.hBackPorch())),
            new TimingRow("H Sync полярность", "", (t, bw) -> optChar(t.hPol())),
            new TimingRow("H Freq", "кГц", (t, bw) -> num(t.hFreqHz() / 1000.0, 3)),
            new TimingRow("H Period", "мкс", (t, bw) -> num(t.hPeriodUs(), 3)),
            new TimingRow("V Total", "строк", (t, bw) -> String.valueOf(t.vTotal())),
            new TimingRow("V Active", "строк", (t, bw) -> String.valueOf(t.vActive())),
            new TimingRow("V Blank", "строк", (t, bw) -> String.valueOf(t.vBlank())),
            new TimingRow("V Blank Duration", "мкс", (t, bw) -> num(t.vBlankDurationUs(), 0)),
            new TimingRow("V Front Porch", "строк", (t, bw) -> opt(t.vFrontPorch())),
            new TimingRow("V Sync", "строк", (t, bw) -> opt(t.vSync())),
            new TimingRow("V Back Porch", "строк", (t, bw) -> opt(t.vBackPorch())),
            new TimingRow("V Sync полярность", "", (t, bw) -> optChar(t.vPol())),
            new TimingRow("V Freq", "Гц", (t, bw) -> num(t.vFreqHz(), 3)),
            new TimingRow("V Period", "мс", (t, bw) -> num(t.vPeriodMs(), 3)),
            new TimingRow("Peak BW", "Мбит/с", (t, bw) -> num(bw.peakBps() / 1_000_000.0, 0)),
            new TimingRow("Line BW", "Мбит/с", (t, bw) -> num(bw.lineBps() / 1_000_000.0, 0)),
            new TimingRow("Active BW", "Мбит/с", (t, bw) -> num(bw.activeBps() / 1_000_000.0, 0)));

    private final class TimingTableModel extends AbstractTableModel {
        private Timing[] timings = new Timing[STANDARDS.length];
        private int bpc = 8;
        private ColorFormat colorFormat = ColorFormat.RGB_444;

        void update(Timing[] timings, int bpc, ColorFormat colorFormat) {
            this.timings = timings;
            this.bpc = bpc;
            this.colorFormat = colorFormat;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return TIMING_ROWS.size();
        }

        @Override
        public int getColumnCount() {
            return STANDARDS.length + 1;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Параметр" : STANDARD_LABELS[column - 1];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TimingRow row = TIMING_ROWS.get(rowIndex);
            if (columnIndex == 0) {
                return row.label() + (row.unit().isEmpty() ? "" : ", " + row.unit());
            }
            Timing t = timings[columnIndex - 1];
            if (t == null) {
                return "";
            }
            Bandwidth bw = VideoTimingCalc.bandwidth(t, bpc, colorFormat);
            return row.fmt().apply(t, bw);
        }
    }

    // ---- вкладка «Modeline» ----

    private JPanel buildModelinesTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (int i = 0; i < STANDARDS.length; i++) {
            JLabel titleLabel = new JLabel(STANDARD_LABELS[i]);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(titleLabel);

            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            JLabel field = new JLabel(" ");
            field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            modelineFields[i] = field;
            row.add(field, BorderLayout.CENTER);
            JButton copy = new JButton("Копировать");
            int idx = i;
            copy.addActionListener(e -> {
                String text = modelineFields[idx].getText();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            });
            row.add(copy, BorderLayout.EAST);
            panel.add(row);
            panel.add(Box.createVerticalStrut(10));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // ---- вкладка «Пропускная способность» ----

    private JScrollPane buildBandwidthTab() {
        JTable table = new JTable(bwModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(230);
        for (int i = 2; i <= STANDARDS.length + 1; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
        table.setRowHeight(20);
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.LEFT);
                LinkCheck check = bwModel.checkAt(row, column);
                if (!isSelected) {
                    if (check == null) {
                        setForeground(t.getForeground());
                    } else {
                        setForeground(switch (check.status()) {
                            case OK -> new Color(0x2e7d32);
                            case DSC_OK -> new Color(0xb26a00);
                            default -> new Color(0xc62828);
                        });
                    }
                }
                return c;
            }
        };
        for (int i = 2; i <= STANDARDS.length + 1; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(statusRenderer);
        }
        return new JScrollPane(table);
    }

    private final class BandwidthTableModel extends AbstractTableModel {
        private Timing[] timings = new Timing[STANDARDS.length];
        private int bpc = 8;
        private ColorFormat colorFormat = ColorFormat.RGB_444;
        private final LinkCheck[][] cache = new LinkCheck[VideoTimingCalc.LINK_STANDARDS.size()][STANDARDS.length];

        void update(Timing[] timings, int bpc, ColorFormat colorFormat) {
            this.timings = timings;
            this.bpc = bpc;
            this.colorFormat = colorFormat;
            List<LinkStandard> standards = VideoTimingCalc.LINK_STANDARDS;
            for (int r = 0; r < standards.size(); r++) {
                for (int c = 0; c < STANDARDS.length; c++) {
                    Timing t = timings[c];
                    cache[r][c] = t == null ? null : VideoTimingCalc.checkLink(standards.get(r), t, bpc, colorFormat);
                }
            }
            fireTableDataChanged();
        }

        LinkCheck checkAt(int row, int column) {
            if (column < 2) {
                return null;
            }
            return cache[row][column - 2];
        }

        @Override
        public int getRowCount() {
            return VideoTimingCalc.LINK_STANDARDS.size();
        }

        @Override
        public int getColumnCount() {
            return STANDARDS.length + 2;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) return "Группа";
            if (column == 1) return "Стандарт";
            return STANDARD_LABELS[column - 2];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LinkStandard std = VideoTimingCalc.LINK_STANDARDS.get(rowIndex);
            if (columnIndex == 0) {
                return std.group();
            }
            if (columnIndex == 1) {
                return std.name() + " — макс. " + Math.round(std.maxMbit()) + " Мбит/с";
            }
            LinkCheck check = cache[rowIndex][columnIndex - 2];
            if (check == null) {
                return "";
            }
            return switch (check.status()) {
                case OK -> "Ok (" + check.percentOfMax() + "%)";
                case FAIL -> "Нет (" + check.percentOfMax() + "%)";
                case DSC_OK -> "Нет (" + check.percentOfMax() + "%) — DSC Ok (" + check.dscBpp() + " бит/пикс)";
                case INVALID_8BPC_ONLY -> "Только 8 бит/компонент";
                case INVALID_RGB_ONLY -> "Только RGB";
            };
        }
    }
}
