package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ProjectorInstance;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ProjectorCalc;
import com.vjstb.ledscheme.service.ProjectorCalc.AmbientLight;
import com.vjstb.ledscheme.service.ProjectorCalc.AspectRatio;
import com.vjstb.ledscheme.service.ProjectorCalc.BrightnessCheck;
import com.vjstb.ledscheme.service.ProjectorCalc.BrightnessStatus;
import com.vjstb.ledscheme.service.ProjectorCalc.ImageSize;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFit;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFitStatus;
import com.vjstb.ledscheme.service.ProjectorCalc.ThrowDistanceRange;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Калькулятор throw ratio/объектива и освещённости экрана проектора (Task #135/v2.0).
 * Отдельный самостоятельный калькулятор (по образцу {@link VideoTimingCalculatorDialog}) —
 * НЕ новый тип оборудования в графе общей схемы (проектор в этом инструменте не
 * участвует в силовой/сигнальной цепочке). Единственное отличие от видеотайминга:
 * умеет экспортировать сконфигурированный проектор в текущую сцену проекта
 * ({@link AppModel#addProjector}), чтобы он попал в спецификацию оборудования
 * (см. {@code OutputStagePanel.buildEquipmentSpec}). Расчёт — {@link ProjectorCalc}.
 */
public class ProjectorCalculatorDialog extends JDialog {

    private final AppModel model;

    private final JComboBox<AspectRatio> aspectBox = new JComboBox<>(AspectRatio.values());
    private final JTextField diagonalField = new JTextField("2.5", 6);
    private final JTextField throwDistanceField = new JTextField("4.0", 6);
    private final JTextField lensNameField = new JTextField("Стандартный зум", 14);
    private final JTextField lensMinField = new JTextField("1.2", 5);
    private final JTextField lensMaxField = new JTextField("1.5", 5);
    private final JTextField vOffsetField = new JTextField("0", 5);
    private final JTextField lumensField = new JTextField("5000", 6);
    private final JTextField gainField = new JTextField("1.0", 5);
    private final JComboBox<AmbientLight> ambientBox = new JComboBox<>(AmbientLight.values());
    private final JTextField labelField = new JTextField("Проектор 1", 14);

    private final JLabel requiredRatioLabel = new JLabel(" ");
    private final JLabel fitStatusLabel = new JLabel(" ");
    private final JLabel rangeLabel = new JLabel(" ");
    private final ProjectorGeometryVisualizer visualizer = new ProjectorGeometryVisualizer();

    private final BrightnessTableModel brightnessModel = new BrightnessTableModel();
    private final JLabel statusLabel = UiKit.muted(" ");
    private final JButton exportButton = new JButton("Экспортировать в проект…");

    private final DefaultListModel<ProjectorInstance> exportedModel = new DefaultListModel<>();
    private final JList<ProjectorInstance> exportedList = new JList<>(exportedModel);

    public ProjectorCalculatorDialog(Window owner, AppModel model) {
        super(owner, "Калькулятор проектора", ModalityType.MODELESS);
        this.model = model;

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildInputPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Геометрия", buildGeometryTab());
        tabs.addTab("Яркость", buildBrightnessTab());
        tabs.setPreferredSize(new Dimension(760, 360));
        content.add(tabs, BorderLayout.CENTER);

        content.add(buildExportPanel(), BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        wireLiveRecalculation();
        refreshExportedList();
        recalculate();
    }

    // ---- входные параметры ----

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Параметры"));

        panel.add(UiKit.formRow("Соотношение сторон", aspectBox));
        panel.add(UiKit.formRow("Диагональ экрана, м", diagonalField));
        panel.add(UiKit.formRow("Throw-дистанция, м", throwDistanceField));
        panel.add(UiKit.formRow("Объектив: название", lensNameField));
        panel.add(UiKit.formRow("Объектив: min throw ratio", lensMinField));
        panel.add(UiKit.formRow("Объектив: max throw ratio", lensMaxField));
        panel.add(UiKit.formRow("Смещение объектива, %", vOffsetField));
        panel.add(UiKit.formRow("ANSI-люмены", lumensField));
        panel.add(UiKit.formRow("Gain экрана", gainField));
        panel.add(UiKit.formRow("Условия освещения", ambientBox));
        panel.add(UiKit.formRow("Название проектора (для спецификации)", labelField));

        return panel;
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
        for (JTextField f : new JTextField[]{diagonalField, throwDistanceField, lensMinField, lensMaxField,
                vOffsetField, lumensField, gainField}) {
            f.getDocument().addDocumentListener(docListener);
        }
        ActionListener recalc = e -> recalculate();
        aspectBox.addActionListener(recalc);
        ambientBox.addActionListener(recalc);
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---- пересчёт ----

    private LensFit lastFit;
    private ImageSize lastSize;
    private double lastThrowDistanceM;
    private double lastVerticalOffsetM;
    private double lastVerticalOffsetPercent;
    private double lastLumens;
    private double lastGain;

    private void recalculate() {
        Double diagonal = parseDoubleOrNull(diagonalField.getText());
        Double throwDistance = parseDoubleOrNull(throwDistanceField.getText());
        Double lensMin = parseDoubleOrNull(lensMinField.getText());
        Double lensMax = parseDoubleOrNull(lensMaxField.getText());
        Double vOffsetPercent = parseDoubleOrNull(vOffsetField.getText());
        Double lumens = parseDoubleOrNull(lumensField.getText());
        Double gain = parseDoubleOrNull(gainField.getText());

        if (diagonal == null || throwDistance == null || lensMin == null || lensMax == null
                || vOffsetPercent == null || lumens == null || gain == null
                || diagonal <= 0 || throwDistance <= 0 || lensMin <= 0 || lensMax < lensMin || lumens < 0 || gain <= 0) {
            statusLabel.setText(" Заполните числовые поля корректными положительными значениями.");
            exportButton.setEnabled(false);
            return;
        }

        AspectRatio aspect = (AspectRatio) aspectBox.getSelectedItem();
        AmbientLight ambient = (AmbientLight) ambientBox.getSelectedItem();
        ImageSize size = ProjectorCalc.imageSizeFromDiagonal(diagonal, aspect);
        LensFit fit = ProjectorCalc.checkLensFit(throwDistance, size, lensMin, lensMax);
        ThrowDistanceRange range = ProjectorCalc.throwDistanceRange(size, lensMin, lensMax);
        double offsetM = ProjectorCalc.verticalOffsetM(size, vOffsetPercent);
        BrightnessCheck brightness = ProjectorCalc.checkBrightness(lumens, gain, size, ambient);

        lastFit = fit;
        lastSize = size;
        lastThrowDistanceM = throwDistance;
        lastVerticalOffsetM = offsetM;
        lastVerticalOffsetPercent = vOffsetPercent;
        lastLumens = lumens;
        lastGain = gain;

        requiredRatioLabel.setText(String.format("Требуемый throw ratio: %.2f", fit.requiredRatio()));
        boolean fits = fit.status() == LensFitStatus.FITS;
        fitStatusLabel.setText(switch (fit.status()) {
            case FITS -> "Объектив подходит на этой дистанции.";
            case TOO_SHORT_INCREASE_DISTANCE -> "Дистанция слишком мала для объектива — увеличьте throw-дистанцию.";
            case TOO_LONG_DECREASE_DISTANCE -> "Дистанция слишком велика для объектива — уменьшите throw-дистанцию.";
        });
        fitStatusLabel.setForeground(fits ? new Color(0x2e7d32) : new Color(0xc62828));
        rangeLabel.setText(String.format("Допустимый диапазон дистанции для этого объектива: %.2f–%.2f м",
                range.minM(), range.maxM()));

        visualizer.update(size, throwDistance, offsetM, vOffsetPercent, fit);
        brightnessModel.update(lumens, gain, size);

        statusLabel.setText(String.format(" Освещённость экрана: %.0f лк (%s)", brightness.screenLux(),
                statusLabelText(brightness.status())));
        exportButton.setEnabled(model.getCurrentScene() != null);
        if (model.getCurrentScene() == null) {
            exportButton.setToolTipText("Выберите сцену в проекте, чтобы экспортировать проектор");
        } else {
            exportButton.setToolTipText(null);
        }
    }

    private static String statusLabelText(BrightnessStatus status) {
        return switch (status) {
            case PASS -> "хватает для выбранных условий";
            case MARGINAL -> "на грани для выбранных условий";
            case FAIL -> "недостаточно для выбранных условий";
        };
    }

    // ---- вкладка «Геометрия» ----

    private JPanel buildGeometryTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        requiredRatioLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fitStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        labels.add(requiredRatioLabel);
        labels.add(fitStatusLabel);
        labels.add(rangeLabel);
        panel.add(labels, BorderLayout.NORTH);
        panel.add(visualizer, BorderLayout.CENTER);
        return panel;
    }

    // ---- вкладка «Яркость» ----

    private JPanel buildBrightnessTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTable table = new JTable(brightnessModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.setRowHeight(24);
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    BrightnessStatus status = brightnessModel.statusAt(row);
                    setForeground(switch (status) {
                        case PASS -> new Color(0x2e7d32);
                        case MARGINAL -> new Color(0xb26a00);
                        case FAIL -> new Color(0xc62828);
                    });
                }
                return c;
            }
        };
        for (int i = 0; i < 4; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(statusRenderer);
        }
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JLabel hint = UiKit.muted("Ориентировочные бакеты освещённости — не нормативная таблица, а типовая"
                + " практика инсталляций.");
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private final class BrightnessTableModel extends AbstractTableModel {
        private double lumens = 5000;
        private double gain = 1.0;
        private ImageSize size = new ImageSize(2, 1.1);

        void update(double lumens, double gain, ImageSize size) {
            this.lumens = lumens;
            this.gain = gain;
            this.size = size;
            fireTableDataChanged();
        }

        BrightnessStatus statusAt(int row) {
            return ProjectorCalc.checkBrightness(lumens, gain, size, AmbientLight.values()[row]).status();
        }

        @Override
        public int getRowCount() {
            return AmbientLight.values().length;
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Условия";
                case 1 -> "Диапазон, лк";
                case 2 -> "Текущая освещённость, лк";
                default -> "Статус";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AmbientLight bucket = AmbientLight.values()[rowIndex];
            return switch (columnIndex) {
                case 0 -> bucket.toString();
                case 1 -> String.format("%.0f–%.0f", bucket.minLux(), bucket.maxLux());
                case 2 -> String.format("%.0f", ProjectorCalc.screenIlluminanceLux(lumens, gain, size));
                default -> switch (statusAt(rowIndex)) {
                    case PASS -> "Хватает";
                    case MARGINAL -> "На грани";
                    case FAIL -> "Недостаточно";
                };
            };
        }
    }

    // ---- экспорт в проект ----

    private JPanel buildExportPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        exportButton.addActionListener(e -> exportCurrentToScene());
        exportRow.add(exportButton);
        panel.add(exportRow);

        exportedList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof ProjectorInstance p) {
                    setText(String.format("%s — %.0f ANSI лм, объектив %s (%.2f–%.2f), throw %.2f м",
                            p.getLabel(), p.getAnsiLumens(), p.getLensName(), p.getLensMinThrowRatio(),
                            p.getLensMaxThrowRatio(), p.getThrowDistanceM()));
                }
                return c;
            }
        });
        JScrollPane exportedScroll = new JScrollPane(exportedList);
        exportedScroll.setPreferredSize(new Dimension(500, 80));
        JPanel listRow = new JPanel(new BorderLayout(6, 0));
        listRow.setBorder(BorderFactory.createTitledBorder("Экспортированные в текущую сцену проекторы"));
        listRow.add(exportedScroll, BorderLayout.CENTER);
        JButton deleteButton = new JButton("Удалить");
        deleteButton.addActionListener(e -> deleteSelectedExported());
        JPanel deleteCol = new JPanel();
        deleteCol.setLayout(new BoxLayout(deleteCol, BoxLayout.Y_AXIS));
        deleteCol.add(deleteButton);
        listRow.add(deleteCol, BorderLayout.EAST);
        panel.add(listRow);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);
        panel.add(bottom);

        return panel;
    }

    private void exportCurrentToScene() {
        if (model.getCurrentScene() == null || lastSize == null || lastFit == null) {
            return;
        }
        ProjectorInstance p = new ProjectorInstance();
        p.setLabel(labelField.getText().isBlank() ? "Проектор" : labelField.getText().trim());
        p.setAnsiLumens(lastLumens);
        p.setScreenGain(lastGain);
        p.setLensName(lensNameField.getText().trim());
        p.setLensMinThrowRatio(lastFit.lensMin());
        p.setLensMaxThrowRatio(lastFit.lensMax());
        p.setThrowDistanceM(lastThrowDistanceM);
        p.setImageWidthM(lastSize.widthM());
        p.setImageHeightM(lastSize.heightM());
        p.setVerticalOffsetPercent(lastVerticalOffsetPercent);
        p.setAmbientLight(((AmbientLight) ambientBox.getSelectedItem()).name());

        try {
            model.addProjector(p);
            refreshExportedList();
            statusLabel.setText(" Добавлено в сцену «" + model.getCurrentScene().getName() + "».");
        } catch (IllegalStateException ex) {
            statusLabel.setText(" " + ex.getMessage());
        }
    }

    private void deleteSelectedExported() {
        ProjectorInstance sel = exportedList.getSelectedValue();
        if (sel == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Удалить проектор «" + sel.getLabel() + "» из сцены?",
                "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            model.deleteProjector(sel.getId());
            refreshExportedList();
        }
    }

    private void refreshExportedList() {
        exportedModel.clear();
        for (ProjectorInstance p : model.projectorsForCurrentScene()) {
            exportedModel.addElement(p);
        }
    }
}
